from locust import HttpUser, task, between
import os, json, uuid, random
from time import perf_counter
from datetime import datetime, timedelta

FEATURES = ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"]

# Mappa sensori e modello per ciascun numero di sensori
FIXED_CONFIG = {
    1: {
        "sensors": ["leftwrist"],
        "model": "cnn_leftwrist.tflite"
    },
    2: {
        "sensors": ["rightpocket", "leftwrist"],
        "model": "cnn_rightpocket_leftwrist.tflite"
    },
    3: {
        "sensors": ["rightpocket", "leftwrist", "rightankle"],
        "model": "cnn_rightpocket_leftwrist_rightankle.tflite"
    },
    4: {
        "sensors": ["rightpocket", "leftwrist", "rightankle", "chest"],
        "model": "cnn_rightpocket_leftwrist_rightankle_chest.tflite"
    }
}

class RunModelNoSaveUser(HttpUser):
    host = "http://127.0.0.1:8000"
    wait_time = between(1, 2)

    @task
    def run_model_no_save(self):
        sensor_count = int(os.getenv("SENSOR_COUNT", 1))
        config = FIXED_CONFIG.get(sensor_count)
        if not config:
            print(f"[ERRORE] Nessuna configurazione per {sensor_count} sensori.")
            return

        sensors = config["sensors"]
        model_name = config["model"]

        user_id = "user_" + uuid.uuid4().hex[:8]
        execution_id = "exec_" + uuid.uuid4().hex[:8]
        now = datetime.utcnow().replace(microsecond=0)

        # Genera dati per ogni feature di ogni sensore
        records = []
        for sensor in sensors:
            for feature in FEATURES:
                for i in range(1600):
                    records.append({
                        "bn": sensor,
                        "n": [feature],
                        "v": round(random.uniform(-5, 5), 4),
                        "t": (now + timedelta(milliseconds=i)).isoformat() + "Z",
                        "user_id": user_id,
                        "execution_id": execution_id
                    })

        payload = {
            "bt": now.timestamp(),
            "user_id": user_id,
            "execution_id": execution_id,
            "selection_mode": "named",
            "model_name": model_name,
            "e": records
        }

        start = perf_counter()
        response = self.client.post("/runModelNoSave", json=payload)
        latency_ms = (perf_counter() - start) * 1000

        try:
            response_data = response.json()
        except Exception:
            response_data = {}

        exec_time = 0
        if isinstance(response_data, dict):
            results = response_data.get("results", [])
            if results and isinstance(results, list):
                exec_time = results[0].get("exec_time_ms", 0)

        result = {
            "timestamp": now.isoformat(),
            "endpoint": "/runModelNoSave",
            "status_code": response.status_code,
            "latency_ms": latency_ms,
            "exec_time_ms": exec_time,
            "sensor_count": sensor_count,
            "users": self.environment.runner.user_count if self.environment and self.environment.runner else None,
            "user_id": user_id,
            "execution_id": execution_id,
            "response": response_data if response.status_code == 200 else response.text
        }

        os.makedirs("results", exist_ok=True)
        with open("results/results_runModelNoSave.jsonl", "a") as f:
            f.write(json.dumps(result) + "\n")
