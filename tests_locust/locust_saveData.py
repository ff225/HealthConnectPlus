from locust import HttpUser, task, between
import random, time, uuid, json, os
from datetime import datetime, timedelta
from pathlib import Path

# --- MAPPATURA SENSORI IN BASE A QUANTI NE VUOI SIMULARE ---
SENSOR_OPTIONS = {
    1: ["leftwrist"],
    2: ["rightpocket", "leftwrist"],
    3: ["rightpocket", "leftwrist", "rightankle"],
    4: ["rightpocket", "leftwrist", "rightankle", "chest"],
}

FEATURES = ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"]
INPUT_SHAPE = (32, 50)  # 1600 valori per feature (compatibile con i modelli)
RESULTS_FOLDER = Path("results")
RESULTS_FOLDER.mkdir(exist_ok=True)

# Variabili d’ambiente fornite dallo script runner
SENSOR_COUNT = int(os.getenv("SENSOR_COUNT", "1"))
ITERATION = int(os.getenv("ITERATION", "0"))

class SaveDataUser(HttpUser):
    """
    Testa l’endpoint /saveData simulando utenti che inviano
    un pacchetto SenML completo da 1600 valori per feature per sensore.
    I dati vengono accodati nel sistema PostgreSQL per essere poi salvati da worker.
    """

    wait_time = between(0.005, 0.010)
    printed_once = False

    @task
    def save_data(self):
        user_id = str(uuid.uuid4())
        execution_id = str(uuid.uuid4())
        sensors = SENSOR_OPTIONS[SENSOR_COUNT]
        now = datetime.utcnow()

        # --- Costruzione lista 'e' con 1600 record per feature ---
        entries = []
        for sensor in sensors:
            for feature in FEATURES:
                for i in range(INPUT_SHAPE[0] * INPUT_SHAPE[1]):
                    entry = {
                        "bn": sensor,
                        "n": [feature],  # IMPORTANTE: n deve essere una lista
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

        # --- Invio POST a /saveData e misurazione latenza ---
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
            # --- DEBUG (disattivato) ---
            # Stampa una sola volta il payload per debug
#            if not SaveDataUser.printed_once:
#                print(json.dumps(payload, indent=2))
#                SaveDataUser.printed_once = True

            # Scrittura risultato in file .jsonl
            filename = RESULTS_FOLDER / "saveData_results.jsonl"
            with open(filename, "a") as f:
                f.write(json.dumps(result) + "\n")

            if resp.status_code == 200:
                resp.success()
            else:
                resp.failure(f"Errore {resp.status_code}")
