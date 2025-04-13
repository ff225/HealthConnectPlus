from locust import HttpUser, task, between
import os, json, random
from time import perf_counter
from datetime import datetime

# Mappa sensori → modello fisso compatibile
FIXED_MODEL_BY_SENSOR_COUNT = {
    1: "cnn_leftwrist.tflite",
    2: "cnn_rightpocket_leftwrist.tflite",
    3: "cnn_rightpocket_leftwrist_rightankle.tflite",
    4: "cnn_rightpocket_leftwrist_rightankle_chest.tflite"
}

class RunModelUser(HttpUser):
    wait_time = between(1, 2)
    host = "http://127.0.0.1:8000"

    @task
    def run_model(self):
        sensor_count = int(os.getenv("SENSOR_COUNT", 1))
        model_name = FIXED_MODEL_BY_SENSOR_COUNT.get(sensor_count)
        if not model_name:
            print(f"[ERRORE] Nessun modello fisso definito per {sensor_count} sensori")
            return

        # Legge i dati generati da /saveData
        try:
            with open("results/results_saveData.jsonl", "r") as f:
                lines = f.readlines()
        except FileNotFoundError:
            print("[ERRORE] File results_saveData.jsonl non trovato.")
            return

        # Filtra solo quelli con lo stesso numero di sensori e status 200
        valid_entries = []
        for line in lines:
            try:
                entry = json.loads(line)
                if entry.get("status_code") == 200 and entry.get("sensor_count") == sensor_count:
                    valid_entries.append(entry)
            except json.JSONDecodeError:
                continue

        if not valid_entries:
            print(f"[ERRORE] Nessun salvataggio valido trovato per {sensor_count} sensori.")
            return

        selected = random.choice(valid_entries)
        user_id = selected["user_id"]
        execution_id = selected["execution_id"]

        # Costruisce il payload per /runModel
        payload = {
            "bt": datetime.utcnow().timestamp(),
            "user_id": user_id,
            "execution_id": execution_id,
            "selection_mode": "named",
            "model_name": model_name,
            "e": []  # Non serve passare dati: verranno recuperati da InfluxDB
        }

        start = perf_counter()
        response = self.client.post("/runModel", json=payload)
        latency = (perf_counter() - start) * 1000

        try:
            response_data = response.json()
        except Exception:
            response_data = {}

        exec_time = 0
        if isinstance(response_data, dict):
            results = response_data.get("results", [])
            if results:
                exec_time = results[0].get("exec_time_ms", 0)

        result = {
            "timestamp": datetime.utcnow().isoformat(),
            "endpoint": "/runModel",
            "status_code": response.status_code,
            "latency_ms": latency,
            "exec_time_ms": exec_time,
            "sensor_count": sensor_count,
            "users": self.environment.runner.user_count if self.environment and self.environment.runner else None,
            "user_id": user_id,
            "execution_id": execution_id,
            "response": response_data if response.status_code == 200 else response.text
        }

        os.makedirs("results", exist_ok=True)
        with open("results/results_runModel.jsonl", "a") as f:
            f.write(json.dumps(result) + "\n")
