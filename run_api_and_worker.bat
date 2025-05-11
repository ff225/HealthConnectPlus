@echo off
title API (4 Workers Uvicorn) + 6 Workers PGSQL

REM Uvicorn con 4 processi per gestire più utenti simultanei
start "FastAPI Uvicorn" cmd /k "uvicorn main:app --host 127.0.0.1 --port 8000 --workers 8 > uvicorn_log.log 2>&1"
timeout /t 2 >nul

REM Avvia i worker asincroni PostgreSQL per svuotare la coda
start "Worker 1" cmd /k "python save_worker_pg.py > worker1.log 2>&1"
start "Worker 2" cmd /k "python save_worker_pg.py > worker2.log 2>&1"
start "Worker 3" cmd /k "python save_worker_pg.py > worker3.log 2>&1"
start "Worker 4" cmd /k "python save_worker_pg.py > worker4.log 2>&1"
start "Worker 5" cmd /k "python save_worker_pg.py > worker5.log 2>&1"
start "Worker 6" cmd /k "python save_worker_pg.py > worker6.log 2>&1"
