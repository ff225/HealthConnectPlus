-- ------------------------------------------------------------------------------
-- Creazione dell'utente "api" se non esiste già
-- ------------------------------------------------------------------------------

DO
$$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'api') THEN
      CREATE ROLE api LOGIN PASSWORD 'test';  -- Utente base per accesso a PostgreSQL
   END IF;
END
$$;

-- ------------------------------------------------------------------------------
-- Creazione del database "api_queue" se non esiste già
-- ------------------------------------------------------------------------------

DO
$$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_database WHERE datname = 'api_queue') THEN
      CREATE DATABASE api_queue OWNER api;  -- Database usato per la coda asincrona
   END IF;
END
$$;

-- ------------------------------------------------------------------------------
-- Collegamento al database appena creato
-- ------------------------------------------------------------------------------

\connect api_queue

-- ------------------------------------------------------------------------------
-- Creazione tabella "data_queue" per gestire la coda di payload in arrivo
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS data_queue (
    id SERIAL PRIMARY KEY,               -- ID progressivo autoincrementale
    payload JSONB NOT NULL,              -- Payload in formato JSON salvato in coda
    processed BOOLEAN DEFAULT FALSE,     -- Stato di elaborazione del record
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- Timestamp di inserimento
);

-- ------------------------------------------------------------------------------
-- Permessi completi all'utente "api" sulla tabella data_queue
-- ------------------------------------------------------------------------------

GRANT ALL PRIVILEGES ON TABLE data_queue TO api;
