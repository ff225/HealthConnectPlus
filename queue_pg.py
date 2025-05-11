import os
import sys
import time
import psycopg2
import json

# --- Lettura sicura delle variabili ambiente ---
PG_DB = os.getenv("PG_DB", "api_queue")
PG_USER = os.getenv("PG_USER", "api")
PG_PASSWORD = os.getenv("PG_PASSWORD", "test")
PG_HOST = os.getenv("PG_HOST", "localhost")
PG_PORT = int(os.getenv("PG_PORT", 5432))

if not PG_DB:
    print("ERRORE: la variabile d'ambiente PG_DB non è definita. Interrompo.")
    sys.exit(1)

DB_PARAMS = {
    "host": PG_HOST,
    "port": PG_PORT,
    "dbname": PG_DB,
    "user": PG_USER,
    "password": PG_PASSWORD
}

# --- Connessione con retry ---
def get_conn(retries=10, delay=2):
    for i in range(retries):
        try:
            return psycopg2.connect(**DB_PARAMS)
        except psycopg2.OperationalError as e:
            print(f"[RETRY] Connessione a PostgreSQL fallita ({i+1}/{retries}): {e}")
            time.sleep(delay)
    raise ConnectionError("Impossibile connettersi a PostgreSQL dopo vari tentativi.")

# --- Inserisce un payload nella coda ---
def enqueue(payload_dict):
    try:
        json_payload = json.dumps(payload_dict)
    except Exception as e:
        print(f"ERRORE nella serializzazione JSON: {e}")
        return

    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("INSERT INTO data_queue (payload) VALUES (%s)", (json_payload,))
        conn.commit()

# --- Estrae un batch di payload non ancora processati ---
def dequeue(batch_size=1):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT id, payload FROM data_queue
                WHERE processed = FALSE
                ORDER BY id
                FOR UPDATE SKIP LOCKED
                LIMIT %s
            """, (batch_size,))
            rows = cur.fetchall()
            if not rows:
                return []

            ids = [r[0] for r in rows]
            cur.execute("UPDATE data_queue SET processed = TRUE WHERE id = ANY(%s)", (ids,))
            conn.commit()

            result = []
            for r in rows:
                raw = r[1]
                try:
                    # Se è già dict (es. da JSONB), lo usi direttamente
                    if isinstance(raw, dict):
                        result.append(raw)
                    else:
                        result.append(json.loads(raw))
                except Exception as e:
                    print(f"[ERRORE] Payload malformato in dequeue (ID {r[0]}): {e}")
            return result

# --- Conta quanti payload sono in attesa nella coda ---
def queue_length():
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM data_queue WHERE processed = FALSE")
            return cur.fetchone()[0]
