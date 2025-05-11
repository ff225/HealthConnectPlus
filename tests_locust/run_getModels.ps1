$SENSOR_COUNTS = @(1, 2, 3, 4)
$USERS_LIST = @(1, 25, 50, 100)

# Cancellazione file risultati precedente
if (Test-Path "../results/getModels_results.jsonl") {
    Remove-Item "../results/getModels_results.jsonl"
}

foreach ($s in $SENSOR_COUNTS) {
    foreach ($u in $USERS_LIST) {
        for ($i = 1; $i -le 10; $i++) {
            Write-Host "Esecuzione: $s sensori, $u utenti, iterazione $i"
            $env:SENSOR_COUNT = "$s"
            $env:ITERATION = "$i"
            locust -f tests_locust/locust_getModels.py --host http://127.0.0.1:8000 --headless -u $u -r 5 -t 20s
        }
    }
}

