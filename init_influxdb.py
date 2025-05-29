import os
from influxdb_client import InfluxDBClient

url = os.environ["INFLUXDB_URL"]
token = os.environ["INFLUXDB_TOKEN"]
org = os.environ["INFLUXDB_ORG"]
bucket_name = "model_results"

client = InfluxDBClient(url=url, token=token, org=org)
bucket_api = client.buckets_api()
org_id = client.organizations_api().find_organizations(org=org)[0].id

# Verifica se il bucket esiste già
existing = bucket_api.find_buckets().buckets
bucket_names = [b.name for b in existing]

if bucket_name not in bucket_names:
    bucket_api.create_bucket(bucket_name=bucket_name, org_id=org_id)
    print(f"Bucket '{bucket_name}' creato con successo.")
else:
    print(f"Bucket '{bucket_name}' già esistente, nessuna azione necessaria.")
