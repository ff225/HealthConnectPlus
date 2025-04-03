import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os
import glob
import json

# === Cartella contenente i risultati ===
results_dir = "results"
jsonl_files = glob.glob(os.path.join(results_dir, "results_sensori*_utenti*.jsonl"))

# === Caricamento e parsing ===
all_results = []
for filepath in jsonl_files:
    with open(filepath, "r") as f:
        for line in f:
            try:
                data = json.loads(line)
                data["source_file"] = os.path.basename(filepath)
                all_results.append(data)
            except Exception:
                continue

df = pd.DataFrame(all_results)

# === Estrai sensori e utenti dal nome file ===
df["num_sensors"] = df["source_file"].str.extract(r"results_sensori(\d+)_")[0].astype(int)
df["num_users"] = df["source_file"].str.extract(r"_utenti(\d+)\.jsonl")[0].astype(int)

# === Salva CSV unificato ===
df.to_csv("results/analyze_all_risultati_completi.csv", index=False)

# === Statistiche aggregate ===
agg = df.groupby(["num_sensors", "num_users"]).agg({
    "save_duration_ms": ["mean", "std", "min", "max"],
    "run_duration_ms": ["mean", "std", "min", "max"]
}).round(2)

print("\nStatistiche aggregate per sensori e utenti:")
print(agg)

# === Plot: Durata salvataggio ===
plt.figure(figsize=(10, 6))
sns.barplot(data=df, x="num_sensors", y="save_duration_ms", hue="num_users", errorbar="sd")
plt.title("Durata salvataggio vs Sensori/Utenti")
plt.xlabel("Numero di Sensori")
plt.ylabel("Durata salvataggio (ms)")
plt.legend(title="Utenti")
plt.tight_layout()
plt.savefig("results/analyze_all_save_vs_sensors_users.png")
plt.close()

# === Plot: Durata esecuzione ===
plt.figure(figsize=(10, 6))
sns.barplot(data=df, x="num_sensors", y="run_duration_ms", hue="num_users", errorbar="sd")
plt.title("Durata esecuzione vs Sensori/Utenti")
plt.xlabel("Numero di Sensori")
plt.ylabel("Durata esecuzione (ms)")
plt.legend(title="Utenti")
plt.tight_layout()
plt.savefig("results/analyze_all_run_vs_sensors_users.png")
plt.close()

# === Plot: Boxplot esecuzione ===
plt.figure(figsize=(12, 6))
sns.boxplot(data=df, x="num_sensors", y="run_duration_ms", hue="num_users")
plt.title("Boxplot durata esecuzione")
plt.xlabel("Numero di Sensori")
plt.ylabel("Durata esecuzione (ms)")
plt.legend(title="Utenti")
plt.tight_layout()
plt.savefig("results/analyze_all_boxplot_run_duration.png")
plt.close()
