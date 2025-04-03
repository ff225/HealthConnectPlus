import os
import tensorflow.lite as tflite
from pymongo import MongoClient

# Connessione a MongoDB
MONGO_URI = "mongodb+srv://272519:bSVDnlDZVVEes2hJ@cluster0.0ow6b.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0"
client = MongoClient(MONGO_URI)
db = client["healthconnect_db"]
collection = db["model_mappings"]

# Reset collezione
collection.delete_many({})

# Mappa sensore → feature
SENSOR_FEATURES = {
    "leftwrist": ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"],
    "rightpocket": ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"],
    "rightankle": ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"],
    "chest": ["accX", "accY", "accZ", "gyroX", "gyroY", "gyroZ"]
}

# Estrai input_shape dal file .tflite
def extract_input_shape(model_path):
    interpreter = tflite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    shape = interpreter.get_input_details()[0]["shape"]
    return [int(x) for x in shape]

# Deduce i sensori e le features dai nomi dei file
def infer_metadata(filename):
    name = filename.replace("cnn_", "").replace(".tflite", "")
    sensors = name.split("_")
    features = []
    for s in sensors:
        if s not in SENSOR_FEATURES:
            raise ValueError(f"Sensore sconosciuto nel nome file: {s}")
        features.append(SENSOR_FEATURES[s])
    return sensors, features

# Percorso dei modelli
models_dir = "models"
base_url = "http://localhost:9000/"

for file in os.listdir(models_dir):
    if file.endswith(".tflite"):
        path = os.path.join(models_dir, file)

        try:
            sensors, features = infer_metadata(file)
            input_shape = extract_input_shape(path)
            total_expected_features = sum(len(f) for f in features)

            if input_shape[2] != total_expected_features:
                print(f"Mismatch: {file} → input_shape[2]={input_shape[2]} ma feature generate={total_expected_features}. Skippato.")
                continue

            model_doc = {
                "model_name": file,
                "sensors": sensors,
                "features": features,
                "input_shape": input_shape,
                "url": base_url + file,
                "execution_requirements": "Dati completi per: " + ", ".join(sensors)
            }

            collection.insert_one(model_doc)
            print(f"Inserito: {file} | Shape: {input_shape} | Sensori: {sensors}")

        except Exception as e:
            print(f"Errore su {file}: {e}")

print("\nPopolamento MongoDB completato.")
