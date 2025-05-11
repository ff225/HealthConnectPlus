from pydantic import BaseModel, field_validator, model_validator
from typing import Optional, List, Literal
from datetime import datetime


class SenMLRecord(BaseModel):
    """
    Rappresenta una singola misurazione SenML.

    - `n` può essere fornito come lista di una sola stringa **oppure** come stringa
      semplice; in quest’ultimo caso viene convertito internamente in lista.
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
        # Consente sia "accX" che ["accX"]
        if isinstance(v, str):
            return [v]
        return v

    @field_validator("n")
    @classmethod
    def check_single_feature(cls, v):
        if not isinstance(v, list) or len(v) != 1:
            raise ValueError("Il campo 'n' deve essere una lista contenente una sola feature")
        return v

    @field_validator("v")
    @classmethod
    def check_value_is_numeric(cls, v):
        if v is not None and not isinstance(v, (int, float)):
            raise ValueError("Il campo 'v' deve essere numerico")
        return v

    @field_validator("t")
    @classmethod
    def check_time_present(cls, v):
        if v is None:
            raise ValueError("Il campo 't' (timestamp) è obbligatorio")
        return v

    @model_validator(mode="after")
    def check_required_fields(self):
        if not self.bn:
            raise ValueError("Il campo 'bn' (sensore) è obbligatorio")
        return self


class SenML(BaseModel):
    """
    Pacchetto completo di misurazioni SenML.
    """

    bt: float                                   # Base time
    bu: Optional[str] = None                    # Unità di misura globale
    user_id: Optional[str] = None
    execution_id: Optional[str] = None
    selection_mode: Optional[Literal["all", "best", "named"]] = "all"
    model_name: Optional[str] = None
    e: List[SenMLRecord]

    model_config = {"protected_namespaces": ()}

    # Helpers per ricavare l'utente / esecuzione effettivi
    @property
    def effective_user_id(self) -> Optional[str]:
        return self.user_id or next((r.user_id for r in self.e if r.user_id), None)

    @property
    def effective_execution_id(self) -> Optional[str]:
        return self.execution_id or next((r.execution_id for r in self.e if r.execution_id), None)
