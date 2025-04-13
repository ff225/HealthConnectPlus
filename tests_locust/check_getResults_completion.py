import os, sys, json

file_path = "results/results_getResults.jsonl"

if not os.path.exists(file_path):
    print("File dei risultati non trovato.")
    sys.exit(1)

with open(file_path, "r") as f:
    lines = f.readlines()

if not lines:
    print("File dei risultati vuoto.")
    sys.exit(1)

recent_entries = [json.loads(line) for line in lines[-5:] if line.strip()]
valid = any(e.get("status_code") == 200 and isinstance(e.get("response"), dict) and "results" in e["response"] for e in recent_entries)

if valid:
    print("✓ Almeno un risultato valido trovato per /getResults.")
    sys.exit(0)
else:
    print("Nessun risultato valido nelle ultime 5 righe.")
    sys.exit(1)
