import logging
import os
import tempfile
import requests
import numpy as np
import time
from fastapi import HTTPException
from models import SenML
from crud import find_compatible_module
from influxdbfun import save_model_output_to_influx, delete_data_influx
from processor import get_feature_data, load_and_run_model

logger = logging.getLogger(__name__)

MAX_RETRIES = int(os.getenv("MAX_RETRIES", 15))
RETRY_DELAY = float(os.getenv("RETRY_DELAY", 3.0))


def run_model_handler(data: SenML, save: bool = True) -> dict:
    user_id = data.effective_user_id
    execution_id = data.effective_execution_id
    selection_mode = data.selection_mode or "all"
    requested_model = data.model_name

    if not user_id or not execution_id:
        raise HTTPException(status_code=400, detail="user_id e execution_id sono obbligatori")

    compatible_models = find_compatible_module(data)["models"]
    if not compatible_models:
        raise HTTPException(status_code=404, detail="Nessun modello compatibile trovato")

    # Selezione modelli in base alla modalità
    if selection_mode == "named":
        if not requested_model:
            raise HTTPException(status_code=400, detail="model_name è richiesto in modalità 'named'")
        selected = [m for m in compatible_models if m["model_name"] == requested_model]
        if not selected:
            raise HTTPException(status_code=404, detail=f"Modello richiesto '{requested_model}' non compatibile")
        models_to_run = selected

    elif selection_mode == "best":
        sorted_models = sorted(compatible_models, key=lambda m: m.get("priority", 999))
        models_to_run = [sorted_models[0]]

    else:  # all
        models_to_run = compatible_models

    results = []

    for model in models_to_run:
        model_name = model["model_name"]
        try:
            output = execute_model_for_metadata(model, user_id, execution_id)
            if save:
                save_model_output_to_influx(
                    sensor="_".join(model["sensors"]),
                    output=output,
                    user_id=user_id,
                    execution_id=execution_id,
                    model_name=model_name
                )
            results.append({
                "model_used": model["url"],
                "model_name": model_name,
                "output": output.tolist()
            })
            logger.info("Esecuzione e salvataggio completati per %s", model_name)
        except Exception as e:
            logger.error("Errore esecuzione modello %s: %s", model["url"], str(e))
            results.append({
                "model_used": model["url"],
                "model_name": model_name,
                "error": str(e)
            })

    return {
        "execution_id": execution_id,
        "results": results
    }


def run_model_no_save_handler(data: SenML) -> dict:

    user_id = data.effective_user_id
    execution_id = data.effective_execution_id
    selection_mode = data.selection_mode or "all"
    requested_model = data.model_name

    if not user_id or not execution_id:
        raise HTTPException(status_code=400, detail="user_id e execution_id sono obbligatori")

    for record in data.e:
        if not record.user_id:
            record.user_id = user_id
        if not record.execution_id:
            record.execution_id = execution_id

    compatible_models = find_compatible_module(data)["models"]
    if not compatible_models:
        raise HTTPException(status_code=404, detail="Nessun modello compatibile trovato")

    # Selezione in base alla modalità
    if selection_mode == "named":
        if not requested_model:
            raise HTTPException(status_code=400, detail="model_name è richiesto in modalità 'named'")
        selected = [m for m in compatible_models if m["model_name"] == requested_model]
        if not selected:
            raise HTTPException(status_code=404, detail=f"Modello richiesto '{requested_model}' non compatibile")
        models_to_run = selected

    elif selection_mode == "best":
        sorted_models = sorted(compatible_models, key=lambda m: m.get("priority", 999))
        models_to_run = [sorted_models[0]]

    else:  # all
        models_to_run = compatible_models

    # Riorganizza dati per modello
    sensor_data_map = {}
    for record in data.e:
        sensor = record.bn
        feature = record.n[0]
        value = record.v
        if sensor and feature and isinstance(value, (int, float)):
            sensor_data_map.setdefault(sensor, {}).setdefault(feature, []).append(value)

    results = []
    for model_meta in models_to_run:
        model_name = model_meta["model_name"]
        input_shape = model_meta["input_shape"]
        structured_features = model_meta["features"]

        try:
            local_path = os.path.join(tempfile.gettempdir(), model_name)
            if not os.path.exists(local_path):
                response = requests.get(model_meta["url"], timeout=10)
                response.raise_for_status()
                with open(local_path, "wb") as f:
                    f.write(response.content)

            combined_data = []
            for (sensor, feats) in structured_features:
                partial_shape = [input_shape[0], input_shape[1], len(feats)]
                total_vals = partial_shape[0] * partial_shape[1]

                values = []
                for feature in feats:
                    v = sensor_data_map.get(sensor, {}).get(feature, [])
                    if len(v) < total_vals:
                        raise ValueError(f"Dati insufficienti per {sensor}/{feature}: {len(v)} < {total_vals}")
                    values.append(v[:total_vals])

                values_np = np.array(values, dtype=np.float32).T
                reshaped = values_np.reshape(partial_shape)
                combined_data.append(reshaped)

            input_tensor = np.concatenate(combined_data, axis=-1)
            output = load_and_run_model(local_path, input_tensor)

            results.append({
                "model_name": model_name,
                "model_used": model_meta["url"],
                "result": output.tolist()
            })
            logger.info("Esecuzione modello %s completata", model_name)

        except Exception as e:
            logger.warning("Errore nell'esecuzione di %s: %s", model_name, str(e))
            results.append({
                "model_name": model_name,
                "model_used": model_meta.get("url"),
                "error": str(e)
            })

    return {"results": results}


def execute_model_for_metadata(model_meta: dict, user_id: str, execution_id: str) -> np.ndarray:
    model_url = model_meta["url"]
    model_name = model_meta["model_name"]
    input_shape = model_meta["input_shape"]
    structured_features = model_meta["features"]

    logger.info("Esecuzione modello %s per exec_id=%s", model_name, execution_id)

    local_path = os.path.join(tempfile.gettempdir(), model_name)
    if not os.path.exists(local_path):
        response = requests.get(model_url, timeout=10)
        response.raise_for_status()
        with open(local_path, "wb") as f:
            f.write(response.content)

    combined_data = []
    for (sensor, feats) in structured_features:
        partial_shape = [input_shape[0], input_shape[1], len(feats)]
        for attempt in range(MAX_RETRIES):
            try:
                sensor_data = get_feature_data(sensor, feats, partial_shape, user_id=user_id, execution_id=execution_id)
                combined_data.append(sensor_data)
                break
            except ValueError as e:
                if "Dati insufficienti" in str(e):
                    logger.warning("Tentativo %d/%d: dati non pronti per %s, attendo %.1fs...",
                                   attempt + 1, MAX_RETRIES, sensor, RETRY_DELAY)
                    time.sleep(RETRY_DELAY)
                else:
                    raise
        else:
            raise ValueError(f"Dati non pronti per il sensore {sensor} dopo {MAX_RETRIES} tentativi.")

    input_data = np.concatenate(combined_data, axis=-1)
    output = load_and_run_model(local_path, input_data)
    return output
