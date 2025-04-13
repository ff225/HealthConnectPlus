import os, sys, json

file_path = "results/results_saveData.jsonl"
if not os.path.exists(file_path):
    print("File dei risultati non trovato.")
    sys.exit(1)

with open(file_path, "r") as f:
    lines = f.readlines()

recent_entries = [json.loads(line) for line in lines[-5:] if line.strip()]
valid = any(e.get("status_code") == 200 for e in recent_entries)

if valid:
    print("Almeno un risultato valido trovato per /saveData.")
    sys.exit(0)
else:
    print("Nessun risultato valido trovato nelle ultime 5 righe.")
    sys.exit(1)
