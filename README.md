# **README della API di supporto per HealthConnect+**

Questa API, sviluppata in FastAPI, costituisce il backend per il
progetto **HealthConnect+**, un sistema per la raccolta, gestione e
analisi di dati sensoriali tramite modelli TFLite. Il progetto utilizza
MongoDB per la gestione dei modelli, InfluxDB per la persistenza dei
dati sensoriali e PostgreSQL per la gestione asincrona tramite coda.

## **File principali del progetto**

| **File**          | **Descrizione**                                                                |
|-------------------|--------------------------------------------------------------------------------|
| main.py           | Definisce tutti gli endpoint HTTP della API.                                   |
| database.py       | Contiene la configurazione delle connessioni a MongoDB, InfluxDB e PostgreSQL. |
| models.py         | Definisce la struttura dei dati utilizzati nella API (SenML e validazioni).    |
| crud.py           | Funzioni per la ricerca e la compatibilità dei modelli salvati in MongoDB.     |
| influxdbfun.py    | Gestione completa di lettura/scrittura da InfluxDB.                            |
| processor.py      | Supporto all'esecuzione modelli: pre-processing dati e gestione TFLite.        |
| run_model.py      | Logica di esecuzione dei modelli TFLite, con o senza salvataggio.              |
| queue_pg.py       | Gestione della coda PostgreSQL per operazioni asincrone.                       |
| save_worker_pg.py | Worker che estrae payload dalla coda e li salva in InfluxDB.                   |

## **File di configurazione e ambiente**

## 

| **File**           | **Descrizione**                                                       |
|--------------------|-----------------------------------------------------------------------|
| Docker-compose.yml | Avvia l'intero sistema: API, InfluxDB, PostgreSQL e worker            |
| Dockerfile         | Definisce l'immagine docker per FastAPI e i worker                    |
| init.sql           | Script di inizializzazione per PostgreSQL (coda dati)                 |
| Init_influxdb.py   | Crea i bucket InfluxDB necessari per i risultati.                     |
| .env               | File di configurazione delle variabili d'ambiente (URI, Token, ecc..) |

## **Test e Validazione**

| **Cartella / File** | **Descrizione**                                                                                 |
|---------------------|-------------------------------------------------------------------------------------------------|
| tests_locust/       | Contiene test di carico Locust per ogni endpoint (saveData, runModel, ecc.).                    |
| final_test.py       | Script di test end-to-end per validare tutta la pipeline (salvataggio → inferenza → risultati). |

## **Come installare ed eseguire**

### **Requisiti**

- Docker installato sulla macchina (versione consigliata: Docker 20+)

- Docker Compose (versione consigliata: v2+)

- Connessione a Internet (per il primo build e accesso a MongoDB Atlas)

### **1. Clonare il repository** {#clonare-il-repository}

git clone https://github.com/ff225/HealthConnectPlus.git  
cd \<NOME_CARTELLA_PROGETTO\>

### **2. Configurare le variabili d'ambiente** {#configurare-le-variabili-dambiente}

Assicurati che il file .env sia presente nella root del progetto. In
caso contrario, crealo e inserisci i parametri necessari:

\# MongoDB  
MONGO_URI=mongodb+srv://272519:bSVDnlDZVVEes2hJ@cluster0.mongodb.net/?retryWrites=true&w=majority  
  
\# InfluxDB  
INFLUXDB_URL=http://influxdb:8086  
INFLUXDB_TOKEN=
jf-r7Bz78njetwULkCAYJrGfh22yb28sariPO13Jf-uxbAEvaiKkQzWhhS3t2RxwSZT7EAOk91WEPeU4bcZN-A==  
INFLUXDB_ORG=Unimore  
INFLUXDB_BUCKET=healthconnect  
INFLUXDB_RESULTS_BUCKET=model_results  
  
\# PostgreSQL  
PG_HOST=pg_queue  
PG_PORT=5432  
PG_DB=api_queue  
PG_USER=api  
PG_PASSWORD=test

Se stai usando la versione fornita nel progetto, .env è già pronto.

### **3. Avviare l'intero sistema** {#avviare-lintero-sistema}

docker-compose up \--build

Questo comando:

- builda le immagini Docker per l\'API e i worker

- avvia MongoDB Atlas (remoto), InfluxDB, PostgreSQL e 8 worker Python
  per la coda

- inizializza automaticamente i bucket InfluxDB e la coda PostgreSQL
  (via init.sql e init_influxdb.py)

Puoi anche salvare tutto in log:

docker-compose up \--build \> docker.log 2\>&1

### **4. Verificare l'API** {#verificare-lapi}

