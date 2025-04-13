from locust import HttpUser, task, between
import os, json, random
from time import perf_counter
from datetime import datetime

class GetResultsUser(HttpUser):
    wait_time = between(1, 2)
    host = "http://127.0.0.1:8000"

    @task
    def get_results(self):
        sensor_count = int(os.getenv("SENSOR_COUNT", 1))

        try:
            with open("results/results_runModel.jsonl", "r") as f:
                lines = f.readlines()
        except FileNotFoundError:
            print("File results_runModel.jsonl non trovato.")
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
            print(f"Nessun risultato valido per {sensor_count} sensori.")
            return

        selected = random.choice(valid_entries)
        user_id = selected["user_id"]
        execution_id = selected["execution_id"]

        params = {
            "user_id": user_id,
            "execution_id": execution_id,
            "hours": 2
        }

        start = perf_counter()
        response = self.client.get("/getResults", params=params)
        latency = (perf_counter() - start) * 1000

        try:
            data = response.json()
        except:
            data = {}

        result = {
            "timestamp": datetime.utcnow().isoformat(),
            "endpoint": "/getResults",
            "status_code": response.status_code,
            "latency_ms": latency,
            "sensor_count": sensor_count,
            "users": self.environment.runner.user_count if self.environment and self.environment.runner else None,
            "user_id": user_id,
            "execution_id": execution_id,
            "response": data if response.status_code == 200 else response.text
        }

        os.makedirs("results", exist_ok=True)
        with open("results/results_getResults.jsonl", "a") as f:
            f.write(json.dumps(result) + "\n")
