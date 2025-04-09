from typing import Dict, Any
from models import SenML
from database import collectionM
import logging

logger = logging.getLogger(__name__)

def find_compatible_module(data: SenML) -> Dict[str, Any]:
    """
    Restituisce tutti i modelli compatibili con i dati forniti.
    Un modello è considerato compatibile se:
    - Tutti i sensori richiesti dal modello sono presenti nei dati ricevuti.
    - Per ogni sensore richiesto, sono disponibili tutte le feature attese.
    Sono inclusi anche i modelli che richiedono un sottoinsieme dei sensori presenti nei dati.
    """

    # Costruisce un dizionario: {nome_sensore: set(feature disponibili)}
    sensor_features = {}
    for entry in data.e:
        if entry.bn:  # Assicura che il sensore sia definito
            sensor_features.setdefault(entry.bn, set()).update(entry.n)

    matching_models = []  # Elenco dei modelli compatibili

    # Itera su tutti i modelli registrati nel database
    for model in collectionM.find():
        model_sensors = model.get("sensors", [])         # Lista dei sensori richiesti dal modello
        model_features = model.get("features", [])       # Lista delle feature richieste (per sensore)
        input_shape = model.get("input_shape", [1, 1])   # Shape di input del modello (usata per validazioni successive)
        url = model.get("url")                           # URL o path al file .tflite

        # Controllo integrità dei metadati del modello
        if not model_sensors or not model_features or not url:
            logger.warning("Modello incompleto o malformato: %s", model.get("model_name", ""))
            continue

        structured_features = []  # Elenco strutturato: [(sensore, [feature...]), ...]
        is_compatible = True      # Flag di compatibilità inizialmente positivo

        try:
            # CASO 1: modello multi-sensore → model_features è una lista di liste
            if isinstance(model_features[0], list):
                if len(model_sensors) != len(model_features):
                    logger.warning("Incoerenza tra sensori e feature in %s", model.get("model_name", ""))
                    continue

                for sensor, feats in zip(model_sensors, model_features):
                    input_feats = sensor_features.get(sensor, set())
                    if not input_feats or not set(feats).issubset(input_feats):
                        # Se mancano feature richieste per un sensore, il modello non è compatibile
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

            # Se compatibile, aggiunge il modello all’elenco dei candidati
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
            # Log di errore in caso di fallimento nella logica di compatibilità
            logger.warning("Errore compatibilità modello %s: %s", model.get("model_name", ""), str(e))
            continue

    logger.info("Modelli compatibili trovati: %d", len(matching_models))
    return {"models": matching_models}
