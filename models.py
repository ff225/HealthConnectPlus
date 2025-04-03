from pydantic import BaseModel, field_validator, model_validator
from typing import Optional, List
from datetime import datetime

class SenMLRecord(BaseModel):
    bn: Optional[str] = None
    n: List[str]
    v: Optional[float] = None
    vs: Optional[str] = None
    u: Optional[str] = None
    t: Optional[datetime] = None
    execution_id: Optional[str] = None
    user_id: Optional[str] = None

    @field_validator("n")
    @classmethod
    def check_single_feature(cls, v):
        if not isinstance(v, list) or len(v) != 1:
            raise ValueError("Il campo 'n' deve essere una lista contenente una sola feature")
        return v

    @field_validator("v")
    @classmethod
    def check_value_is_numeric(cls, v):
        if not isinstance(v, (int, float)):
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
    bt: float
    bu: Optional[str] = None
    user_id: Optional[str] = None
    execution_id: Optional[str] = None
    e: List[SenMLRecord]

    @property
    def effective_user_id(self) -> Optional[str]:
        return self.user_id or next((r.user_id for r in self.e if r.user_id), None)

    @property
    def effective_execution_id(self) -> Optional[str]:
        return self.execution_id or next((r.execution_id for r in self.e if r.execution_id), None)
