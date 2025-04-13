from locust import HttpUser, task, between
import os, json, random
from time import perf_counter
from datetime import datetime

class GetModelsUser(HttpUser):
    wait_time = between(1, 2)
    host = "http://127.0.0.1:8000"

    @task
    def get_models(self):
        sensor_count = int(os.getenv("SENSOR_COUNT", 1))

        # Carica i risultati da /saveData
        try:
            with open("results/results_saveData.jsonl", "r") as f:
                lines = f.readlines()
        except FileNotFoundError:
            print("File results_saveData.jsonl non trovato.")
            return

        valid_entries = []
        for line in lines:
            try:
                entry = json.loads(line)
                if entry.get("status_code") == 200 and entry.get("sensor_count") == sensor_count:
                    valid_entries.append(entry)
            except json.JSONDecodeError:
                continue

        if not valid_entries:
            print(f"Nessun dato valido trovato per {sensor_count} sensori.")
            return

        selected = random.choice(valid_entries)
        user_id = selected["user_id"]
        execution_id = selected["execution_id"]

        payload = {
            "bt": datetime.utcnow().timestamp(),
            "user_id": user_id,
            "execution_id": execution_id,
            "e": []  # Vuoto → verrà fatto lookup da InfluxDB
        }

        start = perf_counter()
        response = self.client.post("/getModels", json=payload)
        latency = (perf_counter() - start) * 1000

        try:
            data = response.json()
        except:
            data = {}

        result = {
            "timestamp": datetime.utcnow().isoformat(),
            "endpoint": "/getModels",
            "status_code": response.status_code,
            "latency_ms": latency,
            "sensor_count": sensor_count,
            "users": self.environment.runner.user_count if self.environment and self.environment.runner else None,
            "user_id": user_id,
            "execution_id": execution_id,
            "response": data if response.status_code == 200 else response.text
        }

        os.makedirs("results", exist_ok=True)
        with open("results/results_getModels.jsonl", "a") as f:
            f.write(json.dumps(result) + "\n")
