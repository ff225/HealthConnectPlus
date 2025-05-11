$SENSOR_COUNTS = @(1, 2, 3, 4)
$USERS_LIST = @(1, 25, 50, 100)

# Cancellazione file risultati precedente
if (Test-Path "../results/runModel_results.jsonl") {
    Remove-Item "../results/runModel_results.jsonl"
}

foreach ($s in $SENSOR_COUNTS) {
    foreach ($u in $USERS_LIST) {
        for ($i = 0; $i -lt 10; $i++) {
            Write-Host "Test runModel | Sensori: $s | Utenti: $u | Iterazione: $i"
            $env:SENSOR_COUNT = "$s"
            $env:ITERATION = "$i"
            locust -f tests_locust/locust_runModel.py --headless -u $u -r $u -t 20s --host http://localhost:8000
        }
    }
}
