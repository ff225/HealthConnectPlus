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
    models_info = find_compatible_module(data)
    if not models_info["models"]:
        logger.info("Nessun modello compatibile trovato per user_id=%s", data.user_id or "anonymous")
        raise HTTPException(status_code=404, detail="Nessun modello compatibile trovato")

    user_id = data.user_id or "anonymous"
    execution_id = data.execution_id or "exec-" + os.urandom(4).hex()
    results = []

    for model in models_info["models"]:
        try:
            output = execute_model_for_metadata(model, user_id, execution_id)
            if save:
                save_model_output_to_influx(
                    sensor="_".join(model["sensors"]),
                    output=output,
                    user_id=user_id,
                    execution_id=execution_id,
                    model_name=model["model_name"]
                )
            results.append({
                "model_used": model["url"],
                "output": output.tolist()
            })
        except Exception as e:
            logger.error("Errore esecuzione modello %s: %s", model["url"], str(e))
            results.append({
                "model_used": model["url"],
                "error": str(e)
            })

    return {
        "execution_id": execution_id,
        "results": results
    }


def run_model_no_save_handler(data: SenML) -> dict:
    for record in data.e:
        if not record.user_id:
            record.user_id = data.user_id
        if not record.execution_id:
            record.execution_id = data.execution_id

    user_id = data.effective_user_id
    execution_id = data.effective_execution_id

    if not user_id or not execution_id:
        raise HTTPException(status_code=400, detail="user_id e execution_id sono obbligatori")

    compatible_models = find_compatible_module(data)
    if not compatible_models["models"]:
        raise HTTPException(status_code=404, detail="Nessun modello compatibile trovato")

    results = []
    for model_meta in compatible_models["models"]:
        model_name = model_meta["model_name"]
        try:
            result_array = execute_model_for_metadata(model_meta, user_id=user_id, execution_id=execution_id)
            result_serializable = result_array.tolist()

            results.append({
                "model_name": model_name,
                "model_used": model_meta.get("url"),
                "result": result_serializable
            })
            logger.info("Esecuzione modello %s completata", model_name)
        except Exception as e:
            logger.warning("Errore nell'esecuzione di %s: %s", model_name, str(e))
            results.append({
                "model_name": model_name,
                "model_used": model_meta.get("url"),
                "error": str(e)
            })

    # Cancellazione sia degli input che degli output dopo esecuzione
    delete_data_influx(user_id=user_id, execution_id=execution_id)
    logger.info("Dati input e output eliminati dopo runModelNoSave per user_id=%s, execution_id=%s", user_id, execution_id)

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
