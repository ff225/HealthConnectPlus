import logging
import os
import tempfile
import requests
import numpy as np
import time
from fastapi import HTTPException
from typing import Dict

from models import SenML
from crud import find_compatible_module
from processor import get_feature_data, load_and_run_model
from queue_pg import enqueue

logger = logging.getLogger(__name__)

# Numero massimo di tentativi per retry in varie operazioni (es. scaricamento modello)
MAX_RETRIES = int(os.getenv("MAX_RETRIES", 15))
RETRY_DELAY = float(os.getenv("RETRY_DELAY", 3.0))

# ------------------------------------------------------------------------------
# build_input_tensor_from_payload
# ------------------------------------------------------------------------------
# Estrae i dati direttamente dal payload (campo "e") e li converte in un
# array NumPy della shape richiesta dal modello. Se mancano dati sufficienti,
# solleva eccezione e si passerà al recupero da InfluxDB.
# ------------------------------------------------------------------------------
def build_input_tensor_from_payload(data: dict, structured_features: list, input_shape: list) -> np.ndarray:
    windows, time_steps, _ = input_shape
    sensor_data_map = {}

    for record in data.get("e", []):
        sensor = record.get("bn")
        feature = record.get("n") if record.get("n") else None
        value = record.get("v")
        if sensor and feature and isinstance(value, (int, float)):
            sensor_data_map.setdefault(sensor, {}).setdefault(feature, []).append(value)

    combined_data = []
    for sensor, feats in structured_features:
        partial_data = []
        for feature in feats:
            values = sensor_data_map.get(sensor, {}).get(feature, [])
            if len(values) < windows * time_steps:
                raise ValueError(f"Dati insufficienti per {sensor}/{feature}: {len(values)} < {windows * time_steps}")
            partial_data.append(values[:windows * time_steps])
        feature_array = np.stack(partial_data, axis=-1).reshape(windows, time_steps, len(feats))
        combined_data.append(feature_array)

    input_tensor = np.concatenate(combined_data, axis=-1)
    return input_tensor

# ------------------------------------------------------------------------------
# prepare_model_local
# ------------------------------------------------------------------------------
# Scarica il file del modello se remoto, o verifica se già presente localmente.
# Supporta URL tipo "file://" per modelli già presenti.
# ------------------------------------------------------------------------------
def prepare_model_local(model_meta: dict) -> str:
    model_url = model_meta["url"]
    model_name = model_meta["model_name"]

    if model_url.startswith("/") or model_url.startswith("file://"):
        local_path = model_url.replace("file://", "")
        if not os.path.exists(local_path):
            raise FileNotFoundError(f"Modello non trovato: {local_path}")
        logger.info(f"[LOCALE] Uso modello locale: {local_path}")
        return local_path

    # Path temporaneo dove salvare modello scaricato
    local_path = os.path.join(tempfile.gettempdir(), model_name)
    if not os.path.exists(local_path):
        logger.info(f"[REMOTE] Scaricamento modello {model_name} da {model_url}")
        response = requests.get(model_url, timeout=10)
        response.raise_for_status()
        with open(local_path, "wb") as f:
            f.write(response.content)
    return local_path

# ------------------------------------------------------------------------------
# run_model_handler
# ------------------------------------------------------------------------------
# Logica principale per eseguire uno o più modelli compatibili.
# Supporta due modalità:
# - selection_mode = "named": esegue solo un modello specifico
# - selection_mode = "best" o default: esegue tutti quelli compatibili
# In caso `save=True`, salva anche l’output su InfluxDB.
# ------------------------------------------------------------------------------
def run_model_handler(data: Dict, save: bool = True) -> dict:
    try:
        senml_data = SenML(**data)
    except Exception:
        logger.exception("Payload non valido per SenML")
        raise HTTPException(status_code=422, detail="Payload non conforme al formato SenML")

    user_id = senml_data.effective_user_id
    execution_id = senml_data.effective_execution_id
    selection_mode = data.get("selection_mode", "all")
    requested_model = data.get("model_name")

    if not user_id or not execution_id:
        raise HTTPException(status_code=400, detail="user_id ed execution_id sono obbligatori")

    compatible_models = find_compatible_module(senml_data)["models"]
    if not compatible_models:
        raise HTTPException(status_code=404, detail="Nessun modello compatibile trovato")

    if selection_mode == "named":
        if not requested_model:
            raise HTTPException(status_code=400, detail="model_name richiesto in modalità 'named'")
        models_to_run = [m for m in compatible_models if m["model_name"] == requested_model]
        if not models_to_run:
            raise HTTPException(status_code=404, detail=f"Modello '{requested_model}' non compatibile")
    else:
        models_to_run = compatible_models

    results = []

    for model_meta in models_to_run:
        model_name = model_meta["model_name"]
        input_shape = model_meta["input_shape"]
        structured_features = model_meta["features"]

        try:
            local_model_path = prepare_model_local(model_meta)

            try:
                # Tentativo 1: costruzione input direttamente dal payload
                input_tensor = build_input_tensor_from_payload(data, structured_features, input_shape)
                logger.info(f"Input costruito dal payload per modello {model_name}")
            except Exception as payload_error:
                # Tentativo 2: se dati insufficienti, recupera da InfluxDB
                logger.warning(f"Input insufficiente nel payload per {model_name}: {payload_error}. Recupero da InfluxDB...")
                combined_data = []
                for sensor, feats in structured_features:
                    sensor_data = get_feature_data(sensor, feats, input_shape, user_id=user_id, execution_id=execution_id)
                    combined_data.append(sensor_data)
                input_tensor = np.concatenate(combined_data, axis=-1)

            start_exec = time.perf_counter()
            output = load_and_run_model(local_model_path, input_tensor)
            exec_time_ms = (time.perf_counter() - start_exec) * 1000

            # Salvataggio output in InfluxDB (matrice)
            if save:
                from influxdbfun import save_model_output_matrix
                for idx_sensor, (sensor_name, feature_list) in enumerate(structured_features):
                    save_model_output_matrix(
                        user_id=user_id,
                        execution_id=execution_id,
                        model_name=model_name,
                        sensor=sensor_name,
                        features=feature_list,
                        output_matrix=output.tolist()
                    )

            results.append({
                "model_used": model_meta["url"],
                "model_name": model_name,
                "output": output.tolist(),
                "exec_time_ms": exec_time_ms
            })

            logger.info(f"Esecuzione completata per modello {model_name} in {exec_time_ms:.2f}ms.")

        except Exception as e:
            logger.error(f"Errore nell'esecuzione del modello {model_name}: {str(e)}")
            results.append({
                "model_used": model_meta.get("url"),
                "model_name": model_name,
                "error": str(e),
                "exec_time_ms": 0
            })

    return {
        "execution_id": execution_id,
        "results": results
    }

# Alias specifico per l’endpoint /runModelNoSave
def run_model_no_save_handler(data: Dict) -> dict:
    return run_model_handler(data, save=False)
