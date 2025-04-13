from locust import HttpUser, task, between
import random, json, os, uuid
from time import perf_counter
from datetime import datetime, timedelta

FEATURES = ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"]
SENSORS_BY_MODEL = {
    1: ["leftwrist"],
    2: ["rightpocket", "leftwrist"],
    3: ["rightpocket", "leftwrist", "rightankle"],
    4: ["rightpocket", "leftwrist", "rightankle", "chest"]
}

class SaveDataUser(HttpUser):
    host = "http://127.0.0.1:8000"
    wait_time = between(1, 2)

    @task
    def save_data(self):
        sensor_count = int(os.getenv("SENSOR_COUNT", 1))
        selected_sensors = SENSORS_BY_MODEL.get(sensor_count, ["leftwrist"])

        user_id = "user_" + uuid.uuid4().hex[:8]
        execution_id = "exec_" + uuid.uuid4().hex[:8]
        records = []
        now = datetime.utcnow().replace(microsecond=0)

        for sensor in selected_sensors:
            for feat in FEATURES:
                for i in range(1600):
                    records.append({
                        "bn": sensor,
                        "n": [feat],
                        "v": round(random.uniform(-5, 5), 4),
                        "t": (now + timedelta(milliseconds=i)).isoformat() + "Z",
                        "user_id": user_id,
                        "execution_id": execution_id
                    })

        payload = {
            "bt": now.timestamp(),
            "user_id": user_id,
            "execution_id": execution_id,
            "e": records
        }

        start = perf_counter()
        response = self.client.post("/saveData", json=payload)
        latency = (perf_counter() - start) * 1000

        result = {
            "timestamp": now.isoformat(),
            "endpoint": "/saveData",
            "status_code": response.status_code,
            "latency_ms": latency,
            "sensor_count": sensor_count,
            "users": self.environment.runner.user_count if self.environment and self.environment.runner else None,
            "user_id": user_id,
            "execution_id": execution_id,
            "used_sensors": selected_sensors
        }

        os.makedirs("results", exist_ok=True)
        with open("results/results_saveData.jsonl", "a") as f:
            f.write(json.dumps(result) + "\n")
