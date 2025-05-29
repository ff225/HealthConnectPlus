from pydantic import BaseModel, field_validator, model_validator
from typing import Optional, List, Literal
from datetime import datetime

# === MODELLO DATI PER LE MISURAZIONI SENSORIALI ===
# Questo modulo definisce la struttura SenML (Sensor Measurement List)
# utilizzata per rappresentare i dati inviati da dispositivi/sensori all’API.

class SenMLRecord(BaseModel):
    """
    Rappresenta una singola misurazione SenML.

    Campi principali:
    - bn: nome del sensore (es. "leftwrist")
    - n: nome della feature misurata (es. "accX"), accettato anche come stringa semplice
    - v: valore numerico della misurazione (opzionale)
    - vs: valore stringa (non usato nel progetto attuale)
    - u: unità di misura (non usata, ma mantenuta per compatibilità)
    - t: timestamp ISO8601 della misurazione (obbligatorio)
    - execution_id, user_id: usati per tracciare chi ha generato i dati

    Le validazioni assicurano che:
    - 'n' sia una lista con una sola feature
    - 'v' sia numerico (se presente)
    - 't' sia sempre presente
    - 'bn' (nome sensore) sia fornito
    """

    bn: Optional[str] = None           # Nome del sensore (es. "leftwrist")
    n: List[str]                       # Feature misurata (lista di lunghezza 1)
    v: Optional[float] = None          # Valore numerico
    vs: Optional[str] = None           # Valore stringa
    u: Optional[str] = None            # Unità di misura
    t: Optional[datetime] = None       # Timestamp ISO‑8601
    execution_id: Optional[str] = None # ID esecuzione
    user_id: Optional[str] = None      # ID utente

    # --- VALIDAZIONI ---------------------------------------------------------

    @field_validator("n", mode="before")
    @classmethod
    def coerce_n_to_list(cls, v):
        # Permette sia "accX" che ["accX"], convertendo sempre in lista
        if isinstance(v, str):
            return [v]
        return v

    @field_validator("n")
    @classmethod
    def check_single_feature(cls, v):
        # Deve essere una lista con una sola feature
        if not isinstance(v, list) or len(v) != 1:
            raise ValueError("Il campo 'n' deve essere una lista contenente una sola feature")
        return v

    @field_validator("v")
    @classmethod
    def check_value_is_numeric(cls, v):
        # Se presente, 'v' deve essere un numero
        if v is not None and not isinstance(v, (int, float)):
            raise ValueError("Il campo 'v' deve essere numerico")
        return v

    @field_validator("t")
    @classmethod
    def check_time_present(cls, v):
        # Il timestamp è obbligatorio
        if v is None:
            raise ValueError("Il campo 't' (timestamp) è obbligatorio")
        return v

    @model_validator(mode="after")
    def check_required_fields(self):
        # Verifica che 'bn' sia presente
        if not self.bn:
            raise ValueError("Il campo 'bn' (sensore) è obbligatorio")
        return self


class SenML(BaseModel):
    """
    Pacchetto completo di misurazioni SenML.

    - bt: base time per tutti i record (usato per riferimento temporale)
    - bu: unità di misura globale (non usata ma definita per compatibilità)
    - user_id, execution_id: possono essere definiti a livello globale del pacchetto
    - e: lista di SenMLRecord (cioè le misurazioni vere e proprie)
    """

    bt: float                                   # Base time (timestamp di riferimento)
    bu: Optional[str] = None                    # Unità di misura globale (non utilizzata)
    user_id: Optional[str] = None               # ID utente (può essere sovrascritto nei singoli record)
    execution_id: Optional[str] = None          # ID esecuzione (può essere sovrascritto nei singoli record)
    e: List[SenMLRecord]                        # Lista di misurazioni

    model_config = {"protected_namespaces": ()} # Evita errori su nomi riservati Pydantic

    # Helpers per accedere in modo coerente agli ID effettivi
    @property
    def effective_user_id(self) -> Optional[str]:
        # Usa il campo globale o il primo presente nei record
        return self.user_id or next((r.user_id for r in self.e if r.user_id), None)

    @property
    def effective_execution_id(self) -> Optional[str]:
        # Usa il campo globale o il primo presente nei record
        return self.execution_id or next((r.execution_id for r in self.e if r.execution_id), None)
