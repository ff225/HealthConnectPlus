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
SELECTION_MODE = "best"
RESULTS_FOLDER = Path("results")
RESULTS_FOLDER.mkdir(exist_ok=True)

SENSOR_COUNT = int(os.getenv("SENSOR_COUNT", "1"))
ITERATION = int(os.getenv("ITERATION", "0"))

class RunModelUser(HttpUser):
    wait_time = between(0.005, 0.010)

    @task
    def run_model(self):
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
                        "n": [feature],
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
            "selection_mode": SELECTION_MODE,
            "e": entries
        }

        start = time.perf_counter()
        with self.client.post("/runModel", json=payload, catch_response=True) as resp:
            duration = (time.perf_counter() - start) * 1000
            result = {
                "users": self.environment.runner.user_count if self.environment.runner else 0,
                "sensors": SENSOR_COUNT,
                "iteration": ITERATION,
                "response_time": duration,
                "status_code": resp.status_code,
            }
            try:
                json_data = resp.json()
                if "results" in json_data:
                    for res in json_data["results"]:
                        result["exec_time_ms"] = res.get("exec_time_ms", 0)
                        break
            except Exception:
                result["exec_time_ms"] = 0

            filename = RESULTS_FOLDER / f"runModel_results.jsonl"
            with open(filename, "a") as f:
                f.write(json.dumps(result) + "\n")

            resp.success()
