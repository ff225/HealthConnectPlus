from locust import HttpUser, task, between
import random, uuid, time, json, os
from time import perf_counter
from datetime import datetime

ALL_SENSORS = {
    "leftwrist": ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"],
    "rightpocket": ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"],
    "rightankle": ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"],
    "chest": ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"]
}

NUM_SENSORS = int(os.getenv("SENSOR_COUNT", "1"))
SAMPLE_PER_FEATURE = 1600

def generate_senml(sensor_names, user_id, execution_id):
    base_time = time.time()
    records = []

    for sensor in sensor_names:
        for i in range(SAMPLE_PER_FEATURE):
            t = base_time + i * 0.02  # Simuliamo 50Hz
            for feat in ALL_SENSORS[sensor]:
                records.append({
                    "bn": sensor,
                    "n": [feat],
                    "v": round(random.uniform(-10, 10), 2),
                    "t": t,
                    "u": "unit",
                    "user_id": user_id,
                    "execution_id": execution_id
                })

    return {"bt": base_time, "e": records}

class SaveAndRunUser(HttpUser):
    wait_time = between(1, 2)

    def log_result(self, result):
        os.makedirs("results", exist_ok=True)
        with open(os.getenv("RESULT_FILE", "results/results.jsonl"), "a") as f:
            f.write(json.dumps(result) + "\n")

    @task
    def save_and_run_model(self):
        sensor_subset = list(ALL_SENSORS.keys())[:NUM_SENSORS]
        user_id = f"user_{random.randint(1000, 9999)}"
        execution_id = str(uuid.uuid4())
        payload = generate_senml(sensor_subset, user_id, execution_id)
        print(f"[DEBUG] NUM_SENSORS attivo: {NUM_SENSORS}")

        # Salvataggio dati
        start_save = perf_counter()
        resp_save = self.client.post("/saveData", json=payload)
        save_duration_ms = (perf_counter() - start_save) * 1000

        if resp_save.status_code != 200:
            self.log_result({
                "timestamp": datetime.utcnow().isoformat(),
                "user_id": user_id,
                "execution_id": execution_id,
                "model": "error_save",
                "num_sensors": NUM_SENSORS,
                "save_duration_ms": save_duration_ms,
                "run_duration_ms": None,
                "status": f"Errore /saveData: {resp_save.status_code}"
            })
            return

        time.sleep(3)

        # Esecuzione modello senza salvataggio
        start_run = perf_counter()
        with self.client.post("/runModelNoSave", json=payload, catch_response=True) as response:
            run_duration_ms = (perf_counter() - start_run) * 1000

            if response.status_code == 200:
                result = response.json()
                if not result.get("results"):
                    response.failure("Nessun modello compatibile trovato")
                    model_used = "no_model_found"
                    status = "no_model"
                else:
                    model_info = result["results"][0]
                    model_used = model_info.get("model_used", "unknown").split("/")[-1]
                    response.success()
                    status = "ok"
            else:
                response.failure(f"Errore API ({response.status_code})")
                model_used = "api_error"
                status = f"api_error_{response.status_code}"

            self.log_result({
                "timestamp": datetime.utcnow().isoformat(),
                "user_id": user_id,
                "execution_id": execution_id,
                "model": model_used,
                "num_sensors": NUM_SENSORS,
                "save_duration_ms": round(save_duration_ms, 2),
                "run_duration_ms": round(run_duration_ms, 2),
                "status": status
            })
