import pandas as pd
import matplotlib.pyplot as plt
import json
import os

plt.rcParams.update({'font.size': 10})

RESULT_FILES = {
    "saveData": "results/results_saveData.jsonl",
    "runModel": "results/results_runModel.jsonl",
    "runModelNoSave": "results/results_runModelNoSave.jsonl",
    "getModels": "results/results_getModels.jsonl",
    "getResults": "results/results_getResults.jsonl",
    "getExecutionData": "results/results_getExecutionData.jsonl",
}

def load_results(path):
    if not os.path.exists(path):
        return pd.DataFrame()
    with open(path, "r", encoding="utf-8") as f:
        lines = [json.loads(line) for line in f if line.strip()]
    return pd.DataFrame(lines)

def plot_metric(df, value_col, ylabel, title, output_file):
    if df.empty or value_col not in df.columns:
        return

    df = df[df["status_code"] == 200]
    df["users"] = df.get("users", 1).fillna(1).astype(int)
    df["sensor_count"] = df.get("sensor_count", 1).fillna(1).astype(int)

    grouped = df.groupby(["users", "sensor_count"])[value_col].mean().reset_index()
    pivot = grouped.pivot(index="users", columns="sensor_count", values=value_col)
    ax = pivot.plot(marker='o', linewidth=2, figsize=(8, 5))

    for line in ax.get_lines():
        for x, y in zip(line.get_xdata(), line.get_ydata()):
            ax.annotate(f"{y:.0f}", (x, y), textcoords="offset points", xytext=(0, 5), ha='center', fontsize=8)

    plt.title(title)
    plt.xlabel("Numero utenti simultanei")
    plt.ylabel(ylabel)
    plt.xticks([1, 25, 50, 100])
    plt.grid(True, linestyle="--", alpha=0.7)
    plt.legend(title="Sensori", loc="upper left")
    plt.tight_layout()
    plt.savefig(output_file)
    plt.close()
    print(f"✅ Salvato: {output_file}")

if __name__ == "__main__":
    os.makedirs("results", exist_ok=True)

    for endpoint, file_path in RESULT_FILES.items():
        df = load_results(file_path)
        if df.empty:
            print(f"⚠️ Nessun dato per {endpoint}")
            continue

        # Latenza
        plot_metric(
            df,
            "latency_ms",
            "Latenza media (ms)",
            f"Latenza media - {endpoint}",
            f"results/latency_{endpoint}_clear.png"
        )

        # Tempo di esecuzione modello solo per runModel e runModelNoSave
        if endpoint in ["runModel", "runModelNoSave"]:
            plot_metric(
                df,
                "exec_time_ms",
                "Tempo esecuzione modello (ms)",
                f"Esecuzione modello - {endpoint}",
                f"results/exec_time_{endpoint}_clear.png"
            )
