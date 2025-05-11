$SENSOR_COUNTS = 1..4
$USERS = @(1, 25, 50, 100)

# Cancellazione file risultati precedente
if (Test-Path "../results/saveData_results.jsonl") {
    Remove-Item "../results/saveData_results.jsonl"
}

foreach ($sensors in $SENSOR_COUNTS) {
    foreach ($userCount in $USERS) {
        for ($i = 0; $i -lt 10; $i++) {
            Write-Host "Esecuzione: $sensors sensori, $userCount utenti, iterazione $i"
            $env:SENSOR_COUNT = $sensors
            $env:ITERATION = $i
            locust -f .\tests_locust\locust_saveData.py --headless -u $userCount -r $userCount --run-time 20s --host http://localhost:8000
        }
    }
}