Dopo l'avvio, visita:

[http://localhost:8000/docs  
](http://localhost:8000/docs)

Per esplorare e testare tutti gli endpoint tramite Swagger UI.

## **Avvio senza Docker (alternativa manuale)**

Se si preferisce non utilizzare Docker, puoi avviare l'intero sistema
manualmente tramite script già pronti per Windows (.bat) e Linux/macOS
(.sh).

###  **Requisiti** {#requisiti-1}

- Python 3.11 installato

- Ambiente virtuale configurato

- Tutte le dipendenze installate:

pip install -r requirements.txt

- Assicurati che:

  - MongoDB (es. MongoDB Atlas) sia raggiungibile

  - InfluxDB sia attivo su INFLUXDB_URL

  - PostgreSQL sia attivo e la tabella data_queue già inizializzata (usa
    init.sql)

  - Il file .env sia correttamente configurato

### **Windows: run_api_and_worker.bat** {#windows-run_api_and_worker.bat}

Script per avviare:

- 1 server Uvicorn con 8 worker

- 6 worker per salvataggio asincrono su PostgreSQL

Per eseguirlo:

run_api_and_worker.bat

Verranno creati:

- uvicorn_log.log per i log dell'API

- workerX.log per ciascun worker (X = 1..6)

### **Linux/macOS: run_api_and_worker.sh** {#linuxmacos-run_api_and_worker.sh}

Script equivalente per ambienti UNIX:

chmod +x run_api_and_worker.sh  
./run_api_and_worker.sh

Anche in questo caso verranno avviati:

- L'API FastAPI con 8 worker

- 6 worker Python per svuotare la coda

L'API sarà disponibile all'indirizzo:

<http://127.0.0.1:8000/docs>

## **Test e Validazioni**

Il progetto supporta sia **test automatici di carico** (Locust) che
**test manuali** via Postman, HTTPie o script Python. È possibile
validare la pipeline completa (dati → inferenza → risultati) in modo
semplice e riproducibile.

### **Test Automatici (Locust)**

I test di carico sono disponibili nella cartella tests_locust/ e coprono
tutti gli endpoint principali:

| **Endpoint**      | **File Locust**            |
|-------------------|----------------------------|
| /saveData         | locust_saveData.py         |
| /runModel         | locust_runModel.py         |
| /runModelNoSave   | locust_runModelNoSave.py   |
| /getModels        | locust_getModels.py        |
| /getResults       | locust_getResults.py       |
| /getExecutionData | locust_getExecutionData.py |

Ogni test supporta configurazioni variabili (1--4 sensori, 1--100
utenti, 10 iterazioni) con ritardi di 5--10ms tra utenti.

Esecuzione tipica in Docker:

tests_locust/run_saveData.sh  
tests_locust/run_runModel.sh

I risultati vengono salvati in results/ (nella root del progetto) per
successiva analisi con analyze_all.py.

### 

###  **Test Manuali** {#test-manuali}

Per facilitare la validazione manuale, è incluso uno script di
generazione payload:

#### **generate_payload.py** {#generate_payload.py}

Questo script crea automaticamente una cartella example_payloads/ con i
seguenti file JSON di esempio:

| **File**                      | **Endpoint target** | **Contenuto**                                |
|-------------------------------|---------------------|----------------------------------------------|
| payload_saveData_X.json       | /saveData           | Payload completi per 1--4 sensori            |
| payload_runModel_X.json       | /runModel           | Include selection_mode: \"best\"             |
| payload_runModelNoSave_X.json | /runModelNoSave     | Idem sopra, senza salvataggio InfluxDB       |
| payload_getResults.json       | /getResults         | Con parametri user, exec, model_name, sensor |
| payload_getExecutionData.json | /getExecutionData   | Parametri base per lettura grezzi            |
| payload_deleteAll.json        | /deleteAll          | Elimina dati test                            |

Per generare questi file:

python generate_payload.py

Questi file possono essere utilizzati con curl, Postman, o un semplice
script Python per testare ogni endpoint. Esempio:

http POST <http://localhost:8000/runModel> \<
example_payloads/payload_runModel_2.json

###  **Test End-to-End** {#test-end-to-end}

È disponibile anche uno script completo final_test.py per validare tutta
la pipeline in sequenza:

1.  /deleteAll

2.  /saveData

3.  /getModels

4.  /runModel

5.  /getResults

6.  /getExecutionData

Questo test è utile per verificare che l\'intero sistema sia
correttamente integrato e funzionante.

## **Esempi di utilizzo**

### **1. Chiamate API con curl** {#chiamate-api-con-curl}

#### **Salvataggio dati sensoriali (/saveData)**

curl -X POST <http://localhost:8000/saveData> \\  
-H \"Content-Type: application/json\" \\  
-d @payload_minimo.json

#### **Esecuzione modello (/runModel)**

curl -X POST <http://localhost:8000/runModel> \\  
-H \"Content-Type: application/json\" \\  
-d @payload_completo_con_selection_best.json

#### **Recupero risultati (/getResults)**

curl
\"<http://localhost:8000/getResults?user_id=test_user_1&execution_id=exec_1&model_name=cnn_leftwrist.tflite&sensor=leftwrist&fog_ready=true>\"

#### **Recupero dati grezzi (/getExecutionData)**

curl
\"<http://localhost:8000/getExecutionData?user_id=test_user_1&execution_id=exec_1>\"

#### **Pulizia dati (/deleteAll)**

curl -X DELETE
\"<http://localhost:8000/deleteAll?user_id=test_user_1&execution_id=exec_1>\"

### **2. Sequenza di utilizzo consigliata** {#sequenza-di-utilizzo-consigliata}

Step 1 → POST /saveData  
→ Salva i dati sensoriali nel sistema (via coda PostgreSQL)  
  
Step 2 → POST /runModel  
→ Esegue un modello compatibile automaticamente (selection_mode =
\"best\")  
  
Step 3 → GET /getResults  
→ Recupera i risultati del modello; se fog_ready=true restituisce anche
matrici (FOG-ready)  
  
Step 4 → GET /getExecutionData  
→ Recupera i dati sensoriali grezzi salvati  
  
Step 5 → DELETE /deleteAll  
→ (Opzionale) Rimuove dati e risultati da InfluxDB

### **3. Modalità FOG-ready** {#modalità-fog-ready}

Quando si specifica il parametro fog_ready=true nella richiesta a
/getResults, l\'API restituisce una struttura compatta adatta per
l'elaborazione edge, come:

{  
\"user_id\": \"test_user_1\",  
\"execution_id\": \"exec_1\",  
\"model_name\": \"cnn_leftwrist.tflite\",  
\"sensor\": \"leftwrist\",  
\"output_matrix\": \[\[-3.2, 1.5\], \[2.1, -1.3\], \...\],  
\"shape\": \[50, 2\],  
\"timestamp\": \"2025-05-27T11:02:01Z\"  
}

Questa forma è utile per:

- Dispositivi embedded (Fog computing)

- App mobili

- Analisi su board IoT

## **Esempi d'uso con curl (payload generati automaticamente)**

### **1. /saveData** {#savedata}

Salva dati sensoriali per un utente:

curl -X POST <http://localhost:8000/saveData>

-H \"Content-Type: application/json\"

-d @tests_locust/generated_payloads/saveData_payload.json

### **2. /runModel** {#runmodel}

Esegue tutti i modelli compatibili con i dati salvati (modalità best):

curl -X POST <http://localhost:8000/runModel>

-H \"Content-Type: application/json\"

-d @tests_locust/generated_payloads/runModel_payload.json

Il payload deve contenere user_id, execution_id (salvati con /saveData),
ma non serve e nel body.

### **3. /runModelNoSave**  {#runmodelnosave}

Esegue un modello specifico con dati contenuti nel payload (senza
salvataggio su InfluxDB):

curl -X POST <http://localhost:8000/runModelNoSave>

-H \"Content-Type: application/json\"

-d @tests_locust/generated_payloads/runModelNoSave_payload.json

Il payload deve contenere model_name, input_shape, structured_features,
e (dati grezzi).

### **4. /getModels**  {#getmodels}

Ritorna l'elenco dei modelli compatibili con i dati già salvati:

curl -X POST <http://localhost:8000/getModels>

-H \"Content-Type: application/json\"

-d @tests_locust/generated_payloads/getModels_payload.json

### **5. /getResults** {#getresults}

Recupera gli output dei modelli già eseguiti per un utente/esecuzione:

curl -X GET
\"<http://localhost:8000/getResults?user_id=test_user_run&execution_id=exec_run_cnn_leftwrist%22>

### **6. /getExecutionData** {#getexecutiondata}

Recupera i dati sensoriali salvati per una specifica esecuzione:

curl -X GET
\"<http://localhost:8000/getExecutionData?user_id=test_user_run&execution_id=exec_run_cnn_leftwrist%22>

### **7. /deleteAll** {#deleteall}

Elimina i dati da InfluxDB per una specifica esecuzione:

curl -X DELETE
\"<http://localhost:8000/deleteAll?user_id=test_user_run&execution_id=exec_run_cnn_lef>
