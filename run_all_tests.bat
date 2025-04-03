@echo off
setlocal enabledelayedexpansion

REM === CONFIGURAZIONE DEL TEST ===
set "SENSOR_COUNT=1"
set "USER_COUNT=1"
set "ITERATIONS=10"

REM === FILE DI OUTPUT DEI RISULTATI ===
set "RESULT_FILE=results\results_sensori!SENSOR_COUNT!_utenti!USER_COUNT!.jsonl"

echo.
echo ===========================================
echo     TEST PERFORMANCE API
echo     Sensori: !SENSOR_COUNT!
echo     Utenti: !USER_COUNT!
echo     Iterazioni: !ITERATIONS!
echo     File risultati: !RESULT_FILE!
echo ===========================================
echo.

if not exist results mkdir results
if exist !RESULT_FILE! del !RESULT_FILE!

REM === Imposta variabili ambiente ===
set SENSOR_COUNT=!SENSOR_COUNT!
set RESULT_FILE=!RESULT_FILE!

for /L %%I in (1,1,!ITERATIONS!) do (
    echo  Iterazione %%I di !ITERATIONS!

    call locust -f locustfile_save_then_run.py ^
        --headless ^
        -u !USER_COUNT! ^
        -r !USER_COUNT! ^
        --run-time 60s ^
        --stop-timeout 5 ^
        --host http://127.0.0.1:8000

    echo.
)

echo Test completato. Risultati salvati in: !RESULT_FILE!
pause
