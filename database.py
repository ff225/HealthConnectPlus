import os
from pymongo import MongoClient
from influxdb_client import InfluxDBClient

# === CONNESSIONE A MONGODB ===
# MongoDB è utilizzato per memorizzare le informazioni sui modelli TFLite,
# in particolare i metadati relativi alla compatibilità (sensori, features, input_shape).

MONGO_URI = os.getenv("MONGO_URI", "mongodb://localhost:27017")  # URI del cluster MongoDB (può essere Atlas o locale)
clientM = MongoClient(MONGO_URI)                                # Istanza client MongoDB
dbM = clientM["healthconnect_db"]                               # Database principale del progetto
collectionM = dbM["model_mappings"]                             # Collezione contenente i modelli salvati


# === CONNESSIONE A INFLUXDB ===
# InfluxDB è utilizzato per gestire:
# - i dati sensoriali (measurement = "sensor_data")
# - i risultati dei modelli (measurement = "model_output")
# È configurato per usare compressione GZIP e retry automatici.

INFLUXDB_URL = os.getenv("INFLUXDB_URL")                        # URL del server InfluxDB (es. http://localhost:8086)
INFLUXDB_TOKEN = os.getenv("INFLUXDB_TOKEN")                    # Token per autenticazione
INFLUXDB_ORG = os.getenv("INFLUXDB_ORG")                        # Organizzazione Influx (es. "Unimore")
INFLUXDB_BUCKET = os.getenv("INFLUXDB_BUCKET")                  # Bucket per i dati sensoriali
INFLUXDB_RESULTS_BUCKET = os.getenv("INFLUXDB_RESULTS_BUCKET")  # Bucket per i risultati modello

# Creazione del client InfluxDB con parametri robusti per ambienti di carico elevato
_influx_client = InfluxDBClient(
    url=INFLUXDB_URL,
    token=INFLUXDB_TOKEN,
    org=INFLUXDB_ORG,
    timeout=60000,              # Timeout esteso per evitare errori durante grandi scritture
    enable_gzip=True,           # Compressione GZIP per ridurre dimensioni del payload
    retries=5,                  # Retry automatici
    max_retry_delay=10000       # Ritardo massimo tra retry in ms
)

# Espone le principali API di InfluxDB
query_api = _influx_client.query_api()      # Per query di lettura
write_api = _influx_client.write_api()      # Per scrittura diretta (usata solo in casi particolari)
delete_api = _influx_client.delete_api()    # Per cancellazione dati
influx_client = _influx_client              # Espone anche il client intero per accessi avanzati
