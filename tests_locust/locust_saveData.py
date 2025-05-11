from locust import HttpUser, task, between
import random, time, uuid, json, os
from datetime import datetime, timedelta
from pathlib import Path

SENSOR_OPTIONS = {
    1: ["leftwrist"],
    2: ["rightpocket", "leftwrist"],
    3: ["rightpocket", "leftwrist", "rightankle"],
    4: ["rightpocket", "leftwrist", "rightankle", "chest"],
}

FEATURES = ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"]
INPUT_SHAPE = (32, 50)  # 1600 valori
RESULTS_FOLDER = Path("results")
RESULTS_FOLDER.mkdir(exist_ok=True)

SENSOR_COUNT = int(os.getenv("SENSOR_COUNT", "1"))
ITERATION = int(os.getenv("ITERATION", "0"))

class SaveDataUser(HttpUser):
    wait_time = between(0.005, 0.010)

    @task
    def save_data(self):
        user_id = str(uuid.uuid4())
        execution_id = str(uuid.uuid4())
        sensors = SENSOR_OPTIONS[SENSOR_COUNT]
        now = datetime.utcnow()

        entries = []
        for sensor in sensors:
            for feature in FEATURES:
                for i in range(INPUT_SHAPE[0] * INPUT_SHAPE[1]):
                    entry = {
                        "bn": sensor,
                        "n": feature,
                        "v": round(random.uniform(-10, 10), 3),
                        "t": (now + timedelta(milliseconds=i)).isoformat() + "Z",
                        "user_id": user_id,
                        "execution_id": execution_id
                    }
                    entries.append(entry)

        payload = {
            "bt": now.timestamp(),
            "user_id": user_id,
            "execution_id": execution_id,
            "e": entries
        }

        start = time.perf_counter()
        with self.client.post("/saveData", json=payload, catch_response=True) as resp:
            duration = (time.perf_counter() - start) * 1000
            result = {
                "users": self.environment.runner.user_count if self.environment.runner else 0,
                "sensors": SENSOR_COUNT,
                "iteration": ITERATION,
                "response_time": duration,
                "status_code": resp.status_code,
            }

            filename = RESULTS_FOLDER / "saveData_results.jsonl"
            with open(filename, "a") as f:
                f.write(json.dumps(result) + "\n")

            if resp.status_code == 200:
                resp.success()
            else:
                resp.failure(f"Errore {resp.status_code}")
