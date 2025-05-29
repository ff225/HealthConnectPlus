import os
import sys
import time
import psycopg2
import json

# ==============================================================================
# CONFIGURAZIONE CONNESSIONE POSTGRESQL
# ==============================================================================

# Variabili ambiente per configurare la connessione al database PostgreSQL
PG_DB = os.getenv("PG_DB", "api_queue")
PG_USER = os.getenv("PG_USER", "api")
PG_PASSWORD = os.getenv("PG_PASSWORD", "test")
PG_HOST = os.getenv("PG_HOST", "localhost")
PG_PORT = int(os.getenv("PG_PORT", 5432))

# Controllo critico: PG_DB deve essere sempre presente
if not PG_DB:
    print("ERRORE: la variabile d'ambiente PG_DB non è definita. Interrompo.")
    sys.exit(1)

# Parametri di connessione usati da tutte le funzioni
DB_PARAMS = {
    "host": PG_HOST,
    "port": PG_PORT,
    "dbname": PG_DB,
    "user": PG_USER,
    "password": PG_PASSWORD
}

# ------------------------------------------------------------------------------
# get_conn
# ------------------------------------------------------------------------------
# Ritorna una connessione attiva a PostgreSQL, con retry in caso di errore.
# Usata internamente da tutte le funzioni di enqueue/dequeue.
# ------------------------------------------------------------------------------
def get_conn(retries=10, delay=2):
    for i in range(retries):
        try:
            return psycopg2.connect(**DB_PARAMS)
        except psycopg2.OperationalError as e:
            print(f"[RETRY] Connessione a PostgreSQL fallita ({i+1}/{retries}): {e}")
            time.sleep(delay)
    raise ConnectionError("Impossibile connettersi a PostgreSQL dopo vari tentativi.")

# ------------------------------------------------------------------------------
# enqueue
# ------------------------------------------------------------------------------
# Inserisce un nuovo payload JSON nella tabella `data_queue`, marcandolo
# automaticamente come `processed = FALSE`. La serializzazione è protetta.
# ------------------------------------------------------------------------------
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

# ------------------------------------------------------------------------------
# dequeue
# ------------------------------------------------------------------------------
# Estrae un batch di payload dalla tabella `data_queue`, marcandoli subito come
# `processed = TRUE` per evitare doppio uso. Usa FOR UPDATE SKIP LOCKED per
# supportare accesso concorrente da più worker.
# ------------------------------------------------------------------------------
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
                    # payload può essere stringa JSON o già dict
                    if isinstance(raw, dict):
                        result.append(raw)
                    else:
                        result.append(json.loads(raw))
                except Exception as e:
                    print(f"[ERRORE] Payload malformato in dequeue (ID {r[0]}): {e}")
            return result

# ------------------------------------------------------------------------------
# queue_length
# ------------------------------------------------------------------------------
# Conta quanti payload sono ancora in attesa (processed = FALSE)
# Utile per monitoraggio o metriche di coda.
# ------------------------------------------------------------------------------
def queue_length():
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM data_queue WHERE processed = FALSE")
            return cur.fetchone()[0]
