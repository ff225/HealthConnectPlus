from typing import Dict, Any
from models import SenML
from database import collectionM, query_api, INFLUXDB_BUCKET
import logging
from functools import lru_cache

logger = logging.getLogger(__name__)

# === CRUD: GESTIONE MODELLI COMPATIBILI E QUERY SENSORIALI ===
# Questo modulo contiene la logica per:
# - interrogare InfluxDB per sensori/feature disponibili
# - identificare i modelli compatibili da MongoDB
# Viene usato da /getModels e run_model.py

# Cache temporanea per evitare richieste duplicate a InfluxDB
_cached_sensors_features = {}

@lru_cache(maxsize=128)
def _cached_sensor_features(user_id: str, execution_id: str):
    """
    Restituisce sensori/feature da InfluxDB con caching LRU (max 128 combinazioni).
    """
    return get_sensors_and_features_from_influx(user_id, execution_id)

def _load_all_models():
    """
    Recupera tutti i modelli salvati su MongoDB.
    Campo usato da runModel e /getModels.
    """
    return list(collectionM.find({}, {
        "sensors": 1,
        "features": 1,
        "url": 1,
        "model_name": 1,
        "input_shape": 1,
        "execution_requirements": 1,
        "description": 1
    }))

def get_sensors_and_features_from_influx(user_id: str, execution_id: str) -> Dict[str, set]:
    """
    Interroga InfluxDB per determinare sensori e feature realmente salvate.

    Output: dizionario del tipo
    {
      "leftwrist": {"accX", "accY", ...},
      "rightpocket": {"gyroZ", ...}
    }
    """
    query = f'''
    from(bucket: "{INFLUXDB_BUCKET}")
      |> range(start: -4d)
      |> filter(fn: (r) => r["_measurement"] == "Sensor_data")
      |> filter(fn: (r) => r["user_id"] == "{user_id}")
      |> filter(fn: (r) => r["execution_id"] == "{execution_id}")
      |> keep(columns: ["sensor", "_field"])
      |> group()
    '''
    result = {}
    try:
        tables = query_api.query(query)
        for table in tables:
            for record in table.records:
                sensor = record.values.get("sensor")
                feature = record.values.get("_field")
                if sensor and feature:
                    result.setdefault(sensor, set()).add(feature)
    except Exception as e:
        logger.warning("Errore nel recupero dati da InfluxDB: %s", e)

    return result

def find_compatible_module(data: SenML) -> Dict[str, Any]:
    """
    Trova i modelli compatibili dato un oggetto SenML.

    Funziona in 2 modalità:
    - Se data.e è presente → usa direttamente sensori/feature del payload
    - Altrimenti → recupera sensori/feature da InfluxDB (via user_id, execution_id)
    """
    user_id = data.effective_user_id
    execution_id = data.effective_execution_id

    # --- Rilevamento sensori e feature disponibili
    if data.e:
        sensor_features = {}
        for entry in data.e:
            if entry.bn:
                sensor_features.setdefault(entry.bn, set()).update(entry.n)
    elif user_id and execution_id:
        logger.info("Recupero sensori e feature da Influx per user_id=%s, execution_id=%s", user_id, execution_id)
        sensor_features = _cached_sensor_features(user_id, execution_id)
    else:
        logger.warning("Payload privo di dati e user_id/execution_id non forniti.")
        return {"models": []}

    if not sensor_features:
        logger.warning("Nessun sensore/feature trovato disponibile.")
        return {"models": []}

    # --- Verifica modelli compatibili
    matching_models = []

    for model in _load_all_models():
        model_name = model.get("model_name", "")
        model_url = model.get("url")
        model_sensors = model.get("sensors", [])
        model_features = model.get("features", [])
        input_shape = model.get("input_shape", [1, 1, 1])

        if not model_sensors or not model_features or not model_url:
            logger.warning("Modello incompleto: %s", model_name)
            continue

        structured_features = []
        is_compatible = True

        try:
            # Caso: features raggruppate per sensore
            if isinstance(model_features[0], list):
                if len(model_sensors) != len(model_features):
                    logger.warning("Mismatch numero sensori/features su modello %s", model_name)
                    continue
                for sensor, feats in zip(model_sensors, model_features):
                    available_feats = sensor_features.get(sensor, set())
                    if not available_feats.issuperset(feats):
                        is_compatible = False
                        break
                    structured_features.append((sensor, feats))
            # Caso legacy: singolo sensore e lista flat di feature
            else:
                sensor = model_sensors[0]
                feats = model_features
                available_feats = sensor_features.get(sensor, set())
                if not available_feats.issuperset(feats):
                    is_compatible = False
                else:
                    structured_features.append((sensor, feats))

            # Se compatibile → aggiungi
            if is_compatible:
                matching_models.append({
                    "url": model_url,
                    "description": model.get("description", ""),
                    "input_shape": input_shape,
                    "features": structured_features,
                    "sensors": model_sensors,
                    "execution_requirements": model.get("execution_requirements", ""),
                    "model_name": model_name,
                    "priority": model.get("priority", 999)
                })

        except Exception as e:
            logger.warning("Errore compatibilità modello %s: %s", model_name, str(e))

    logger.info("Modelli compatibili trovati: %d", len(matching_models))
    return {"models": matching_models}
