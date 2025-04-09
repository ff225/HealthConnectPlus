from pydantic import BaseModel, field_validator, model_validator
from typing import Optional, List, Literal
from datetime import datetime

class SenMLRecord(BaseModel):
    # Rappresenta una singola misurazione secondo lo standard SenML

    bn: Optional[str] = None  # Base name del sensore (es. "leftwrist", "rightpocket") – obbligatorio per identificare la sorgente
    n: List[str]              # Nome della feature misurata, contenuto in una lista con un solo elemento (es. ["accx"])
    v: Optional[float] = None # Valore numerico della misurazione
    vs: Optional[str] = None  # Valore stringa della misurazione
    u: Optional[str] = None   # Unità di misura (es. "m/s2", "g")
    t: Optional[datetime] = None  # Timestamp ISO8601 della misurazione – obbligatorio per il tracciamento temporale
    execution_id: Optional[str] = None  # ID dell’esecuzione associata al dato – per tracciabilità
    user_id: Optional[str] = None       # ID dell’utente associato al dato – per contesto multiutente

    @field_validator("n")
    @classmethod
    def check_single_feature(cls, v):
        # Verifica che 'n' contenga una sola feature
        if not isinstance(v, list) or len(v) != 1:
            raise ValueError("Il campo 'n' deve essere una lista contenente una sola feature")
        return v

    @field_validator("v")
    @classmethod
    def check_value_is_numeric(cls, v):
        # Verifica che 'v' sia numerico se presente
        if not isinstance(v, (int, float)):
            raise ValueError("Il campo 'v' deve essere numerico")
        return v

    @field_validator("t")
    @classmethod
    def check_time_present(cls, v):
        # Verifica che il campo 't' (timestamp) sia presente
        if v is None:
            raise ValueError("Il campo 't' (timestamp) è obbligatorio")
        return v

    @model_validator(mode="after")
    def check_required_fields(self):
        # Verifica che 'bn' (nome del sensore) sia presente
        if not self.bn:
            raise ValueError("Il campo 'bn' (sensore) è obbligatorio")
        return self


class SenML(BaseModel):
    # Rappresenta un pacchetto completo di misurazioni secondo SenML

    bt: float  # Base time: riferimento temporale per tutte le misurazioni
    bu: Optional[str] = None  # Unità di misura globale per i valori, se non definita nei singoli record
    user_id: Optional[str] = None       # ID dell’utente associato al pacchetto di dati
    execution_id: Optional[str] = None  # ID dell’esecuzione associata al pacchetto
    selection_mode: Optional[Literal["all", "best", "named"]] = "all"
    # Modalità di selezione dei modelli:
    # - "all": tutti i modelli compatibili
    # - "best": il modello migliore
    # - "named": solo quello specificato in 'model_name'

    model_name: Optional[str] = None  # Nome del modello da usare (richiesto se selection_mode == "named")
    e: List[SenMLRecord]              # Elenco delle misurazioni (record) associate al pacchetto

    model_config = {
        "protected_namespaces": ()  # Permette l’uso di proprietà personalizzate (es. effective_user_id)
    }

    @property
    def effective_user_id(self) -> Optional[str]:
        # Restituisce lo user_id prioritario: prima dal pacchetto, poi dal primo record disponibile
        return self.user_id or next((r.user_id for r in self.e if r.user_id), None)

    @property
    def effective_execution_id(self) -> Optional[str]:
        # Restituisce l’execution_id prioritario: prima dal pacchetto, poi dal primo record disponibile
        return self.execution_id or next((r.execution_id for r in self.e if r.execution_id), None)
