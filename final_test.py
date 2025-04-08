import pytest
import requests
import time
from datetime import datetime, timedelta
from pymongo import MongoClient
from collections import defaultdict

BASE_URL = "http://localhost:8000"
MONGO_URI = "mongodb+srv://272519:bSVDnlDZVVEes2hJ@cluster0.0ow6b.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0"
client = MongoClient(MONGO_URI)
collection = client["healthconnect_db"]["model_mappings"]

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
        raise ValueError(f"Mismatch feature count: atteso {total_feat}, ma trovati {len(flattened_feats)} da features")

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


@pytest.mark.parametrize("model_doc", list(collection.find()))
@pytest.mark.parametrize("selection_mode", ["all", "best", "named"])
def test_full_pipeline(model_doc, selection_mode):
    model_name = model_doc["model_name"]
    print(f"\n===== MODELLO: {model_name} | MODE: {selection_mode} =====")

    exec_id_run = f"exec_run_{selection_mode}_{model_name.replace('.tflite', '')}"
    exec_id_nosave = f"exec_nosave_{selection_mode}_{model_name.replace('.tflite', '')}"

    # Pulizia
    print("Pulizia InfluxDB...")
    assert requests.delete(f"{BASE_URL}/deleteAll").status_code == 200

    # ======= RUNMODEL (con salvataggio) =======
    senml_run, debug_input_run = generate_senml_from_model(model_doc, exec_id_run, USER_ID_RUN)
    senml_run["selection_mode"] = selection_mode
    if selection_mode == "named":
        senml_run["model_name"] = model_doc["model_name"]

    print("\n/saveData")
    resp = requests.post(f"{BASE_URL}/saveData", json=senml_run)
    print(resp.status_code, resp.json())
    assert resp.status_code == 200

    print("\n/getModels")
    models = requests.post(f"{BASE_URL}/getModels", json=senml_run).json().get("models", [])
    print(f"/getModels → {len(models)} modelli compatibili trovati")
    for m in models:
        print(f"  → {m['model_name']} (sensors: {', '.join(m['sensors'])})")

    print("Attesa 10 secondi per scrittura dati...")
    time.sleep(10)

    print("\n/runModel")
    run_result = requests.post(f"{BASE_URL}/runModel", json=senml_run).json()
    print(f"/runModel → {len(run_result.get('results', []))} risultati restituiti")
    for r in run_result["results"]:
        print(f" {r['model_used'].split('/')[-1]}")
        print(f"    Output (troncato): {str(r.get('output', ''))[:100].rstrip()}...")
        print("    Input:")
        for (sensor, feat), values in debug_input_run.items():
            print(f"      {sensor}.{feat}: [{', '.join(map(str, values))}]")

    print("\n/getResults")
    results = requests.get(f"{BASE_URL}/getResults", params={"user_id": USER_ID_RUN, "execution_id": exec_id_run}).json()["results"]
    print(f"\nNumero di modelli eseguiti: {len(results)}")
    for r in results:
        print(f"  → Modello: {r['model_name']}, Sensor: {r['sensor']}")
        print(f"    Output (troncato): {str(r['result'])[:100].rstrip()}...")

    assert len(results) > 0
    input("\nPremi INVIO per proseguire a /runModelNoSave...")

    # ======= RUNMODELNOSAVE (senza salvataggio) =======
    senml_nosave, debug_input_nosave = generate_senml_from_model(model_doc, exec_id_nosave, USER_ID_NOSAVE)
    senml_nosave["selection_mode"] = selection_mode
    if selection_mode == "named":
        senml_nosave["model_name"] = model_doc["model_name"]

    print("\n/runModelNoSave")
    no_save_result = requests.post(f"{BASE_URL}/runModelNoSave", json=senml_nosave).json()
    print(f"/runModelNoSave → {len(no_save_result.get('results', []))} modelli eseguiti senza salvataggio")
    for r in no_save_result["results"]:
        print(f"  {r['model_used'].split('/')[-1]}")
        print(f"    Output (troncato): {str(r.get('result', ''))[:100].rstrip()}...")
        print("    Input:")
        for (sensor, feat), values in debug_input_nosave.items():
            print(f"      {sensor}.{feat}: [{', '.join(map(str, values))}]")

    print("\n/getExecutionData")
    exec_data = requests.get(f"{BASE_URL}/getExecutionData", params={
        "user_id": USER_ID_NOSAVE,
        "execution_id": exec_id_nosave
    }).json()

    print(f"/getExecutionData → input: {len(exec_data['inputs'])} | output: {len(exec_data['outputs'])}")
    print(" Verifica: dati temporanei eliminati dopo /runModelNoSave")
    assert exec_data["inputs"] == []
    assert exec_data["outputs"] == []

    input("\nPremi INVIO per completare il test...")
