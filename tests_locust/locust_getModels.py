from locust import HttpUser, task, between
import random
import uuid
import time
import json
from datetime import datetime
import os

# --- CONFIGURAZIONE ---------------------------------------------------------

SENSORI_DISPONIBILI = ["leftwrist", "rightpocket", "rightankle", "chest"]
FEATURES = ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"]
RESULTS_DIR = "results"  # Directory dove salvare i risultati dei test
os.makedirs(RESULTS_DIR, exist_ok=True)


class GetModelsUser(HttpUser):
    """
    Utente Locust per testare l’endpoint /getModels.

    - Invia payload SenML validi con dati fittizi per un numero variabile di sensori.
    - Registra tempo di risposta e salva ogni esecuzione in results/getModels_results.jsonl
    - La variabile d’ambiente SENSOR_COUNT determina quanti sensori usare.
    """

    printed_once = False                              # Stampa il primo payload per debug
    wait_time = between(0.005, 0.01)                   # Delay tra utenti (realismo carico)

    def on_start(self):
        """
        Inizializzazione dell'utente: genera ID unici e legge il numero di sensori da usare.
        """
        self.user_id = str(uuid.uuid4())
        self.execution_id = str(uuid.uuid4())
        self.iteration = 0
        self.sensor_count = int(os.environ.get("SENSOR_COUNT", 1))

    @task
    def get_models(self):
        """
        Task eseguito da ogni utente virtuale:
        - Crea un payload completo per /getModels
        - Invia la richiesta POST
        - Registra latenza e codice di risposta
        """
        self.iteration += 1
        selected_sensors = SENSORI_DISPONIBILI[:self.sensor_count]
        bt = time.time()

        # --- Costruzione payload SenML ---
        records = []
        now = datetime.utcnow().isoformat() + "Z"
        for sensor in selected_sensors:
            for feature in FEATURES:
                records.append({
                    "bn": sensor,
                    "n": [feature],
                    "v": random.uniform(-10.0, 10.0),
                    "t": now,
                    "user_id": self.user_id,
                    "execution_id": self.execution_id
                })

        payload = {
            "bt": bt,
            "e": records,
            "user_id": self.user_id,
            "execution_id": self.execution_id
        }

        # --- Invio richiesta e misurazione latenza ---
        start_time = time.time()
        with self.client.post("/getModels", json=payload, catch_response=True) as response:
            elapsed = (time.time() - start_time) * 1000
            output = {
                "users": self.environment.runner.user_count if self.environment.runner else 0,
                "sensors": self.sensor_count,
                "iteration": self.iteration,
                "response_time": round(elapsed, 2),
                "status_code": response.status_code,
            }
            # --- DEBUG (disattivato) ---
            # Solo alla prima iterazione stampo il payload (utile per debug)
#            if not GetModelsUser.printed_once:
#               print(json.dumps(payload, indent=2))
#               GetModelsUser.printed_once = True

            # Salvataggio dei risultati su file
            filename = os.path.join(RESULTS_DIR, "getModels_results.jsonl")
            with open(filename, "a") as f:
                f.write(json.dumps(output) + "\n")

            # Flag di fallimento se non è 200 OK
            if response.status_code != 200:
                response.failure(f"Errore {response.status_code}")
