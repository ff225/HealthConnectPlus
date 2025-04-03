from typing import Dict, Any
from models import SenML
from database import collectionM
import logging

logger = logging.getLogger(__name__)

def find_compatible_module(data: SenML) -> Dict[str, Any]:
    """
    Restituisce tutti i modelli compatibili con i dati forniti.
    Un modello è compatibile se tutti i sensori richiesti sono presenti,
    e per ognuno di essi sono disponibili le feature richieste.
    Sono inclusi modelli che utilizzano un sottoinsieme dei sensori presenti.
    """

    # Mappa: sensore → set(feature disponibili) dai dati in ingresso
    sensor_features = {}
    for entry in data.e:
        if entry.bn:
            sensor_features.setdefault(entry.bn, set()).update(entry.n)

    matching_models = []

    for model in collectionM.find():
        model_sensors = model.get("sensors", [])
        model_features = model.get("features", [])
        input_shape = model.get("input_shape", [1, 1])
        url = model.get("url")

        if not model_sensors or not model_features or not url:
            logger.warning("Modello incompleto o malformato: %s", model.get("model_name", ""))
            continue

        structured_features = []
        is_compatible = True

        try:
            # Multi-sensore: lista di liste
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

            # Singolo sensore
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
