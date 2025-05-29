from locust import HttpUser, task, between
import random, time, uuid, json, os
from datetime import datetime, timedelta
from pathlib import Path

# --- CONFIGURAZIONE DEI SENSORI E FEATURE DISPONIBILI -----------------------

SENSOR_OPTIONS = {
    1: ["leftwrist"],
    2: ["rightpocket", "leftwrist"],
    3: ["rightpocket", "leftwrist", "rightankle"],
    4: ["rightpocket", "leftwrist", "rightankle", "chest"],
}

FEATURES = ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"]
INPUT_SHAPE = (32, 50)  # Modello richiede 32 finestre da 50 time step per feature
SELECTION_MODE = "best"  # Esegue un solo modello automaticamente

RESULTS_FOLDER = Path("results")
RESULTS_FOLDER.mkdir(exist_ok=True)

# Parametri definiti tramite variabili d'ambiente (es. da script bash)
SENSOR_COUNT = int(os.getenv("SENSOR_COUNT", "1"))
ITERATION = int(os.getenv("ITERATION", "0"))


class RunModelUser(HttpUser):
    """
    Simula utenti che inviano richieste al'endpoint /runModel:
    - Genera payload realistici compatibili con i modelli salvati
    - Valuta latenza e tempo di esecuzione
    - Salva risultati in results/runModel_results.jsonl
    """

    wait_time = between(0.005, 0.010)

    @task
    def run_model(self):
        # --- Preparazione dati unici per utente ed esecuzione ---
        user_id = str(uuid.uuid4())
        execution_id = str(uuid.uuid4())
        sensors = SENSOR_OPTIONS[SENSOR_COUNT]
        now = datetime.utcnow()

        # --- Generazione dei dati SenML completi ---
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

        # --- Invio richiesta POST a /runModel ---
        start = time.perf_counter()
        with self.client.post("/runModel", json=payload, catch_response=True) as resp:
            duration = (time.perf_counter() - start) * 1000  # ms

            result = {
                "users": self.environment.runner.user_count if self.environment.runner else 0,
                "sensors": SENSOR_COUNT,
                "iteration": ITERATION,
                "response_time": duration,
                "status_code": resp.status_code,
            }

            # --- Estrazione tempo di esecuzione modello (exec_time_ms) ---
            try:
                json_data = resp.json()
                if "results" in json_data:
                    for res in json_data["results"]:
                        result["exec_time_ms"] = res.get("exec_time_ms", 0)
                        break
            except Exception:
                result["exec_time_ms"] = 0  # In caso di errore, default

            # --- Salvataggio risultato su file JSONL ---
            filename = RESULTS_FOLDER / "runModel_results.jsonl"
            with open(filename, "a") as f:
                f.write(json.dumps(result) + "\n")

            resp.success()  # Marca la richiesta come riuscita se non fallita manualmente
