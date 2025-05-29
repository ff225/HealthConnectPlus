# Script per generare payload JSON di esempio per test manuali degli endpoint API.
# Ogni payload rispetta la struttura e la logica attesa dai modelli e dagli endpoint del progetto.

import json
import os
import copy
from datetime import datetime, timezone

# --- Feature standard previste nei modelli (6 per ogni sensore)
FEATURES = ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"]

# --- Mappatura modelli → sensori richiesti
MODELS = {
    1: {"model_name": "cnn_leftwrist.tflite", "sensors": ["leftwrist"]},
    2: {"model_name": "cnn_rightpocket_leftwrist.tflite", "sensors": ["rightpocket", "leftwrist"]},
    3: {"model_name": "cnn_rightpocket_leftwrist_rightankle.tflite", "sensors": ["rightpocket", "leftwrist", "rightankle"]},
    4: {"model_name": "cnn_rightpocket_leftwrist_rightankle_chest.tflite", "sensors": ["rightpocket", "leftwrist", "rightankle", "chest"]},
}

# -------------------------------------------------------------------
# Funzione per generare un singolo record SenML (un punto dati)
# -------------------------------------------------------------------

def generate_senml_record(sensor: str, feature: str, timestamp: float, value: float, user_id: str, execution_id: str):
    return {
        "bn": sensor,
        "n": feature,
        "v": value,
        "t": timestamp,
        "user_id": user_id,
        "execution_id": execution_id
    }

# -------------------------------------------------------------------
# Costruisce un payload completo SenML per un dato numero di sensori
# - num_records: numero di punti per ciascuna feature
# - complete=True → genera l’intera sequenza (es. 1600 valori)
# -------------------------------------------------------------------

def generate_payload(sensor_count: int, num_records: int, complete: bool = False):
    now = datetime.now(timezone.utc).timestamp()
    sensors = MODELS[sensor_count]["sensors"]
    user_id = f"test_user_{sensor_count}"
    execution_id = f"exec_{sensor_count}"
    e = []

    for i in range(num_records):
        for sensor in sensors:
            for feat in FEATURES:
                if complete or i == 0:  # se non completo, genera solo il primo valore (es. per test incompleti)
                    e.append(generate_senml_record(sensor, feat, now + i * 0.01, value=i * 0.1, user_id=user_id, execution_id=execution_id))

    return {
        "bt": now,
        "user_id": user_id,
        "execution_id": execution_id,
        "e": e
    }

# -------------------------------------------------------------------
# Salva il dizionario come file JSON leggibile
# -------------------------------------------------------------------

def write_json_file(obj, name, directory="example_payloads"):
    os.makedirs(directory, exist_ok=True)
    path = os.path.join(directory, name)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)

# -------------------------------------------------------------------
# Genera tutti i payload di esempio utili per i test manuali:
# - saveData
# - runModel
# - runModelNoSave
# - getResults
# - getExecutionData
# - deleteAll
# -------------------------------------------------------------------

def generate_all_payloads():
    os.makedirs("example_payloads", exist_ok=True)

    for sensor_count in range(1, 5):
        # Payload completo per /saveData (32×50 = 1600 valori per feature)
        payload_save = generate_payload(sensor_count, num_records=32 * 50, complete=True)
        write_json_file(payload_save, f"payload_saveData_{sensor_count}.json")

        # Aggiunta selection_mode per /runModel
        payload_run = copy.deepcopy(payload_save)
        payload_run["selection_mode"] = "best"
        write_json_file(payload_run, f"payload_runModel_{sensor_count}.json")

        # Stesso payload anche per /runModelNoSave
        payload_nosave = copy.deepcopy(payload_save)
        payload_nosave["selection_mode"] = "best"
        write_json_file(payload_nosave, f"payload_runModelNoSave_{sensor_count}.json")

    # Payload minimo per /getResults
    write_json_file({
        "user_id": "test_user_1",
        "execution_id": "exec_1",
        "model_name": "cnn_leftwrist.tflite",
        "sensor": "leftwrist"
    }, "payload_getResults.json")

    # Payload per /getExecutionData
    write_json_file({
        "user_id": "test_user_1",
        "execution_id": "exec_1"
    }, "payload_getExecutionData.json")

    # Payload per /deleteAll
    write_json_file({
        "user_id": "test_user_1",
        "execution_id": "exec_1"
    }, "payload_deleteAll.json")

# -------------------------------------------------------------------
# Entry point per generare tutti i file se eseguito direttamente
# -------------------------------------------------------------------

if __name__ == "__main__":
    generate_all_payloads()
