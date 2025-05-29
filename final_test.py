import requests
import time
from datetime import datetime, timedelta
from pymongo import MongoClient
from collections import defaultdict
from typing import List, Dict

BASE_URL = "http://localhost:8000"
MONGO_URI = "mongodb+srv://272519:bSVDnlDZVVEes2hJ@cluster0.0ow6b.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0"
client = MongoClient(MONGO_URI)
collection = client["healthconnect_db"]["model_mappings"]

USER_ID = "demo_user"
EXEC_ID = "demo_exec"
EXEC_ID_NOSAVE = "demo_exec_nosave"

def print_execution_data(data: List[Dict]):
    print(f"\nDati grezzi restituiti ({len(data)} elementi):")
    by_sensor = defaultdict(list)
    for row in data:
        sensor = row.get("sensor", "unknown")
        by_sensor[sensor].append(row["data"])

    for sensor, entries in by_sensor.items():
        print(f"\n→ Sensor: {sensor} | Totale punti: {len(entries)}")
        if entries:
            sample = entries[0]
            for feat, _ in sample.items():
                values = [entry.get(feat) for entry in entries[:5]]
                print(f"   {feat}: {values} ...")

def generate_senml(model_doc, exec_id, user_id):
    input_shape = model_doc["input_shape"]
    sensors = model_doc["sensors"]
    features = model_doc["features"]

    windows, steps, total_feat = input_shape
    total = windows * steps

    flattened_feats = [f for group in features for f in group]
    assert total_feat == len(flattened_feats), "Mismatch feature count"

    records = []
    now = datetime.utcnow()
    debug_input = defaultdict(list)

    for sensor, feats in zip(sensors, features):
        for feat in feats:
            for i in range(total):
                value = round(0.001 * i, 4)
                records.append({
                    "bn": sensor,
                    "n": [feat],
                    "v": value,
                    "t": (now + timedelta(milliseconds=i)).isoformat()
                })
                if len(debug_input[(sensor, feat)]) < 3:
                    debug_input[(sensor, feat)].append(value)

    return {
        "bt": time.time(),
        "user_id": user_id,
        "execution_id": exec_id,
        "e": records
    }, debug_input

def run_test(model_doc):
    model_name = model_doc["model_name"]
    print(f"\n=== MODELLO: {model_name} ===")

    exec_id_run = f"exec_run_{model_name.replace('.tflite', '')}"
    exec_id_nosave = f"exec_nosave_{model_name.replace('.tflite', '')}"

    USER_ID_RUN = "test_user_run"
    USER_ID_NOSAVE = "test_user_nosave"

    user_id = USER_ID_RUN
    execution_id = exec_id_run

    print(f"→ DELETE /deleteAll")
    resp = requests.delete(f"{BASE_URL}/deleteAll", params={
        "user_id": USER_ID_RUN,
        "execution_id": exec_id_run
    })
    print(resp.status_code, resp.json())
    assert resp.status_code == 200

    senml_run, debug_input_run = generate_senml(model_doc, exec_id_run, USER_ID_RUN)
    senml_run["selection_mode"] = "best"

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

    print("\n/getResults (fog_ready=True)")
    resp_fog = requests.get(f"{BASE_URL}/getResults", params={
        "user_id": USER_ID_RUN,
        "execution_id": exec_id_run,
        "fog_ready": "true"
    })
    print(f"/GET /getResults (fog) → status={resp_fog.status_code}")
    try:
        results_fog = resp_fog.json()
        for r in results_fog:
            print(f"\n→ Modello: {r['model_name']} | Sensore: {r['sensor']}")
            print("  Output matrix (prima riga):", r["output_matrix"][0] if r.get("output_matrix") else "vuoto")
    except Exception as e:
        print("  Errore parsing JSON fog-ready:", e)

    print("\n/getExecutionData")
    for attempt in range(15):
        response = requests.get(
            f"{BASE_URL}/getExecutionData",
            params={"user_id": user_id, "execution_id": execution_id},
            timeout=5,
        )
        if response.status_code == 200 and response.json():
            break
        print("  Tentativo... attesa 30 secondi")
        time.sleep(30)

    print(f"/GET /getExecutionData → status={response.status_code}")
    data = response.json()
    print_execution_data(data)
    print(f"\nNumero elementi restituiti da /getExecutionData: {len(data)}")

    input("\nPremi INVIO per completare il test...")

if __name__ == "__main__":
    models = list(collection.find({}))
    for m in models:
        run_test(m)
