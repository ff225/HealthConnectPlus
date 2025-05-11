import time
import logging
import os
from queue_pg import dequeue, queue_length
from influxdbfun import save_data_to_influx, save_model_output_to_influx
from models import SenML

# --- Configurazione ---
BATCH_SIZE = 10
IDLE_SLEEP_SEC = 0.05
IDLE_LOG_INTERVAL = 5
IDLE_FULL_LOG_INTERVAL = 100

# --- Logging ---
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("Worker")

VERBOSE_LOGGING = os.getenv("VERBOSE_LOGGING", "false").lower() == "true"
logger.info("Avvio worker PostgreSQL per salvataggio asincrono (batch=%d)...", BATCH_SIZE)

idle_counter = 0

while True:
    try:
        payloads = dequeue(batch_size=BATCH_SIZE)

        if not payloads:
            idle_counter += 1
            if VERBOSE_LOGGING and idle_counter % IDLE_LOG_INTERVAL == 0:
                logger.info("Coda PostgreSQL: %d payload rimanenti", queue_length())
            if VERBOSE_LOGGING and idle_counter % IDLE_FULL_LOG_INTERVAL == 0:
                logger.info("Worker in attesa... (nessun payload)")
            time.sleep(IDLE_SLEEP_SEC)
            continue

        idle_counter = 0

        for payload in payloads:
            try:
                if "model_output" in payload:
                    save_model_output_to_influx(payload["model_output"])
                    logger.info("Worker ha salvato output modello (%d punti)", len(payload["model_output"]))
                else:
                    senml_data = SenML(**payload)  # validazione
                    written = save_data_to_influx(senml_data)
                    logger.info(
                        "Worker ha salvato %d punti (user_id=%s, exec_id=%s)",
                        written,
                        senml_data.effective_user_id or "anonymous",
                        senml_data.effective_execution_id or "none"
                    )
            except Exception as e:
                logger.exception("Errore durante parsing o salvataggio payload: %s", e)

    except Exception as outer_e:
        logger.exception("Errore nel ciclo principale del worker: %s", outer_e)
        time.sleep(1)
