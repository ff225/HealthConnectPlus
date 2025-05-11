import logging
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from typing import Dict

from influxdbfun import (
    delete_data_influx,
    get_latest_results,
    get_execution_data
)
from crud import find_compatible_module, _cached_sensors_features
from run_model import run_model_handler
from database import collectionM
from queue_pg import enqueue
from time import perf_counter
from models import SenML

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

app = FastAPI(
    title="API per Gestione ed Esecuzione Modelli TFLite",
    description="Sistema per la raccolta, validazione ed elaborazione dati sensoriali tramite modelli TFLite.",
    version="1.0.0"
)

@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    logger.warning("%s %s -> %d: %s", request.method, request.url.path, exc.status_code, exc.detail)
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    logger.warning("%s %s -> 422: Payload malformato", request.method, request.url.path)
    return JSONResponse(status_code=422, content={"detail": exc.errors()})

@app.post("/saveData", summary="Salvataggio dati sensoriali", tags=["Dati"])
async def save_data(data: Dict):
    start = perf_counter()
    e = data.get("e", [])
    user_id = data.get("user_id") or next((r.get("user_id") for r in e if r.get("user_id")), None)
    execution_id = data.get("execution_id") or next((r.get("execution_id") for r in e if r.get("execution_id")), None)

    if not user_id or not execution_id:
        raise HTTPException(status_code=400, detail="user_id ed execution_id sono obbligatori nel payload o nei record")

    for record in e:
        record.setdefault("user_id", user_id)
        record.setdefault("execution_id", execution_id)

    logger.info("Richiesta /saveData: %d elementi (user_id=%s, exec_id=%s)", len(e), user_id, execution_id)

    try:
        enqueue(data)
    except Exception as ex:
        logger.exception("Errore accodamento dati")
        raise HTTPException(status_code=500, detail="Impossibile accodare i dati per il salvataggio")

    duration = (perf_counter() - start) * 1000
    return {
        "message": "Dati accodati con successo",
        "user_id": user_id,
        "execution_id": execution_id,
        "records_count": len(e),
        "elapsed_ms": duration
    }

@app.post("/getModels", summary="Ricerca modelli compatibili", tags=["Modelli"])
async def get_models(data: Dict):
    try:
        senml_data = SenML(**data)
    except Exception as e:
        logger.exception("Payload non valido per SenML")
        raise HTTPException(status_code=422, detail="Payload non conforme al formato SenML")

    return find_compatible_module(senml_data)

@app.post("/runModel", summary="Esecuzione modello con salvataggio", tags=["Modelli"])
async def run_model(data: Dict):
    return run_model_handler(data, save=True)

@app.post("/runModelNoSave", summary="Esecuzione modello senza salvataggio", tags=["Modelli"])
async def run_model_no_save(data: Dict):
    return run_model_handler(data, save=False)

#FOG
@app.get("/getResults", summary="Recupero risultati modello", tags=["Risultati"])
async def get_results(
        sensor: str = Query(None),
        user_id: str = Query(None),
        execution_id: str = Query(None),
        model_name: str = Query(None),
        hours: int = Query(2, ge=1, le=168)
):
    return get_latest_results(sensor=sensor, user_id=user_id, execution_id=execution_id, model_name=model_name, hours=hours)

@app.get("/getExecutionData", summary="Recupero dati grezzi", tags=["Dati"])
async def get_execution_data_api(user_id: str = Query(...), execution_id: str = Query(...)):
    return get_execution_data(user_id, execution_id)

#per data, per sensore,
@app.delete("/deleteAll", summary="Elimina tutti i dati", tags=["Manutenzione"])
async def delete_all():
    _cached_sensors_features.clear()
    delete_data_influx()
    return {"message": "Tutti i dati sono stati eliminati"}

#priorità grafici per paper