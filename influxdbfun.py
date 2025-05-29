import logging
from time import sleep
from datetime import datetime
from typing import List, Dict, Optional
from influxdb_client import Point
from influxdb_client.client.write_api import SYNCHRONOUS
from database import influx_client, INFLUXDB_ORG as ORG, INFLUXDB_BUCKET as BUCKET_DATA, INFLUXDB_RESULTS_BUCKET as BUCKET_RESULTS, query_api
from models import SenML

logger = logging.getLogger(__name__)

# Lista predefinita delle feature accettate nei dati grezzi
FEATURE_LIST = ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"]

# --- FUNZIONE: Salvataggio Dati Sensoriali in InfluxDB --- #
def save_data_to_influx(data: SenML) -> int:
    """
    Salva i dati grezzi nel bucket `sensor_data`.
    I dati sono in formato SenML, già validati.
    Scrittura sincrona con retry in caso di errore.
    """
    write_api = influx_client.write_api(write_options=SYNCHRONOUS)
    points = []

    for record in data.e:
        if not (record.bn and record.n and record.t is not None):
            continue

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

# --- FUNZIONE: Salvataggio Output Dettagliato (per-feature) --- #
def save_model_output_to_influx(points: List[dict]) -> None:
    """
    Salva output dettagliato per ogni punto (sensor-feature-time_idx).
    Usato quando il modello ritorna dati flat, non in matrice.
    """
    write_api = influx_client.write_api(write_options=SYNCHRONOUS)
    max_retries = 5
    retry_delay = 0.5
    influx_points = []

    try:
        for p in points:
            point = (
                Point("model_output")
                .tag("user_id", p["user_id"])
                .tag("execution_id", p["execution_id"])
                .tag("model_name", p["model_name"])
                .tag("sensor", p["sensor"])
                .tag("feature", p["feature"])
                .tag("time_idx", str(p.get("time_idx", 0)))
                .field("output", p["output"])
            )
            influx_points.append(point)

        if not influx_points:
            logger.warning("Nessun punto valido da salvare (output modello dettagliato)")
            return

        for attempt in range(max_retries):
            try:
                write_api.write(bucket=BUCKET_RESULTS, org=ORG, record=influx_points)
                logger.info(f"Salvati {len(influx_points)} punti di output modello su InfluxDB (bucket={BUCKET_RESULTS})")
                return
            except Exception as e:
                if attempt < max_retries - 1:
                    logger.warning(f"Tentativo {attempt+1}/{max_retries} fallito nel salvataggio output: {e}")
                    sleep(retry_delay)
                    retry_delay *= 2
                else:
                    logger.error(f"Errore definitivo salvataggio output modello: {e}")
                    raise
    except Exception as e:
        logger.exception("Errore durante la generazione dei punti output modello: %s", e)

# --- FUNZIONE: Salvataggio Output come Matrice (FOG-ready) --- #
def save_model_output_matrix(
        user_id: str,
        execution_id: str,
        model_name: str,
        sensor: str,
        features: List[str],
        output_matrix: List[List[float]]
) -> None:
    """
    Salva output modello come matrice tempo-feature.
    Ogni riga è un time step, ogni colonna una feature.
    """
    write_api = influx_client.write_api(write_options=SYNCHRONOUS)
    max_retries = 5
    retry_delay = 0.5
    influx_points = []

    try:
        for time_idx, row in enumerate(output_matrix):
            for feature_idx, value in enumerate(row):
                if feature_idx >= len(features):
                    logger.warning("Indice fuori range: feature_idx=%d >= len(features)=%d", feature_idx, len(features))
                    continue
                point = (
                    Point("model_output")
                    .tag("user_id", user_id)
                    .tag("execution_id", execution_id)
                    .tag("model_name", model_name)
                    .tag("sensor", sensor)
                    .tag("feature", features[feature_idx])
                    .tag("time_idx", str(time_idx))
                    .field("output", float(value))
                )
                influx_points.append(point)

        if not influx_points:
            logger.warning("Nessun punto valido da salvare da matrice output modello")
            return

        for attempt in range(max_retries):
            try:
                write_api.write(bucket=BUCKET_RESULTS, org=ORG, record=influx_points)
                logger.info("Salvati %d punti da matrice output modello (bucket=%s)", len(influx_points), BUCKET_RESULTS)
                return
            except Exception as e:
                if attempt < max_retries - 1:
                    logger.warning("Tentativo %d/%d fallito salvataggio matrice: %s", attempt+1, max_retries, e)
                    sleep(retry_delay)
                    retry_delay *= 2
                else:
                    logger.error("Errore definitivo salvataggio matrice output modello: %s", e)
                    raise

    except Exception as e:
        logger.exception("Errore durante preparazione salvataggio matrice output: %s", e)

# --- DELETE --- #
def delete_data_influx(user_id: str, execution_id: str) -> None:
    """
    Elimina tutti i dati sensoriali e di output da InfluxDB per una coppia user/execution.
    """
    start = "1970-01-01T00:00:00Z"
    stop = datetime.utcnow().isoformat() + "Z"

    predicate_sensor = f'_measurement="sensor_data" AND user_id="{user_id}" AND execution_id="{execution_id}"'
    predicate_output = f'_measurement="model_output" AND user_id="{user_id}" AND execution_id="{execution_id}"'

    try:
        delete_api = influx_client.delete_api()
        logger.info(f"[DELETE] SensorData → {predicate_sensor}")
        delete_api.delete(start, stop, predicate_sensor, bucket=BUCKET_DATA, org=ORG)
        logger.info(f"[DELETE] ModelOutput → {predicate_output}")
        delete_api.delete(start, stop, predicate_output, bucket=BUCKET_RESULTS, org=ORG)
        logger.info(f"Cancellati dati per user_id={user_id}, execution_id={execution_id}")
    except Exception as e:
        logger.exception(f"Errore durante la cancellazione da InfluxDB: {e}")
        raise

