#!/bin/bash

echo "[TEST] Avvio run_saveData.sh"
bash /app/tests_locust/run_saveData.sh

echo "[TEST] Avvio run_getModels.sh"
bash /app/tests_locust/run_getModels.sh

echo "[TEST] Avvio run_runModel.sh"
bash /app/tests_locust/run_runModel.sh

echo "[TEST] Avvio run_runModelNoSave.sh"
bash /app/tests_locust/run_runModelNoSave.sh

echo "[COMPLETATO] Tutti i test eseguiti."
