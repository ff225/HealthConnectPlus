import time
import json
import psycopg2
from datetime import datetime
from influxdb_client import InfluxDBClient
from database import INFLUXDB_BUCKET, INFLUXDB_RESULTS_BUCKET, INFLUXDB_ORG

# --- Parametri connessione DB --- #
DB_PARAMS = {
    "dbname": "api_queue",
    "user": "api",
    "password": "test",
    "host": "localhost",
    "port": 5432,
}

# --- Connessione InfluxDB --- #
influx = InfluxDBClient(
    url="http://localhost:8086",
    token="jf-r7Bz78njetwULkCAYJrGfh22yb28sariPO13Jf-uxbAEvaiKkQzWhhS3t2RxwSZT7EAOk91WEPeU4bcZN-A==",
    org=INFLUXDB_ORG
)

query_api = influx.query_api()

def connect_pg():
    return psycopg2.connect(**DB_PARAMS)

def is_saved_in_influx(data: dict) -> bool:
    """
    Verifica se i dati sono già stati salvati in InfluxDB.
    """
    try:
        if "e" in data:
            # payload SenML
            user_id = data.get("user_id") or data["e"][0].get("user_id")
            execution_id = data.get("execution_id") or data["e"][0].get("execution_id")
            if not user_id or not execution_id:
                return False

            query = f'''
            from(bucket: "{INFLUXDB_BUCKET}")
              |> range(start: -7d)
              |> filter(fn: (r) => r["_measurement"] == "sensor_data")
              |> filter(fn: (r) => r["user_id"] == "{user_id}")
              |> filter(fn: (r) => r["execution_id"] == "{execution_id}")
              |> limit(n:1)
            '''
        elif "model_output" in data:
            # payload output modello
            first = data["model_output"][0]
            user_id = first.get("user_id")
            execution_id = first.get("execution_id")
            model_name = first.get("model_name")
            if not user_id or not execution_id or not model_name:
                return False

            query = f'''
            from(bucket: "{INFLUXDB_RESULTS_BUCKET}")
              |> range(start: -7d)
              |> filter(fn: (r) => r["_measurement"] == "model_output")
              |> filter(fn: (r) => r["user_id"] == "{user_id}")
              |> filter(fn: (r) => r["execution_id"] == "{execution_id}")
              |> filter(fn: (r) => r["model_name"] == "{model_name}")
              |> limit(n:1)
            '''
        else:
            return False

        result = query_api.query(query=query)
        return any(result)  # True se ci sono record

    except Exception as e:
        print(f"[InfluxDB ERROR] {e}")
        return False

def monitor_queue():
    print("Avvio monitoraggio coda PostgreSQL e verifica su InfluxDB...")
    conn = connect_pg()
    cur = conn.cursor()

    try:
        while True:
            cur.execute("SELECT COUNT(*) FROM data_queue;")
            total_remaining = cur.fetchone()[0]

            cur.execute("""
                SELECT id, payload
                FROM data_queue
                ORDER BY id ASC
                LIMIT 10;
            """)
            rows = cur.fetchall()

            print("\n[{}] Stato attuale della coda: {} payload totali".format(
                datetime.now().strftime("%H:%M:%S"), total_remaining
            ))

            for row in rows:
                payload_str = row[1]
                try:
                    payload = json.loads(payload_str)
                    if "e" in payload:
                        payload_type = "SenML"
                    elif "model_output" in payload:
                        payload_type = "model_output"
                    else:
                        payload_type = "Unknown"

                    saved = is_saved_in_influx(payload)
                    stato = "salvato" if saved else "NON salvato"

                except Exception:
                    payload_type = "INVALID_JSON"
                    stato = "NON verificabile"

                print(f"ID {row[0]:<5} | Tipo: {payload_type:<13} | Stato Influx: {stato}")

            time.sleep(3)

    except KeyboardInterrupt:
        print("\nInterrotto manualmente.")
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    monitor_queue()
