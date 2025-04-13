from typing import Dict, Any
from models import SenML
from database import collectionM, query_api, INFLUXDB_BUCKET
import logging

logger = logging.getLogger(__name__)


def get_sensors_and_features_from_influx(user_id: str, execution_id: str) -> Dict[str, set]:
    """
    Recupera da InfluxDB i sensori e le feature effettivamente salvati per una specifica esecuzione.
    Utilizza user_id ed execution_id come filtri primari. Include range(start: 0) per compatibilità InfluxDB v2.
    """
    query = f'''
    from(bucket: "{INFLUXDB_BUCKET}")
      |> range(start: 0)
      |> filter(fn: (r) => r["_measurement"] == "Sensor_data")
      |> filter(fn: (r) => r["user_id"] == "{user_id}")
      |> filter(fn: (r) => r["execution_id"] == "{execution_id}")
      |> keep(columns: ["sensor", "_field"])
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
        logger.warning("Errore nel recupero dati da InfluxDB per compatibilità modelli: %s", e)

    return result


def find_compatible_module(data: SenML) -> Dict[str, Any]:
    """
    Restituisce tutti i modelli compatibili con i dati forniti.
    Un modello è considerato compatibile se:
    - Tutti i sensori richiesti dal modello sono presenti nei dati ricevuti (campo `e`)
      oppure, se `e` è vuoto, vengono trovati nei dati già salvati su InfluxDB.
    - Per ogni sensore richiesto, sono disponibili tutte le feature attese.
    Sono inclusi anche i modelli che richiedono un sottoinsieme dei sensori presenti nei dati.
    """

    # Costruisce un dizionario: {nome_sensore: set(feature disponibili)}
    sensor_features = {}

    # Se `e` contiene dati, costruisce il dizionario da lì
    if data.e:
        for entry in data.e:
            if entry.bn:
                sensor_features.setdefault(entry.bn, set()).update(entry.n)

    # Se `e` è vuoto, tenta il recupero da InfluxDB
    else:
        user_id = data.effective_user_id
        execution_id = data.effective_execution_id

        if not user_id or not execution_id:
            logger.warning("Payload privo di dati e senza user_id / execution_id. Impossibile determinare compatibilità.")
            return {"models": []}

        logger.info("Nessun dato nel payload, recupero sensori/feature da InfluxDB per user_id=%s, exec_id=%s", user_id, execution_id)
        sensor_features = get_sensors_and_features_from_influx(user_id, execution_id)

    matching_models = []  # Elenco dei modelli compatibili

    # Itera su tutti i modelli registrati nel database
    for model in collectionM.find():
        model_sensors = model.get("sensors", [])
        model_features = model.get("features", [])
        input_shape = model.get("input_shape", [1, 1])
        url = model.get("url")

        # Verifica che i metadati del modello siano completi
        if not model_sensors or not model_features or not url:
            logger.warning("Modello incompleto o malformato: %s", model.get("model_name", ""))
            continue

        structured_features = []
        is_compatible = True

        try:
            # CASO 1: modello multi-sensore → model_features è una lista di liste
            if isinstance(model_features[0], list):
                if len(model_sensors) != len(model_features):
                    logger.warning("Incoerenza tra sensori e feature in %s", model.get("model_name", ""))
                    continue

                for sensor, feats in zip(model_sensors, model_features):
                    input_feats = sensor_features.get(sensor, set())
                    if not input_feats or not set(feats).issubset(input_feats):
                        is_compatible = False
                        break
                    structured_features.append((sensor, feats))

            # CASO 2: modello a singolo sensore → model_features è una lista piatta
            else:
                sensor = model_sensors[0]
                feats = model_features
                input_feats = sensor_features.get(sensor, set())
                if not input_feats or not set(feats).issubset(input_feats):
                    is_compatible = False
                else:
                    structured_features.append((sensor, feats))

            if is_compatible:
                matching_models.append({
                    "url": url,
                    "description": model.get("description", ""),
                    "input_shape": input_shape,
                    "features": structured_features,
                    "sensors": model_sensors,
                    "execution_requirements": model.get("execution_requirements", ""),
                    "model_name": model.get("model_name", "")
                })

        except Exception as e:
            logger.warning("Errore compatibilità modello %s: %s", model.get("model_name", ""), str(e))
            continue

    logger.info("Modelli compatibili trovati: %d", len(matching_models))
    return {"models": matching_models}
