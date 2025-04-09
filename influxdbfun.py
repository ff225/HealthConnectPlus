import logging
import json
from datetime import datetime
from fastapi import HTTPException
from influxdb_client import Point, InfluxDBClient
from influxdb_client.client.exceptions import InfluxDBError
from models import SenML
from database import (
    write_api,
    query_api,
    INFLUXDB_BUCKET,
    INFLUXDB_RESULTS_BUCKET,
    INFLUXDB_ORG,
    INFLUXDB_URL,
    INFLUXDB_TOKEN
)

logger = logging.getLogger(__name__)


def save_data_to_influx(data: SenML):
    """
    Salva i dati grezzi dei sensori su InfluxDB.
    Ogni record viene convertito in un punto InfluxDB e scritto nel bucket principale.
    """
    points = []
    user_id = data.user_id or "anonymous"  # Default se non specificato

    for record in data.e:
        try:
            feature_name = record.n[0]  # Nome della feature
            if record.v is None or not isinstance(record.v, (int, float)):
                continue  # Salta valori non validi

            point = (
                Point("Sensor_data")
                .tag("sensor", record.bn or "unknown")  # Tag per sensore
                .tag("user_id", user_id)                # Tag per utente
                .tag("execution_id", record.execution_id or "none")  # Tag per esecuzione
                .field(feature_name, float(record.v))   # Feature e valore
                .field("unit", record.u or "")          # Eventuale unità di misura
                .time(record.t or datetime.utcnow())    # Timestamp
            )
            points.append(point)
        except Exception as e:
            logger.warning("Errore nella creazione del punto InfluxDB: %s", e)

    if points:
        BATCH_SIZE = 1000  # Scrittura batch per efficienza
        for i in range(0, len(points), BATCH_SIZE):
            batch = points[i:i + BATCH_SIZE]
            write_api.write(bucket=INFLUXDB_BUCKET, record=batch)
            logger.info("Scritti %d punti (batch %d)", len(batch), i // BATCH_SIZE + 1)

        write_api.flush()  # Forza lo svuotamento del buffer
        logger.info("%d punti totali salvati per user_id=%s", len(points), user_id)
    else:
        logger.warning("Nessun punto valido da salvare")


def save_model_output_to_influx(sensor: str, output, user_id: str = "anonymous", execution_id: str = "none", model_name: str = "unknown"):
    """
    Salva l'output del modello su InfluxDB (bucket dei risultati).
    L'output è serializzato in formato JSON.
    """
    try:
        logger.info("Salvataggio output modello %s per %s (user_id=%s, execution_id=%s)", model_name, sensor, user_id, execution_id)
        point = (
            Point("ModelOutput")
            .tag("sensor", sensor)
            .tag("user_id", user_id)
            .tag("execution_id", execution_id)
            .tag("model_name", model_name)
            .field("result", json.dumps(output.tolist()))  # Serializza output (es. array NumPy)
            .time(datetime.utcnow())
        )
        write_api.write(bucket=INFLUXDB_RESULTS_BUCKET, record=point)
        logger.info("Output salvato correttamente per %s", sensor)
    except Exception as e:
        logger.error("Errore durante il salvataggio output: %s", e)


def get_latest_results(sensor: str = None, user_id: str = None, execution_id: str = None, hours: int = 2, model_name: str = None):
    """
    Recupera gli output dei modelli eseguiti nelle ultime N ore.
    I risultati possono essere filtrati per sensore, utente, esecuzione e modello.
    """
    query = f'''
    from(bucket: "{INFLUXDB_RESULTS_BUCKET}")
      |> range(start: -{hours}h)
      |> filter(fn: (r) => r["_measurement"] == "ModelOutput")
    '''
    # Applica filtri opzionali
    if sensor:
        query += f'|> filter(fn: (r) => r["sensor"] == "{sensor}")\n'
    if user_id:
        query += f'|> filter(fn: (r) => r["user_id"] == "{user_id}")\n'
    if execution_id:
        query += f'|> filter(fn: (r) => r["execution_id"] == "{execution_id}")\n'
    if model_name:
        query += f'|> filter(fn: (r) => r["model_name"] == "{model_name}")\n'
    query += '|> sort(columns: ["_time"])'

    results = []
    try:
        tables = query_api.query(query)
        for table in tables:
            for r in table.records:
                results.append({
                    "time": r.get_time().isoformat(),
                    "sensor": r.values.get("sensor"),
                    "user_id": r.values.get("user_id"),
                    "execution_id": r.values.get("execution_id"),
                    "model_name": r.values.get("model_name", ""),
                    "result": r.get_value()
                })
    except Exception as e:
        logger.error("Errore nella query /getResults: %s", e)

    return {"count": len(results), "results": results}


def get_execution_data(user_id: str = None, execution_id: str = None):
    """
    Recupera sia i dati grezzi sia i risultati per una specifica esecuzione,
    filtrando per user_id ed execution_id.
    """

    def build_query(bucket: str, measurement: str):
        # Costruisce una query parametrica su misura
        q = f'''
        from(bucket: "{bucket}")
          |> range(start: -12h)
          |> filter(fn: (r) => r["_measurement"] == "{measurement}")
        '''
        if user_id:
            q += f'|> filter(fn: (r) => r["user_id"] == "{user_id}")\n'
        if execution_id:
            q += f'|> filter(fn: (r) => r["execution_id"] == "{execution_id}")\n'
        return q + '|> sort(columns: ["_time"])'

    input_data, output_data = [], []

    # Recupera dati grezzi dei sensori
    try:
        for r in query_api.query(build_query(INFLUXDB_BUCKET, "Sensor_data")):
            for row in r.records:
                input_data.append({
                    "type": "input",
                    "time": row.get_time().isoformat(),
                    "sensor": row.values.get("sensor"),
                    "user_id": row.values.get("user_id"),
                    "execution_id": row.values.get("execution_id"),
                    "field": row.get_field(),
                    "value": row.get_value()
                })
    except Exception as e:
        logger.error("Errore lettura input: %s", e)

    # Recupera output del modello
    try:
        for r in query_api.query(build_query(INFLUXDB_RESULTS_BUCKET, "ModelOutput")):
            for row in r.records:
                output_data.append({
                    "type": "output",
                    "time": row.get_time().isoformat(),
                    "sensor": row.values.get("sensor"),
                    "user_id": row.values.get("user_id"),
                    "execution_id": row.values.get("execution_id"),
                    "model_name": row.values.get("model_name", ""),
                    "result": row.get_value()
                })
    except Exception as e:
        logger.error("Errore lettura output: %s", e)

    return {
        "execution_id": execution_id,
        "user_id": user_id,
        "inputs": input_data,
        "outputs": output_data
    }


def delete_data_influx(user_id: str = None, execution_id: str = None):
    """
    Elimina i dati da InfluxDB, sia input che output, in base a user_id ed execution_id.
    Se non specificati, elimina tutti i dati.
    """
    client = InfluxDBClient(
        url=INFLUXDB_URL,
        token=INFLUXDB_TOKEN,
        org=INFLUXDB_ORG,
        timeout=60_000  # 60 secondi
    )

    delete_api = client.delete_api()
    start, stop = "1970-01-01T00:00:00Z", "2100-01-01T00:00:00Z"

    # Costruisce il predicato di cancellazione per input e output
    pred_input = '_measurement="Sensor_data"'
    pred_output = '_measurement="ModelOutput"'

    if user_id and execution_id:
        pred_input += f' AND user_id="{user_id}" AND execution_id="{execution_id}"'
        pred_output += f' AND user_id="{user_id}" AND execution_id="{execution_id}"'

    try:
        delete_api.delete(start, stop, bucket=INFLUXDB_BUCKET, org=INFLUXDB_ORG, predicate=pred_input)
        delete_api.delete(start, stop, bucket=INFLUXDB_RESULTS_BUCKET, org=INFLUXDB_ORG, predicate=pred_output)
        logger.info("Dati eliminati: %s / %s", pred_input, pred_output)
    except InfluxDBError as e:
        logger.exception("Errore InfluxDB durante eliminazione")
        raise HTTPException(
            status_code=500,
            detail=f"Errore InfluxDB: {getattr(e, 'code', 'unknown')} - {getattr(e, 'message', str(e))}"
        )
    except Exception as e:
        logger.exception("Errore sconosciuto durante eliminazione")
        raise HTTPException(
            status_code=500,
            detail=f"Errore interno durante la cancellazione dati: {str(e)}"
        )
    finally:
        client.close()
