import numpy as np
from database import query_api, INFLUXDB_BUCKET
import logging

logger = logging.getLogger(__name__)

def get_feature_data(sensor: str, fields: list[str], input_shape: list[int], user_id: str, execution_id: str) -> np.ndarray:
    time_steps, windows = input_shape[1], input_shape[0]
    total_per_feature = windows * time_steps
    all_data = []

    for field in fields:
        query = f'''
        from(bucket: "{INFLUXDB_BUCKET}")
          |> range(start: -15m)
          |> filter(fn: (r) => r["_measurement"] == "Sensor_data")
          |> filter(fn: (r) => r["sensor"] == "{sensor}")
          |> filter(fn: (r) => r["_field"] == "{field}")
          |> filter(fn: (r) => r["user_id"] == "{user_id}")
          |> filter(fn: (r) => r["execution_id"] == "{execution_id}")
          |> sort(columns: ["_time"])
          |> limit(n: {total_per_feature})
        '''
        tables = query_api.query(query)
        values = [record.get_value() for table in tables for record in table.records]

        numeric_values = [v for v in values if isinstance(v, (int, float))]

        logger.info(f"[{user_id} - {execution_id}] {sensor}/{field}: {len(numeric_values)} valori (richiesti: {total_per_feature})")

        if len(numeric_values) < total_per_feature:
            raise ValueError(
                f"Dati insufficienti per {sensor}/{field}: {len(numeric_values)} < {total_per_feature}"
            )

        all_data.append(numeric_values[:total_per_feature])

    try:
        data = np.array(all_data, dtype=np.float32).T
        reshaped = data.reshape(input_shape)
        return reshaped

    except Exception as e:
        logger.exception("Errore durante reshape NumPy")
        raise ValueError(f"Errore nella creazione dell'array NumPy per {sensor}: {e}")


def load_and_run_model(model_path: str, input_data: np.ndarray) -> np.ndarray:
    import tensorflow.lite as tflite
    interpreter = tflite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    expected_shape = list(input_details[0]["shape"])
    if expected_shape != list(input_data.shape):
        raise ValueError(f"Forma input non compatibile: atteso {expected_shape}, ricevuto {list(input_data.shape)}")

    interpreter.set_tensor(input_details[0]["index"], input_data.astype(np.float32))
    interpreter.invoke()

    output = interpreter.get_tensor(output_details[0]["index"])

    # LOG COMPLETO PER DEBUG OUTPUT
    logger.info("[MODELLO] Output shape: %s - dtype: %s - valori: %s",
                output.shape, output.dtype, repr(output))

    return output
