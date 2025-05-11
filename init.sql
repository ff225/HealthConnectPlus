DO
$$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'api') THEN
      CREATE ROLE api LOGIN PASSWORD 'test';
   END IF;
END
$$;

DO
$$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_database WHERE datname = 'api_queue') THEN
      CREATE DATABASE api_queue OWNER api;
   END IF;
END
$$;

\connect api_queue

CREATE TABLE IF NOT EXISTS data_queue (
    id SERIAL PRIMARY KEY,
    payload JSONB NOT NULL,
    processed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

GRANT ALL PRIVILEGES ON TABLE data_queue TO api;
