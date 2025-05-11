from queue_pg import get_conn

with get_conn() as conn:
    with conn.cursor() as cur:
        cur.execute("DELETE FROM data_queue")
    conn.commit()
print("Coda svuotata.")