from influxdb_client import InfluxDBClient
from pymongo import MongoClient

INFLUXDB_TOKEN = "jf-r7Bz78njetwULkCAYJrGfh22yb28sariPO13Jf-uxbAEvaiKkQzWhhS3t2RxwSZT7EAOk91WEPeU4bcZN-A=="
INFLUXDB_URL = "http://localhost:8086"
INFLUXDB_ORG = "Unimore"
INFLUXDB_BUCKET = "healthconnect"
INFLUXDB_RESULTS_BUCKET = "model_results"

clientI = InfluxDBClient(url=INFLUXDB_URL, token=INFLUXDB_TOKEN, org=INFLUXDB_ORG)
write_api = clientI.write_api(synchronous=True)
query_api = clientI.query_api()

MONGO_URI = "mongodb+srv://272519:bSVDnlDZVVEes2hJ@cluster0.0ow6b.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0"
clientM = MongoClient(MONGO_URI)
dbM = clientM["healthconnect_db"]
collectionM = dbM["model_mappings"]