# --- GET RESULTS: formato flat --- #
def get_latest_results(
        user_id: str,
        execution_id: str,
        model_name: Optional[str] = None,
        sensor: Optional[str] = None,
        limit_per_feature: Optional[int] = None,
        hours: int = 2,
        fog_ready: bool = False
) -> List[Dict]:
    """
    Recupera i risultati del modello in formato flat (riga = 1 valore per feature).
    """
    if fog_ready:
        return get_latest_results_grouped_matrix(user_id, execution_id, model_name, sensor, hours)

    try:
        query = f'''
        from(bucket: "{BUCKET_RESULTS}")
          |> range(start: -{hours}h)
          |> filter(fn: (r) => r["_measurement"] == "model_output")
          |> filter(fn: (r) => r["user_id"] == "{user_id}")
          |> filter(fn: (r) => r["execution_id"] == "{execution_id}")
        '''
        if model_name:
            query += f'  |> filter(fn: (r) => r["model_name"] == "{model_name}")\n'
        if sensor:
            query += f'  |> filter(fn: (r) => r["sensor"] == "{sensor}")\n'

        query += '''
          |> sort(columns: ["_time"], desc: true)
        '''

        if limit_per_feature:
            query += '''
              |> group(columns: ["sensor", "feature"])
              |> limit(n: %d)
            ''' % limit_per_feature

        query += '''
          |> keep(columns: ["_time", "_value", "sensor", "feature", "model_name"])
        '''

        tables = query_api.query(query=query, org=ORG)
        results = []

        for table in tables:
            for record in table.records:
                results.append({
                    "model_name": record.values.get("model_name"),
                    "sensor": record.values.get("sensor"),
                    "feature": record.values.get("feature"),
                    "timestamp": str(record.get_time()),
                    "output": record.get_value()
                })

        return results

    except Exception as e:
        logger.error(f"Errore durante il recupero output modello: {e}")
        return []

# --- GET RESULTS: formato FOG-ready --- #
def get_latest_results_grouped_matrix(
        user_id: str,
        execution_id: str,
        model_name: Optional[str] = None,
        sensor: Optional[str] = None,
        hours: int = 2
) -> List[Dict]:
    """
    Recupera output modello come matrice tempo-feature (FOG-ready).
    Utile per app mobili e dispositivi edge.
    """
    try:
        query = f'''
        from(bucket: "{BUCKET_RESULTS}")
          |> range(start: -{hours}h)
          |> filter(fn: (r) => r["_measurement"] == "model_output")
          |> filter(fn: (r) => r["user_id"] == "{user_id}")
          |> filter(fn: (r) => r["execution_id"] == "{execution_id}")
        '''
        if model_name:
            query += f'  |> filter(fn: (r) => r["model_name"] == "{model_name}")\n'
        if sensor:
            query += f'  |> filter(fn: (r) => r["sensor"] == "{sensor}")\n'

        query += '''
          |> sort(columns: ["_time"])
          |> keep(columns: ["_time", "_value", "sensor", "feature", "model_name"])
        '''

        tables = query_api.query(query=query, org=ORG)

        grouped_outputs = {}
        timestamp_by_group = {}

        for table in tables:
            for record in table.records:
                model = record.values["model_name"]
                sensor = record.values["sensor"]
                feature = record.values["feature"]
                value = record.get_value()
                ts = record.get_time()

                key = (model, sensor)
                if key not in grouped_outputs:
                    grouped_outputs[key] = {}
                    timestamp_by_group[key] = ts

                grouped_outputs[key].setdefault(feature, []).append(value)

        results = []
        for (model, sensor), feature_map in grouped_outputs.items():
            features_sorted = sorted(feature_map.keys())
            output_matrix = list(zip(*[feature_map[feat] for feat in features_sorted]))

            results.append({
                "user_id": user_id,
                "execution_id": execution_id,
                "model_name": model,
                "sensor": sensor,
                "output_matrix": [list(row) for row in output_matrix],
                "shape": [len(output_matrix), len(features_sorted)],
                "timestamp": timestamp_by_group[(model, sensor)].isoformat()
            })

        return results

    except Exception as e:
        logger.exception("Errore durante la ricostruzione matrice output FOG-ready: %s", e)
        return []

# --- GET: Dati grezzi --- #
def get_execution_data(user_id: str, execution_id: str) -> List[Dict]:
    """
    Recupera i dati grezzi dal bucket sensor_data.
    Usa pivot() per aggregare tutte le feature in un unico oggetto per timestamp.
    """
    try:
        query = f'''
        from(bucket: "{BUCKET_DATA}")
          |> range(start: -4d)
          |> filter(fn: (r) => r._measurement == "sensor_data")
          |> filter(fn: (r) => r["user_id"] == "{user_id}" and r["execution_id"] == "{execution_id}")
          |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
          |> sort(columns: ["_time"])
        '''

        tables = query_api.query(query=query, org=ORG)
        results = []

        for table in tables:
            for record in table.records:
                result = {
                    "time": str(record.get_time()),
                    "sensor": record.values.get("bn"),
                    "data": {k: v for k, v in record.values.items() if k in FEATURE_LIST}
                }
                results.append(result)

        return results

    except Exception as e:
        logger.error(f"Errore durante lettura da InfluxDB per /getExecutionData: {e}")
        return []
