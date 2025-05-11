import logging
from time import sleep
from datetime import datetime
from typing import List
from influxdb_client import Point
from influxdb_client.client.write_api import SYNCHRONOUS

from database import influx_client, INFLUXDB_ORG as ORG, INFLUXDB_BUCKET as BUCKET_DATA, INFLUXDB_RESULTS_BUCKET as BUCKET_RESULTS
from models import SenML

# Logger
logger = logging.getLogger(__name__)

# --- FUNZIONE: salvataggio dati sensoriali (Sensor_data) --- #
def save_data_to_influx(data: SenML) -> int:
    """
    Salva i dati SenML nel bucket 'healthconnect' di InfluxDB.
    """
    write_api = influx_client.write_api(write_options=SYNCHRONOUS)
    points = []

    for record in data.e:
        if not (record.bn and record.n and record.t is not None):
            continue  # Skippa record incompleti

        user_id = record.user_id or data.user_id or "anonymous"
        execution_id = record.execution_id or data.execution_id or "none"

        point = (
            Point("sensor_data")
            .tag("sensor", record.bn)
            .tag("user_id", user_id)
            .tag("execution_id", execution_id)
            .field(record.n[0], record.v if record.v is not None else 0.0)
            .time(record.t)
        )
        points.append(point)

    if not points:
        logger.warning("Nessun punto valido da salvare")
        return 0

    max_retries = 5
    retry_delay = 0.5

    for attempt in range(max_retries):
        try:
            write_api.write(bucket=BUCKET_DATA, org=ORG, record=points)
            logger.info(f"Salvati {len(points)} punti su InfluxDB (bucket={BUCKET_DATA})")
            return len(points)
        except Exception as e:
            if attempt < max_retries - 1:
                logger.warning(f"Tentativo {attempt+1}/{max_retries} fallito: {e}")
                sleep(retry_delay)
                retry_delay *= 2
            else:
                logger.error(f"Errore definitivo salvataggio dati sensoriali: {e}")
                raise

    return 0

# --- FUNZIONE: salvataggio output modelli (Model_output) --- #
def save_model_output_to_influx(points: List[dict]) -> None:
    """
    Salva l'output del modello nel bucket 'model_results' di InfluxDB.
    """
    write_api = influx_client.write_api(write_options=SYNCHRONOUS)

    max_retries = 5
    retry_delay = 0.5

    for attempt in range(max_retries):
        try:
            for point in points:
                p = Point("model_output")
                for key, value in point.items():
                    if key == "output":
                        p = p.field(key, value)
                    else:
                        p = p.tag(key, value)
                write_api.write(bucket=BUCKET_RESULTS, org=ORG, record=p)
            logger.info(f"Salvati {len(points)} output modello su InfluxDB (bucket={BUCKET_RESULTS})")
            break
        except Exception as e:
            if attempt < max_retries - 1:
                logger.warning(f"Tentativo {attempt+1}/{max_retries} fallito output: {e}")
                sleep(retry_delay)
                retry_delay *= 2
            else:
                logger.error(f"Errore definitivo salvataggio output modello: {e}")
                raise

# --- FUNZIONE: cancellazione dati utente --- #
def delete_data_influx(user_id: str, execution_id: str) -> None:
    """
    Cancella dati sensoriali dal bucket 'healthconnect' per user_id e execution_id.
    """
    delete_api = influx_client.delete_api()

    start = "1970-01-01T00:00:00Z"
    stop = datetime.utcnow().isoformat() + "Z"
    predicate = f'_measurement="sensor_data" AND user_id="{user_id}" AND execution_id="{execution_id}"'

    try:
        delete_api.delete(start, stop, predicate, bucket=BUCKET_DATA, org=ORG)
        logger.info(f"Cancellati dati per user_id={user_id}, execution_id={execution_id} dal bucket {BUCKET_DATA}")
    except Exception as e:
        logger.error(f"Errore cancellazione dati InfluxDB: {e}")
        raise

# --- FUNZIONE: recupero ultimi risultati modello --- #
def get_latest_results(sensor: str = None, user_id: str = None, execution_id: str = None, model_name: str = None, hours: int = 2) -> list:
    """
    Recupera i risultati recenti del modello filtrati per user_id ed execution_id.
    """
    query_api = influx_client.query_api()

    query = f'''
    from(bucket: "{BUCKET_RESULTS}")
      |> range(start: -4d)
      |> filter(fn: (r) => r["_measurement"] == "model_output")
      |> filter(fn: (r) => r["user_id"] == "{user_id}")
      |> filter(fn: (r) => r["execution_id"] == "{execution_id}")
      |> pivot(rowKey:["_time"], columnKey: ["feature"], valueColumn: "_value")
    '''
    if user_id:
        query += f'  |> filter(fn: (r) => r["user_id"] == "{user_id}")\n'
    if execution_id:
        query += f'  |> filter(fn: (r) => r["execution_id"] == "{execution_id}")\n'
    if model_name:
        query += f'  |> filter(fn: (r) => r["model_name"] == "{model_name}")\n'
    if sensor:
        query += f'  |> filter(fn: (r) => r["sensor"] == "{sensor}")\n'

    query += '  |> pivot(rowKey:["_time"], columnKey: ["feature"], valueColumn: "_value")\n'
    try:
        tables = query_api.query(query=query, org=ORG)
        return [record.values for table in tables for record in table.records]
    except Exception as e:
        logger.error(f"Errore recupero risultati modello: {e}")
        return []

# --- FUNZIONE: recupero dati sensoriali grezzi --- #
def get_execution_data(user_id: str, execution_id: str) -> list:
    """
    Recupera dati sensoriali salvati in InfluxDB per una esecuzione specifica.
    """
    query_api = influx_client.query_api()

    query = f'''
    from(bucket: "{BUCKET_DATA}")
      |> range(start: -4d)
      |> filter(fn: (r) => r["_measurement"] == "sensor_data")
      |> filter(fn: (r) => r["user_id"] == "{user_id}")
      |> filter(fn: (r) => r["execution_id"] == "{execution_id}")
      |> pivot(rowKey:["_time"], columnKey: ["feature"], valueColumn: "_value")
      |> sort(columns: ["_time"])
    '''

    try:
        tables = query_api.query(query=query, org=ORG)
        return [record.values for table in tables for record in table.records]
    except Exception as e:
        logger.error(f"Errore recupero dati esecuzione: {e}")
        return []
