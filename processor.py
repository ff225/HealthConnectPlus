import numpy as np
from database import query_api, INFLUXDB_BUCKET
import logging

logger = logging.getLogger(__name__)


def get_feature_data(sensor: str, fields: list[str], input_shape: list[int], user_id: str, execution_id: str) -> np.ndarray:
    """
    Recupera i dati dal bucket InfluxDB relativi a uno specifico sensore, feature, utente ed esecuzione.
    I dati vengono aggregati, trasformati in un array NumPy e reshape-ati secondo l'input richiesto dal modello.

    Args:
        sensor (str): Nome del sensore da interrogare (es. "rightpocket").
        fields (list[str]): Lista delle feature richieste (es. ["accx", "accy", "accz"]).
        input_shape (list[int]): Forma richiesta dal modello [window, timestep].
        user_id (str): Identificativo dell’utente.
        execution_id (str): Identificativo dell’esecuzione.

    Returns:
        np.ndarray: Array NumPy compatibile con l'input del modello.
    """
    time_steps, windows = input_shape[1], input_shape[0]
    total_per_feature = windows * time_steps  # Numero totale di valori necessari per ogni feature
    all_data = []  # Lista per accumulare i dati grezzi delle feature

    for field in fields:
        # Costruisce la query Flux per estrarre i dati corrispondenti alla feature
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

        # Esegue la query e recupera tutti i valori numerici disponibili
        tables = query_api.query(query)
        values = [record.get_value() for table in tables for record in table.records]
        numeric_values = [v for v in values if isinstance(v, (int, float))]

        logger.info(f"[{user_id} - {execution_id}] {sensor}/{field}: {len(numeric_values)} valori (richiesti: {total_per_feature})")

        # Verifica che siano presenti abbastanza dati per la feature richiesta
        if len(numeric_values) < total_per_feature:
            raise ValueError(
                f"Dati insufficienti per {sensor}/{field}: {len(numeric_values)} < {total_per_feature}"
            )

        # Troncamento ai valori necessari ed aggiunta alla lista globale
        all_data.append(numeric_values[:total_per_feature])

    try:
        # Trasposizione: ogni colonna corrisponde a una feature
        data = np.array(all_data, dtype=np.float32).T

        # Reshape verso la forma richiesta dal modello
        reshaped = data.reshape(input_shape)
        return reshaped

    except Exception as e:
        logger.exception("Errore durante reshape NumPy")
        raise ValueError(f"Errore nella creazione dell'array NumPy per {sensor}: {e}")


def load_and_run_model(model_path: str, input_data: np.ndarray) -> np.ndarray:
    """
    Carica un modello TFLite da file e lo esegue sui dati di input.

    Args:
        model_path (str): Percorso al file .tflite del modello.
        input_data (np.ndarray): Dati da fornire in input al modello.

    Returns:
        np.ndarray: Output del modello come array NumPy.
    """
    import tensorflow.lite as tflite

    # Caricamento e allocazione del modello TFLite
    interpreter = tflite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()

    # Recupero delle informazioni su input/output
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    expected_shape = list(input_details[0]["shape"])
    if expected_shape != list(input_data.shape):
        raise ValueError(f"Forma input non compatibile: atteso {expected_shape}, ricevuto {list(input_data.shape)}")

    # Impostazione dell’input e invocazione dell'inferenza
    interpreter.set_tensor(input_details[0]["index"], input_data.astype(np.float32))
    interpreter.invoke()

    # Estrazione dell’output dal modello
    output = interpreter.get_tensor(output_details[0]["index"])

    # Logging completo per debugging
    logger.info("[MODELLO] Output shape: %s - dtype: %s - valori: %s",
                output.shape, output.dtype, repr(output))

    return output
