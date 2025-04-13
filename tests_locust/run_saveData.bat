@echo off
setlocal enabledelayedexpansion
set LOCUSTFILE=tests_locust/locust_saveData.py
set CHECKSCRIPT=tests_locust/check_saveData_completion.py

for %%S in (1 2 3 4) do (
    for %%U in (1 25 50 100) do (
        for /L %%I in (1,1,10) do (
            echo --------------------------------------------
            echo Test /saveData con %%S sensori e %%U utenti (Iterazione %%I)
            set SENSOR_COUNT=%%S

            locust -f !LOCUSTFILE! --headless -u %%U -r %%U --run-time 2m

            echo Verifica risultati generati...
            python !CHECKSCRIPT!
            if errorlevel 1 (
                echo Test FALLITO o incompleto con %%S sensori e %%U utenti.
                pause
            ) else (
                echo Test completato con successo.
            )

            timeout /t 5 >nul
        )
    )
)

echo --------------------------------------------
echo TUTTI I TEST /saveData COMPLETATI.
pause
