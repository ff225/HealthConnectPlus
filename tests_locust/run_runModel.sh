#!/bin/bash
export LOCUSTFILE_PATH="/app/tests_locust/locust_runModel.py"

for SENSOR_COUNT in 1 2 3 4; do
  for USERS in 1 25 50 100; do
    for ITER in $(seq 0 9); do
      echo "Esecuzione runModel | Sensori=$SENSOR_COUNT | Utenti=$USERS | Iterazione=$ITER"
      SENSOR_COUNT=$SENSOR_COUNT ITERATION=$ITER locust -f "$LOCUSTFILE_PATH" --host http://localhost:8000 --headless -u $USERS -r 10 -t 30s
      sleep 2
    done
  done
done
