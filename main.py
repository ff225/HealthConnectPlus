import logging
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from typing import Dict, Optional
from time import perf_counter

from influxdbfun import (
    delete_data_influx,
    get_latest_results,
    get_execution_data
)
from crud import find_compatible_module, _cached_sensors_features
from run_model import run_model_handler
from database import collectionM
from queue_pg import enqueue
from models import SenML

# Configurazione del logging per tutta l'applicazione
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

# Inizializzazione dell'app FastAPI principale. Tutti gli endpoint sono registrati su questa istanza.
app = FastAPI(
    title="API per Gestione ed Esecuzione Modelli TFLite",
    description="Sistema per la raccolta, validazione ed elaborazione dati sensoriali tramite modelli TFLite.",
    version="1.0.0"
)

# Gestione eccezioni HTTP generiche
@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    logger.warning("%s %s -> %d: %s", request.method, request.url.path, exc.status_code, exc.detail)
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})

# Gestione eccezioni per payload non validi (es. schema SenML malformato)
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    logger.warning("%s %s -> 422: Payload malformato", request.method, request.url.path)
    return JSONResponse(status_code=422, content={"detail": exc.errors()})

# Endpoint per il salvataggio asincrono dei dati sensoriali in formato SenML.
# I dati vengono accodati nella coda PostgreSQL e salvati successivamente da worker separati.
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

# Endpoint che riceve dati in formato SenML e restituisce i modelli TFLite compatibili
# basandosi su sensori, feature e shape richiesti.
@app.post("/getModels", summary="Ricerca modelli compatibili", tags=["Modelli"])
async def get_models(data: Dict):
    try:
        senml_data = SenML(**data)
    except Exception as e:
        logger.exception("Payload non valido per SenML")
        raise HTTPException(status_code=422, detail="Payload non conforme al formato SenML")

    return find_compatible_module(senml_data)

# Endpoint per l'esecuzione di un modello con salvataggio dei risultati.
# La selezione del modello può essere automatica (selection_mode="best") o nominativa.
@app.post("/runModel", summary="Esecuzione modello con salvataggio", tags=["Modelli"])
async def run_model(data: Dict):
    return run_model_handler(data, save=True)

# Endpoint per l'esecuzione del modello SENZA salvataggio dei risultati.
# Utile per test o uso interattivo da app che non richiedono persistenza.
@app.post("/runModelNoSave", summary="Esecuzione modello senza salvataggio", tags=["Modelli"])
async def run_model_no_save(data: Dict):
    return run_model_handler(data, save=False)

# Endpoint per il recupero dei risultati dei modelli.
# Supporta output flat o strutturato (FOG-ready) se fog_ready=True.
@app.get("/getResults", summary="Recupero risultati modello", tags=["Risultati"])
def get_results(
        user_id: str,
        execution_id: str,
        model_name: Optional[str] = None,
        sensor: Optional[str] = None,
        limit_per_feature: Optional[int] = None,
        hours: int = 2,
        fog_ready: bool = False
):
    if fog_ready:
        from influxdbfun import get_latest_results_grouped_matrix
        return get_latest_results_grouped_matrix(
            user_id=user_id,
            execution_id=execution_id,
            model_name=model_name,
            sensor=sensor,
            hours=hours
        )
    else:
        return get_latest_results(
            user_id=user_id,
            execution_id=execution_id,
            model_name=model_name,
            sensor=sensor,
            limit_per_feature=limit_per_feature,
            hours=hours
        )

# Restituisce i dati sensoriali grezzi salvati in InfluxDB, usati tipicamente per debug o verifica.
@app.get("/getExecutionData", summary="Recupero dati grezzi", tags=["Dati"])
async def get_execution_data_api(user_id: str = Query(...), execution_id: str = Query(...)):
    return get_execution_data(user_id, execution_id)

# Elimina tutti i dati (sensor_data + model_output) associati a user_id ed execution_id.
# Pulisce anche la cache usata per velocizzare la compatibilità dei modelli.
@app.delete("/deleteAll", summary="Elimina dati per utente ed esecuzione", tags=["Manutenzione"])
async def delete_all(
        user_id: str = Query(..., description="ID utente"),
        execution_id: str = Query(..., description="ID esecuzione"),
):
    _cached_sensors_features.clear()
    try:
        delete_data_influx(user_id=user_id, execution_id=execution_id)
        return {"message": f"Dati eliminati per user_id={user_id}, execution_id={execution_id}"}
    except Exception as e:
        logger.exception("Errore cancellazione dati")
        raise HTTPException(status_code=500, detail="Errore durante la cancellazione dei dati")
