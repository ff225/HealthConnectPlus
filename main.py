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

# Configurazione base del logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

# Inizializzazione dell'app FastAPI
app = FastAPI(
    title="API per Gestione ed Esecuzione Modelli TFLite",
    description="Sistema per la raccolta, validazione ed elaborazione dati sensoriali tramite modelli TFLite.",
    version="1.0.0"
)

# Handler per eccezioni HTTP standard
@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc: HTTPException):
    logger.warning("%s %s -> %d: %s", request.method, request.url.path, exc.status_code, exc.detail)
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})

# Handler per errori di validazione del payload (RequestValidationError)
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    logger.warning("%s %s -> 422: Payload malformato", request.method, request.url.path)
    return JSONResponse(status_code=422, content={"detail": exc.errors()})

# Funzione di validazione dei sensori presenti nel payload
def validate_sensors(data: SenML):
    known_sensors = {s for doc in collectionM.find({}, {"sensors": 1}) for s in doc.get("sensors", [])}
    input_sensors = {entry.bn for entry in data.e if entry.bn}
    unknown_sensors = input_sensors - known_sensors
    if unknown_sensors:
        logger.warning("Sensori sconosciuti ricevuti: %s", ", ".join(unknown_sensors))

@app.post(
    "/saveData",
    summary="Salvataggio dati sensoriali",
    description="""
Riceve dati SenML da uno o più sensori e li salva su InfluxDB. 
Ogni record viene associato a un `user_id` e a un `execution_id`, se non specificati direttamente nei record saranno propagati dal payload principale.
""",
    tags=["Dati"]
)
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

@app.post(
    "/getModels",
    summary="Ricerca modelli compatibili",
    description="""
Analizza i dati ricevuti e restituisce un elenco di modelli TFLite compatibili in base ai sensori e alle feature fornite.
I modelli vengono filtrati dinamicamente interrogando il database MongoDB.
""",
    tags=["Modelli"]
)
async def get_models(data: SenML):
    logger.info("Richiesta /getModels per user_id=%s", data.effective_user_id or "anonymous")
    validate_sensors(data)
    models = find_compatible_module(data)
    if not models["models"]:
        raise HTTPException(status_code=404, detail="Nessun modello compatibile trovato")
    return models

@app.post(
    "/runModel",
    summary="Esecuzione modello con salvataggio",
    description="""
Esegue uno o più modelli TFLite compatibile con i dati forniti. I risultati della predizione vengono salvati su InfluxDB.
I dati in ingresso devono essere compatibili con almeno un modello registrato.
""",
    tags=["Modelli"]
)
async def run_model(data: SenML):
    logger.info("Esecuzione modello (con salvataggio) per user_id=%s", data.effective_user_id or "anonymous")
    return run_model_handler(data, save=True)

@app.post(
    "/runModelNoSave",
    summary="Esecuzione modello senza salvataggio",
    description="""
Esegue uno o più modelli TFLite direttamente sui dati forniti, senza salvare nulla su InfluxDB.
Ideale per esecuzioni in tempo reale o per motivi di privacy.
I dati devono includere `user_id`, `execution_id`, sensori e feature in linea con i modelli disponibili.
""",
    tags=["Modelli"]
)
async def run_model_no_save(data: SenML):
    logger.info("Esecuzione modelli (senza salvataggio) per user_id=%s", data.effective_user_id or "anonymous")
    return run_model_no_save_handler(data)

@app.get(
    "/getResults",
    summary="Recupero dei risultati di inferenza",
    description="""
Restituisce i risultati delle inferenze eseguite nei precedenti intervalli di tempo.

È possibile filtrare i risultati per:
- `sensor`: sensore utilizzato (es. 'leftwrist')
- `user_id`: utente associato all'inferenza
- `execution_id`: identificativo univoco dell'esecuzione
- `model_name`: nome del modello TFLite
- `hours`: limite temporale massimo in ore (default 2, minimo 1, massimo 168)

I dati vengono recuperati da InfluxDB.
""",
    tags=["Risultati"]
)
async def get_results(
        sensor: str = Query(None, description="Nome del sensore (es. 'rightpocket')"),
        user_id: str = Query(None, description="ID dell’utente"),
        execution_id: str = Query(None, description="ID specifico dell’esecuzione"),
        model_name: str = Query(None, description="Nome del modello TFLite"),
        hours: int = Query(2, ge=1, le=168, description="Numero di ore da considerare (tra 1 e 168)")
):
    logger.info("Recupero risultati: sensor=%s, user_id=%s, execution_id=%s, model_name=%s, hours=%d",
                sensor, user_id, execution_id, model_name, hours)
    return get_latest_results(sensor=sensor, user_id=user_id, execution_id=execution_id, hours=hours, model_name=model_name)

@app.get(
    "/getExecutionData",
    summary="Recupero dati grezzi per esecuzione specifica",
    description="""
Restituisce i dati originali inviati a `/saveData` relativi a uno specifico `user_id` e `execution_id`.
Utile per verificare i dati su cui è stata effettuata l'inferenza o per eseguire nuovamente un modello.
""",
    tags=["Dati"]
)
async def get_execution_data_api(
        user_id: str = Query(None, description="ID dell’utente"),
        execution_id: str = Query(None, description="ID specifico dell’esecuzione")
):
    logger.info("Richiesta dati per user_id=%s, execution_id=%s", user_id, execution_id)
    return get_execution_data(user_id=user_id, execution_id=execution_id)

@app.delete(
    "/deleteAll",
    summary="Eliminazione totale dei dati",
    description="""
Elimina tutti i dati salvati su InfluxDB, sia input che risultati.
""",
    tags=["Manutenzione"]
)
async def delete_all():
    logger.warning("Eliminazione di tutti i dati InfluxDB")
    delete_data_influx()
    return {"message": "Tutti i dati sono stati eliminati"}
