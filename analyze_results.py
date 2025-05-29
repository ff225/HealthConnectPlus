# analyze_all.py
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os
from glob import glob

# --- Directory risultati ---
results_dir = "results"
output_dir = os.path.join(results_dir, "plots")
os.makedirs(output_dir, exist_ok=True)

# --- Mapping endpoint e metriche ---
latency_fields = {
    "saveData": "response_time",
    "getModels": "response_time",
    "runModel": "response_time",
    "runModelNoSave": "response_time"
}
exec_time_fields = {
    "runModel": "exec_time_ms",
    "runModelNoSave": "exec_time_ms"
}

# --- Rimozione outlier con metodo IQR ---
def remove_outliers_iqr(df: pd.DataFrame, col: str) -> pd.DataFrame:
    Q1 = df[col].quantile(0.25)
    Q3 = df[col].quantile(0.75)
    IQR = Q3 - Q1
    lower = Q1 - 1.5 * IQR
    upper = Q3 + 1.5 * IQR
    return df[(df[col] >= lower) & (df[col] <= upper)]

# --- Raggruppamento per fasce di utenti ---
def group_users(user_value: int) -> int:
    if user_value == 1:
        return 1
    elif 2 <= user_value <= 25:
        return 25
    elif 26 <= user_value <= 50:
        return 50
    elif 51 <= user_value <= 100:
        return 100
    return user_value

# --- Boxplot con medie annotate ---
def plot_box(data, x, y, hue, title, ylabel, filename, log_y=False, ylim=None):
    plt.figure(figsize=(14, 6))
    palette = sns.color_palette("Set2", n_colors=data[hue].nunique())
    ax = sns.boxplot(data=data, x=x, y=y, hue=hue, palette=palette, showfliers=False)

    means = data.groupby([x, hue])[y].mean().reset_index()
    for _, row in means.iterrows():
        xpos = sorted(data[x].unique()).index(row[x])
        hue_vals = sorted(data[hue].unique())
        hpos = hue_vals.index(row[hue])
        offset = (hpos - 1.5) * 0.2
        ax.text(xpos + offset, row[y] + (row[y] * 0.05), f"{int(row[y])}", color="black", ha="center", fontsize=8)

    plt.title(title)
    plt.ylabel(ylabel)
    plt.xlabel("Numero di sensori")
    if ylim:
        plt.ylim(*ylim)
    if log_y:
        plt.yscale("log")
    plt.legend(title="Fasce utenti")
    plt.grid(True, linestyle="--", alpha=0.3)
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, filename))
    plt.close()

# --- Analisi file di risultati ---
for file_path in glob(os.path.join(results_dir, "*_results.jsonl")):
    endpoint = os.path.basename(file_path).replace("_results.jsonl", "")
    df = pd.read_json(file_path, lines=True)

    if "sensors" not in df or "users" not in df:
        continue

    df["users_grouped"] = df["users"].apply(group_users)

    # Analisi latenza
    if endpoint in latency_fields:
        col = latency_fields[endpoint]
        if col in df.columns:
            df[col] = df[col].astype(float)
            df_filtered = remove_outliers_iqr(df, col)
            plot_box(
                data=df_filtered,
                x="sensors",
                y=col,
                hue="users_grouped",
                title=f"Latenza API - {endpoint}",
                ylabel="Tempo risposta (ms)",
                filename=f"{endpoint}_01_response_time.png",
                log_y=True
            )
            df_filtered.to_csv(os.path.join(output_dir, f"{endpoint}_summary.csv"), index=False)

    # Analisi tempo esecuzione modello
    if endpoint in exec_time_fields:
        col = exec_time_fields[endpoint]
        if col in df.columns:
            df[col] = df[col].astype(float)
            df_filtered = remove_outliers_iqr(df, col)
            ymax = df_filtered[col].quantile(0.95) * 1.1
            plot_box(
                data=df_filtered,
                x="sensors",
                y=col,
                hue="users_grouped",
                title=f"Tempo di esecuzione modello - {endpoint}",
                ylabel="Exec time (ms)",
                filename=f"{endpoint}_02_exec_time_ms.png",
                log_y=False,
                ylim=(0, ymax)
            )
            df_filtered.to_csv(os.path.join(output_dir, f"{endpoint}_summary.csv"), index=False)
