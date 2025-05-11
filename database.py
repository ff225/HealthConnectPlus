import os
from pymongo import MongoClient
from influxdb_client import InfluxDBClient

# --- MongoDB --- #
MONGO_URI = os.getenv("MONGO_URI", "mongodb://localhost:27017")
clientM = MongoClient(MONGO_URI)
dbM = clientM["healthconnect_db"]
collectionM = dbM["model_mappings"]

# --- InfluxDB --- #
INFLUXDB_URL = os.getenv("INFLUXDB_URL")
INFLUXDB_TOKEN = os.getenv("INFLUXDB_TOKEN")
INFLUXDB_ORG = os.getenv("INFLUXDB_ORG")
INFLUXDB_BUCKET = os.getenv("INFLUXDB_BUCKET")
INFLUXDB_RESULTS_BUCKET = os.getenv("INFLUXDB_RESULTS_BUCKET")

_influx_client = InfluxDBClient(
    url=INFLUXDB_URL,
    token=INFLUXDB_TOKEN,
    org=INFLUXDB_ORG,
    timeout=60000,
    enable_gzip=True,
    retries=5,
    max_retry_delay=10000
)

query_api = _influx_client.query_api()
write_api = _influx_client.write_api()
delete_api = _influx_client.delete_api()
influx_client = _influx_client
