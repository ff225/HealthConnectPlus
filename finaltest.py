import pytest
import requests
import time
from datetime import datetime, timedelta
from collections import defaultdict

BASE_URL = "http://localhost:8000"

USER_ID_RUN = "test_user_run"
USER_ID_NOSAVE = "test_user_nosave"

def generate_senml_from_model(model_doc, exec_id, user_id):
    input_shape = model_doc["input_shape"]
    sensors = model_doc["sensors"]
    features = model_doc["features"]

    windows, steps, total_feat = input_shape
    total = windows * steps

    flattened_feats = [feat for sublist in features for feat in sublist]
    if len(flattened_feats) != total_feat:
        raise ValueError("Mismatch feature count")

    records = []
    timestamp = datetime.utcnow()
    debug_input = defaultdict(list)

    for sensor, feats in zip(sensors, features):
        for feat in feats:
            for i in range(total):
                value = round(0.001 * i, 4)
                records.append({
                    "bn": sensor,
                    "n": [feat],
                    "v": value,
                    "t": (timestamp + timedelta(milliseconds=i)).isoformat()
                })
                if len(debug_input[(sensor, feat)]) < 3:
                    debug_input[(sensor, feat)].append(value)

    return {
        "bt": time.time(),
        "user_id": user_id,
        "execution_id": exec_id,
        "e": records
    }, debug_input

def get_models_from_example_senml(example_senml):
    response = requests.post(f"{BASE_URL}/getModels", json=example_senml)
    response.raise_for_status()
    return response.json().get("models", [])

def test_full_pipeline():
    example_senml = {
        "bt": time.time(),
        "user_id": "test_user",
        "execution_id": "test_exec",
        "e": [{
            "bn": "leftwrist",
            "n": ["x_acc"],
            "v": 0.1,
            "t": datetime.utcnow().isoformat()
        }]
    }

    models = get_models_from_example_senml(example_senml)
    assert len(models) > 0, "No compatible models found."

    for model_doc in models:
        model_name = model_doc["model_name"]
        exec_id_run = f"exec_run_{model_name.replace('.tflite', '')}"
        exec_id_nosave = f"exec_nosave_{model_name.replace('.tflite', '')}"

        senml_run, debug_input_run = generate_senml_from_model(model_doc, exec_id_run, USER_ID_RUN)

        resp_run = requests.post(f"{BASE_URL}/saveData", json=senml_run)
        assert resp_run.status_code == 200

        time.sleep(10)

        run_result = requests.post(f"{BASE_URL}/runModel", json=senml_run).json()
        results = run_result.get("results", [])
        assert len(results) > 0, "No results from runModel"

        for r in results:
            _ = r['output']  # Output disponibile qui se serve
            _ = debug_input_run  # Input disponibile qui se serve

        senml_nosave, debug_input_nosave = generate_senml_from_model(model_doc, exec_id_nosave, USER_ID_NOSAVE)

        resp_nosave = requests.post(f"{BASE_URL}/saveData", json=senml_nosave)
        assert resp_nosave.status_code == 200

        time.sleep(5)

        no_save_result = requests.post(f"{BASE_URL}/runModelNoSave", json=senml_nosave).json()
        no_save_results = no_save_result.get("results", [])
        assert len(no_save_results) > 0, "No results from runModelNoSave"

        for r in no_save_results:
            _ = r['result']  # Output disponibile qui se serve
            _ = debug_input_nosave  # Input disponibile qui se serve

        exec_data = requests.get(f"{BASE_URL}/getExecutionData", params={
            "user_id": USER_ID_NOSAVE,
            "execution_id": exec_id_nosave
        }).json()

        inputs, outputs = exec_data.get("inputs", []), exec_data.get("outputs", [])
        assert inputs == [], "Inputs not deleted"
        assert outputs == [], "Outputs not deleted"
