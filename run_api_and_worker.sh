#!/bin/bash

# Avvia FastAPI con 8 worker tramite Uvicorn
echo "Avvio API FastAPI con Uvicorn (8 worker)..."
nohup uvicorn main:app --host 127.0.0.1 --port 8000 --workers 8 > uvicorn_log.log 2>&1 &

sleep 2

# Avvia 6 worker per il salvataggio asincrono dei dati
echo "Avvio 6 worker PostgreSQL..."

for i in {1..6}; do
    nohup python save_worker_pg.py > "worker${i}.log" 2>&1 &
done

echo "Avvio completato. API su http://127.0.0.1:8000/docs"
