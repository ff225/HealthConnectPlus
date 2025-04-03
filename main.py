import logging
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from models import SenML
from influxdbfun import (
    save_data_to_influx,
    delete_data_influx,
    get_latest_results,
    get_execution_data
)
from crud import find_compatible_module
from run_model import run_model_handler, run_model_no_save_handler
from database import collectionM
from time import perf_counter

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

app = FastAPI()
print("FastAPI è stata creata con successo")

@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc: HTTPException):
    logger.warning("%s %s -> %d: %s", request.method, request.url.path, exc.status_code, exc.detail)
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request, exc: RequestValidationError):
    logger.warning("%s %s -> 422: Payload malformato", request.method, request.url.path)
    return JSONResponse(status_code=422, content={"detail": exc.errors()})

def validate_sensors(data: SenML):
    known_sensors = {s for doc in collectionM.find({}, {"sensors": 1}) for s in doc.get("sensors", [])}
    input_sensors = {entry.bn for entry in data.e if entry.bn}
    unknown_sensors = input_sensors - known_sensors

    if unknown_sensors:
        logger.warning("Sensori sconosciuti ricevuti: %s", ", ".join(unknown_sensors))

@app.post("/saveData")
async def save_data(data: SenML, request: Request):
    start = perf_counter()

    for record in data.e:
        if not record.user_id:
            record.user_id = data.user_id
        if not record.execution_id:
            record.execution_id = data.execution_id

    user_id = data.effective_user_id or "anonymous"
    execution_id = data.effective_execution_id or "none"

    logger.info("Richiesta /saveData ricevuta con %d elementi (user_id=%s, execution_id=%s)", len(data.e), user_id, execution_id)

    validate_sensors(data)
    save_data_to_influx(data)

    duration = (perf_counter() - start) * 1000
    unique_sensors = {r.bn for r in data.e if r.bn}
    unique_features = {r.n[0] for r in data.e if r.n}

    logger.info("Salvataggio completato: %d punti da %d sensori / %d feature (%.2fms)",
                len(data.e), len(unique_sensors), len(unique_features), duration)

    return {
        "message": "Dati salvati con successo",
        "saved_points": len(data.e),
        "user_id": user_id,
        "execution_id": execution_id,
        "sensors": sorted(unique_sensors),
        "features": sorted(unique_features),
        "elapsed_ms": duration
    }

@app.post("/getModels")
async def get_models(data: SenML):
    logger.info("Richiesta /getModels per user_id=%s", data.effective_user_id or "anonymous")
    validate_sensors(data)
    models = find_compatible_module(data)
    if not models["models"]:
        raise HTTPException(status_code=404, detail="Nessun modello compatibile trovato")
    return models

@app.post("/runModel")
async def run_model(data: SenML):
    logger.info("Esecuzione modello (con salvataggio) per user_id=%s", data.effective_user_id or "anonymous")
    return run_model_handler(data, save=True)

#cercare di rendere "autonoma" ovvero la chiamata riceve tutti i dati necessari, non deve interrogare influxdb
#il modello dovrebbe essere eseguito in realtime  <<4 s
@app.post("/runModelNoSave")
async def run_model_no_save(data: SenML):
    logger.info("Esecuzione modelli (senza salvataggio) per user_id=%s", data.effective_user_id or "anonymous")
    return run_model_no_save_handler(data)

@app.get("/getResults")
async def get_results(
        sensor: str = Query(None),
        user_id: str = Query(None),
        execution_id: str = Query(None),
        model_name: str = Query(None),
        hours: int = Query(2, ge=1, le=168)
):
    logger.info("Recupero risultati: sensor=%s, user_id=%s, execution_id=%s, model_name=%s, hours=%d",
                sensor, user_id, execution_id, model_name, hours)
    return get_latest_results(sensor=sensor, user_id=user_id, execution_id=execution_id, hours=hours, model_name=model_name)

@app.get("/getExecutionData")
async def get_execution_data_api(user_id: str = Query(None), execution_id: str = Query(None)):
    logger.info("Richiesta dati per user_id=%s, execution_id=%s", user_id, execution_id)
    return get_execution_data(user_id=user_id, execution_id=execution_id)

@app.delete("/deleteAll")
async def delete_all():
    logger.warning("Eliminazione di tutti i dati InfluxDB")
    delete_data_influx()
    return {"message": "Tutti i dati sono stati eliminati"}
