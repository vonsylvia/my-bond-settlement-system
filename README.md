# Bond Settlement System

A complete SWIFT bond settlement system built for IBM WebSphere + Oracle environment.

## Architecture

- **Frontend**: Vue.js 3 + Vite + Axios
- **Backend**: Spring MVC 6 REST API
- **SWIFT Messaging**: Prowide Core (MT541 send / MT548 receive)
- **Message Queue**: IBM MQ via JMS
  - **Sending**: Spring JmsTemplate with application-managed MQ client connection
  - **Receiving**: Message-Driven EJB (MDB) via JCA activation spec on IBM MQ Resource Adapter
- **Persistence**: Hibernate 6 / JPA 3.1 on Oracle Database
- **Application Server**: IBM WebSphere Liberty (Jakarta EE 10)
- **Packaging**: EAR (WAR + EJB JAR + Common Library)

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 20+ (for frontend)
- Docker & Docker Compose (for local development)

### Runtime Stack (provided via Docker)

- IBM WebSphere Liberty 24.x (Jakarta EE 10)
- IBM MQ 9.3+ (Jakarta-compatible Resource Adapter)
- Oracle Database 19c+ (XE edition for dev)

## Settlement Flow
[Click for the sequence diagram](https://mermaid.live/view#pako:eNqVVmtv4kYU_StX_lAZLUt4OQGrG4lXqKM44ZmoFVI12DdkusZmZ8ZkaZSv_QH9if0lvWOb4GB22-VDBPY5Z-499zF5MbzIR8M2JH6JMfSwz9lKsPUiBPowT0UCZoL5KNInGyYU9_iGhQrmDjAJ9zFW_pBwJaJQYegXYZ1RgptuBA9X4N73fl6Ks0vzoTMpFcHT-14CRqUCXGOopii23MMi0h1roNN16Vvx7fAhkXlwrmbQCQLOKLXk3CFT-Mx2J_T63YTyzB_VBDfBjh6kkQ6uuycinQySSCfoRaHH6QTFo_Cb0abid4J5AUKfKbZkkmApMDX44-Xl3LFhGi_XXIF8cwB4KJWIPa2f4ucOYclWG0Z30xmcsQ0_O-BTDL0mENlpg0wUnYOKKXSxpSrtAxDoKRCrpVlvVMtQb1r0x7KypPXnNlII0RaFLlC537XhLlbLKA59G7oxD3z4iWoW-uDOrGbtwCM0RaHxU7bFfCZgjga3fed2WDpGJzGPRPTMfYSlFpf_IZu8BcGeYY1SshUeI92xDddrOcP1JqD6VySFaibNUZlSFJXxfDAflE7pzzc-EUAqpmIJ__z1NxBhliKTbj-4ndWvXq1BTyCxfDCVruwEH8uZwifNPmG7Ze1tb1RP2u6Oy8MHO2voW1TPkfisTfIoYRqrA8UdUxwamppiUn8iJ4-uBJLUI4zYTjdJ7pDhQ8ZIxEkbtizgftLPVFYRxerdCQleO6pPaIF5mFWYJkmWCvYc9Zf1_US1706YdZceS5HmILMTt5zBda9zlLOrafQYOtRf2zT66QY9UIKvVigkRKGbtoeZO5loe3I2vBNccWrTXSWIos_xxlwY4uSMGwUVWgk2bNKiHPaI6UVCYJBwHb8Ma0VJ5LjE2nNHTEjc-4pfqXvIt30PUS1k3t8DVwd_xWn68vO13L0xD3gW7IsEn8DtzHq_DPqHt-8Vi63__wjLiCJ5igKfukaC2Z3_asOHL2oHZzQ7Nzc2fKQfpW-JdGKftl8Qrageg9nsZuDSxPxeOBkDMuotk6uOc_MjiXwffzqEY877CLJl9mOSBdLb_ZmbnXeXQ-8Jvc_5uyFN6vhaGA4Kt8LZy74bXvMXhA5uHKPYFW8Zaum9npPf29kmy0pSKizA3D7wUTEeSPiQxWmUjZXgvmGTGpaNNYo10z-NF62yMNQT8RaGTV99fGRxoBbGInwlGl2iv0XRes-kpbR6MuxHRkUoG3FS4Ow_lzcIeYiiR1tEGXatdnGeiBj2i_GVfrfalfpF06o1Wy2r1ahZzbKxo8eNaqXarp-3axfNptVotF_Lxp_JsbVKo1pr1y-qjaZltav189brv6-4yUE)
```mermaid
sequenceDiagram
    actor Trader
    participant UI as Vue.js Frontend
    participant API as Spring MVC<br/>(WAR)
    participant SVC as SettlementService
    participant MQ as IBM MQ
    participant GW as SWIFT Alliance<br/>Gateway
    participant MDB as SwiftReplyMDB<br/>(EJB)
    participant REC as ReconciliationService
    participant DB as Oracle Database

    Trader->>UI: Submit settlement instruction
    UI->>API: POST /api/settlement
    API->>SVC: submitInstruction(request)

    rect rgb(230, 245, 255)
        Note over SVC,DB: Outbound: Build & Send MT541
        SVC->>DB: Save instruction (PENDING)
        SVC->>SVC: Prowide builds MT541
        SVC->>DB: Save MT541 raw message
        SVC->>MQ: JmsTemplate.send(SWIFT.SEND.QUEUE)
        SVC->>DB: Update status → SENT
    end

    API-->>UI: 201 Created (tradeRef, status=SENT)

    rect rgb(255, 245, 230)
        Note over MQ,GW: SWIFT Network Processing
        MQ->>GW: MT541 (Receive Free of Payment)
        GW->>GW: SWIFTNet validation & routing
        GW->>MQ: MT548 (Settlement Status)
    end

    rect rgb(230, 255, 230)
        Note over MQ,DB: Inbound: MDB receives MT548 via JCA
        MQ->>MDB: JCA Activation Spec triggers onMessage()
        MDB->>MDB: ServiceRegistry.lookup("reconciliationService")
        MDB->>REC: processSwiftReply(correlationId, mt548)
        REC->>REC: Parse MT548 (extract tradeRef & status)
        REC->>DB: Find instruction by tradeRef
        alt Status = MATCHED
            REC->>DB: Update status → MATCHED
            REC->>DB: Update bond holdings (BUY: +qty / SELL: -qty)
            REC->>DB: Audit log: SETTLEMENT_MATCHED
        else Status = FAILED
            REC->>DB: Update status → FAILED
            REC->>DB: Audit log: SETTLEMENT_FAILED
        else Status = PENDING
            REC->>DB: Audit log: SETTLEMENT_PENDING
        end
    end

    Trader->>UI: Check settlement status
    UI->>API: GET /api/settlement/{tradeRef}
    API->>DB: Query instruction
    DB-->>API: Instruction (status=MATCHED)
    API-->>UI: Settlement details + status
```

### Component Architecture
[Click for the graph](https://mermaid.live/view#pako:eNqNVNtymzAQ_RWN-tJObeIbdsxDZzAmqT2-BZxk2tAHAcJRIgMjIKkT59-7EnbiW2aqB2YlnXO0qz3iFQdJSLGBF4Kk92je82IEIyv8csE2nTsPwxfNSPBIFhT5SRxWM5rnnC5pnGuUCA__KWl71FtF_UBWfVCgcag9E4HcVLB4gcY31h5ZDmvujIDo2O4cWUmci4RzKrIjnHtjNQDnvh_gUvHEAnoEdGxrOpGKNEjigHFGcpbEn6Fde9K3ZebuM4vyMc0yqNqFvKlAZ2i4zOZ0mXKSHzP75hRow5kpowzAP5lPRQxQ1D4CDyaDucpeZeHQBctysRrELGeEsxe6f6lw_Ikbtoe98bS_f8n0wdceDloix7jfa2yLcmjKV7DwH0eMBj1gceafSSug7J4IGp644cvjWk6rq-DAZIPJhWOCwCCOBAFqEeSF2L_f8ZXsizzkdnAx1-REu7q2r-0DFPT65h3l2LPRrxOwqWNaI_vuq4enggScoj7JiU8yOPLbDkyJbMWQycE4cUDRJTT0mXyUt1ORtC6qVn-slXnN2WCtXFpuykhuSnccrJSe2yyqWKkMx-56U3m5V8YlRSa1Yaj85GJZ_xYr41LHMpEZ5OypNH5Kg7XyQwmUTlQwoRpHxVr2cyMCKLXHk-SxSNF3JGjEaSB1dmAQoKoGMF-wcEHX5ZPb7kG4XzcEZV7wVOBJzc31piXvzshX0BWpGjHOjS9RFHZqtQpYI3mkxpdms7mLU0mWwG7NtrufAlWlJfC8Y9l2bxeIK_APZCE2wH-0gpdULImc4lcp4eH8Hh6Yhw0IQxqRguce9uI3oKUk_p0kyy1TJMXiHhsR4RnMijQEv_QZAat_QNTvxEqKOMeGrhSw8Yr_YqNe11qtjt5tNGrdTvNcr7UreIWNaqPV1PS63m2123pbr3f0twp-UYfWtUazVe_qerdWqwOv_fYP6HWy5g)
```mermaid
graph TB
    subgraph EAR["EAR Package bond-settlement.ear"]
        subgraph WAR["settlement-backend.war Spring MVC"]
            CTRL["REST Controllers"]
            SVC2["SettlementService"]
            RECON["ReconciliationService"]
            SENDER["SwiftMessageSender / JmsTemplate"]
            DAO["JPA DAOs / Hibernate 6"]
            INIT["ServiceRegistryInitializer"]
        end
        subgraph EJBMOD["settlement-ejb.jar"]
            MDB2["SwiftReplyMDB"]
        end
        subgraph LIB["lib/ EAR shared"]
            REG["ServiceRegistry"]
        end
    end

    subgraph INFRA["Infrastructure"]
        MQSEND["SWIFT.SEND.QUEUE"]
        MQRECV["SWIFT.REPLY.QUEUE"]
        ORACLE[("Oracle Database")]
        SWIFT["SWIFT Alliance Gateway"]
    end

    CTRL -->|REST API| SVC2
    SVC2 --> DAO
    SVC2 --> SENDER
    SENDER -->|JMS| MQSEND
    MQSEND --> SWIFT
    SWIFT --> MQRECV
    MQRECV -->|JCA ActivationSpec| MDB2
    INIT -->|register| REG
    MDB2 -->|lookup + reflection| REG
    REG -.->|bridge| RECON
    RECON --> DAO
    DAO -->|JPA / JTA| ORACLE

    style REG fill:#ffd700,stroke:#333
    style MDB2 fill:#90EE90,stroke:#333
    style INIT fill:#87CEEB,stroke:#333
```

## Quick Start (Docker)

### 1. Prepare dependencies

Download the following and place in `docker/liberty/`:

```bash
# Oracle JDBC driver (from Maven Central or Oracle)
mkdir -p docker/liberty/jdbc
cp ~/.m2/repository/com/oracle/database/jdbc/ojdbc11/23.3.0.23.09/ojdbc11-23.3.0.23.09.jar \
   docker/liberty/jdbc/ojdbc11.jar
```

### 2. Build

```bash
mvn clean package -DskipTests
```

### 3. Start Docker environment

```bash
docker compose up -d
```

This starts:
- **Oracle XE** on port `1521`
- **IBM MQ** on port `1414` (web console on `9443`)
- **WebSphere Liberty** on port `9080` (HTTPS on `9444`)

The Liberty image is built from `docker/liberty/Dockerfile`, which extracts the IBM MQ Jakarta Resource Adapter (`wmq.jakarta.jmsra.rar`) from the MQ container image via multi-stage build.

### 4. Initialize database

Wait for Oracle to be healthy, then run the DDL:

```bash
docker exec -i settlement-oracle bash -c \
  "sqlplus settlement/settlement123@//localhost:1521/XEPDB1" < db/V1__create_schema.sql
```

### 5. Verify deployment

```bash
# Check MQ connectivity
curl http://localhost:9080/settlement/api/mq/health

# Test MDB message delivery (sends test MT548 to reply queue)
curl -X POST "http://localhost:9080/settlement/api/mq/test-mdb?correlationId=TEST-001"

# Check application health
curl http://localhost:9080/settlement/api/holdings

# Submit a settlement instruction
curl -X POST http://localhost:9080/settlement/api/settlement \
  -H "Content-Type: application/json" \
  -d '{
    "isin": "US0378331005",
    "quantity": 1000,
    "direction": "BUY",
    "counterparty": "DEUTDEFF",
    "bicCode": "CITIUS33",
    "accountId": "ACC-001",
    "settlementDate": "2026-06-01"
  }'
```

### 6. Build frontend (optional)

```bash
cd settlement-frontend
npm install
npm run dev    # Development server on http://localhost:5173
npm run build  # Production build
```

## Common Commands

| Command | Description |
|---------|-------------|
| `mvn clean package` | Build all modules (produces EAR) |
| `mvn clean package -DskipTests` | Build without running tests |
| `mvn test` | Run unit tests |
| `mvn verify` | Run unit + integration tests |
| `docker compose up -d` | Start all services |
| `docker compose down` | Stop all services |
| `docker compose up -d --force-recreate liberty` | Redeploy after rebuild |
| `docker compose logs -f liberty` | Follow Liberty logs |
| `docker compose logs -f ibmmq` | Follow MQ logs |

## MQ Administration

```bash
# Enter MQ container
docker exec -it settlement-mq bash

# Check queue depths
echo "DISPLAY QLOCAL(*) CURDEPTH" | runmqsc SETTLEMENT_QM

# Put a test message on reply queue
echo "test message body" | /opt/mqm/samp/bin/amqsput SWIFT.REPLY.QUEUE SETTLEMENT_QM

# Browse messages (non-destructive)
/opt/mqm/samp/bin/amqsbcg SWIFT.SEND.QUEUE SETTLEMENT_QM
```

## Database Administration

```bash
# Connect to Oracle
docker exec -it settlement-oracle sqlplus settlement/settlement123@//localhost:1521/XEPDB1

# Useful queries
SELECT TRADE_REF, STATUS, ISIN FROM SETTLEMENT_INSTRUCTION ORDER BY CREATED_AT DESC;
SELECT * FROM BOND_HOLDING;
SELECT TRADE_REF, EVENT_TYPE, DETAIL FROM AUDIT_LOG ORDER BY CREATED_AT DESC;
```

## Modules

| Module | Description |
|--------|-------------|
| `settlement-common` | Shared library with cross-module service bridge (ServiceRegistry) |
| `settlement-backend` | Spring MVC WAR with REST API, service layer, JMS sender |
| `settlement-ejb` | Message-Driven EJB for SWIFT MT548 reply processing |
| `settlement-ear` | Enterprise Archive packaging for WebSphere deployment |
| `settlement-frontend` | Vue.js 3 single-page application |

## Project Structure

```
my-bond-settlement-system/
├── pom.xml                          # Parent POM (dependency management)
├── docker-compose.yml               # Local dev environment
├── db/
│   └── V1__create_schema.sql        # Oracle DDL
├── docker/
│   ├── liberty/
│   │   ├── server.xml               # Liberty config (MQ RA, JMS, activation spec)
│   │   ├── Dockerfile               # Liberty image with MQ Jakarta RA
│   │   └── jdbc/                    # JDBC driver (gitignored)
│   └── mq/
│       └── config.mqsc              # MQ queue definitions
├── settlement-common/               # Shared library (ServiceRegistry)
├── settlement-backend/              # Spring MVC WAR module
│   └── src/main/java/com/settlement/
│       ├── config/
│       │   ├── MqClientConfig.java          # JNDI JMS ConnectionFactory + JmsTemplate (XA)
│       │   └── ServiceRegistryInitializer.java  # Registers Spring beans for MDB access
│       ├── controller/
│       │   ├── SettlementController.java    # REST API
│       │   └── MqConnectivityController.java # MQ health & MDB test endpoints
│       ├── service/                         # Business logic
│       ├── jms/SwiftMessageSender.java      # JMS sender (MT541)
│       ├── reconcile/ReconciliationService.java # MT548 processing & reconciliation
│       ├── dao/                             # Data access (JPA)
│       ├── entity/                          # JPA entities
│       └── dto/                             # Request/Response DTOs
├── settlement-ejb/                  # EJB module
│   └── src/main/
│       ├── java/com/settlement/ejb/
│       │   └── SwiftReplyMDB.java           # Message-Driven Bean (MT548 receiver)
│       └── resources/META-INF/
│           ├── ejb-jar.xml                  # EJB deployment descriptor
│           └── ibm-ejb-jar-bnd.xml          # Liberty MDB activation spec binding
├── settlement-ear/                  # EAR packaging
├── settlement-frontend/             # Vue.js 3 frontend
└── docs/
    └── openapi.yaml                 # API specification
```

## Messaging Architecture

### Outbound (Send MT541)

```
Spring JmsTemplate → MQQueueConnectionFactory (app-managed) → SWIFT.SEND.QUEUE → SWIFT Gateway
```

### Inbound (Receive MT548)

```
SWIFT Gateway → SWIFT.REPLY.QUEUE → JCA Activation Spec (container-managed)
    → SwiftReplyMDB.onMessage()
    → ServiceRegistry.lookup("reconciliationService")
    → ReconciliationService.processSwiftReply()
    → Oracle DB (update status + holdings)
```

### Cross-Module Bridge

The WAR (Spring) and EJB modules run in separate classloaders within the EAR.
`ServiceRegistry` in `settlement-common` (EAR lib/) provides a thread-safe bridge:
- **WAR startup**: `ServiceRegistryInitializer` registers Spring-managed `ReconciliationService`
- **MDB runtime**: `SwiftReplyMDB` retrieves it via `ServiceRegistry.lookup()` + reflection

### Transaction Management (JTA + XA Two-Phase Commit)

The system uses **JTA with XA two-phase commit** to guarantee atomic consistency between Oracle Database and IBM MQ. Liberty's JTA transaction manager coordinates both resources.
[Click for the graph](https://mermaid.live/view#pako:eNqNklFv2jAUhf-K5b50GlDSNCSxpmqEMKlVvTZtJlVbpsqQC0RLbOQ43Rjlv_di2Eqqapofotg-3_G5117TqcqBMjrXYrkgV7eZJDjqZrJbuEyH3zKKX5JqIWsxNYWS5PiqmIA2q3cZ_b4DtiMvNOz20-hlNeVocAhzIcUc9IeJPjk_Tn-q7s1C1EBGqqoK03a8Hz7EEeL3Q3ILtWr0FJjlrrWYlkBiYcQE4dcQT96CLiJOeNLSppx0u-dPSw1LoeFkajM87c79HxXaWRXIPJOvWnd9gyE-HlQuSsLBLFTeihBHD1YZR4zciUcghayNbixhY78nX5a5MEBqI0xTt2Ce7GCeIIwhCE-9M8dij4Ugl5_jCzL69Bc5iGmPJd0e1gWyLGoDeatu6_zG_rbifaFmhVeAvZkVZcmOZrPc7_c7mF39AHbkuu6hzDrvlYE_Go-jfyh5sleG_fE4bHnSDr7UIqcMWwQdWoGuxHZK11uPjJoFVPgcGP7mMBNNaTKayQ1iSyG_KlX9IbVq5gvKZqKscdbYDseFwKt7kWC7QI9UIw1lnnWgbE1_UeaGPSfwz8LA94OBc-oOOnRFmeM6vSAI_dPAc3zPcQaDTYf-tmf2e4HvbZ4B900Lsg)
```mermaid
graph LR
    subgraph JTA["JTA Transaction (Liberty)"]
        direction TB
        TM["Transaction Manager<br/>(Two-Phase Commit)"]
        XA_DB["XA Resource:<br/>Oracle Database"]
        XA_MQ["XA Resource:<br/>IBM MQ"]
        TM -->|prepare/commit| XA_DB
        TM -->|prepare/commit| XA_MQ
    end

    subgraph OP["@Transactional Method"]
        DB_OP["DB: Save instruction<br/>+ Update status"]
        MQ_OP["MQ: Send MT541<br/>via JNDI CF"]
    end

    DB_OP -.->|enlisted| XA_DB
    MQ_OP -.->|enlisted| XA_MQ

    style TM fill:#ffd700,stroke:#333
    style XA_DB fill:#87CEEB,stroke:#333
    style XA_MQ fill:#90EE90,stroke:#333
```

**Why JTA is required:**

1. **XA consistency (outbound)** — `SettlementService.submitInstruction()` saves to Oracle AND sends MT541 to MQ in the same `@Transactional` method. Using a container-managed JNDI `ConnectionFactory` (`jms/SwiftQueueCF`), both resources are enlisted in the JTA transaction. If either fails, both roll back atomically.
2. **MDB compatibility (inbound)** — MDB runs in a container-managed JTA transaction. Spring's `@Transactional` on `ReconciliationService.processSwiftReply()` joins this existing JTA transaction instead of attempting a local `Connection.commit()` (which would cause `DSRA9350E`).
3. **Hibernate JTA platform** — configured via `hibernate.transaction.jta.platform` = `WebSphereLibertyJtaPlatform`.

Key configuration:

| File | Setting | Purpose |
|------|---------|---------|
| `applicationContext.xml` | `JtaTransactionManager` | Spring delegates to Liberty's JTA |
| `applicationContext.xml` | `jtaDataSource` (not `dataSource`) | DB connection enlisted in JTA |
| `MqClientConfig.java` | `InitialContext.doLookup("jms/SwiftQueueCF")` | MQ connection enlisted in JTA |
| `server.xml` | `<jmsQueueConnectionFactory>` | Container-managed XA connection factory |

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/holdings` | List all bond holdings |
| `GET` | `/api/holdings/{accountId}` | Get holdings for account |
| `POST` | `/api/settlement` | Submit settlement instruction |
| `GET` | `/api/settlement/{tradeRef}` | Get instruction status |
| `GET` | `/api/mq/health` | IBM MQ connection health check |
| `POST` | `/api/mq/test-mdb` | Send test MT548 to verify MDB processing |

## Configuration

Environment variables used by the Docker setup:

| Variable | Default | Description |
|----------|---------|-------------|
| `ORACLE_HOST` | `oracle` | Oracle DB hostname |
| `ORACLE_PORT` | `1521` | Oracle DB port |
| `MQ_HOST` | `ibmmq` | IBM MQ hostname |
| `MQ_PORT` | `1414` | IBM MQ port |
| `MQ_CHANNEL` | `SETTLEMENT.SVRCONN` | MQ channel name |
| `MQ_QMGR` | `SETTLEMENT_QM` | MQ queue manager |
| `MQ_USER` | `app` | MQ application user |
| `MQ_PASSWORD` | `passw0rd` | MQ application password |

## Liberty Server Configuration

Key server.xml elements for MDB activation:

| Element | Purpose |
|---------|---------|
| `<resourceAdapter id="mqJmsRa">` | IBM MQ Jakarta Resource Adapter |
| `<jmsQueueConnectionFactory>` | Container-managed MQ connection factory |
| `<jmsQueue>` | SWIFT send/reply queue definitions |
| `<jmsActivationSpec id="jms/SwiftReplyActivationSpec">` | MDB activation spec bound to SWIFT.REPLY.QUEUE |

## Troubleshooting

### Liberty startup slow

Liberty first start downloads features. Subsequent starts are faster (~25s).

### MQ connection refused (MQRC 2035)

Ensure MQ permissions are granted:

```bash
docker exec settlement-mq bash -c '
  setmqaut -m SETTLEMENT_QM -t qmgr -p app +connect +inq
  setmqaut -m SETTLEMENT_QM -t queue -n "SWIFT.*" -p app +put +get +inq
  echo "REFRESH SECURITY(*)" | runmqsc SETTLEMENT_QM
'
```

### MDB not receiving messages

1. Check activation spec binding: `docker logs settlement-liberty | grep CNTR0180I`
2. Check endpoint activation: `docker logs settlement-liberty | grep J2CA8801I`
3. Verify MQ RA installed: `docker logs settlement-liberty | grep J2CA7001I`
4. Test connectivity: `curl http://localhost:9080/settlement/api/mq/health`

### MDB receives messages but DB not updated

Check for `DSRA9350E: Operation Connection.commit is not allowed during a global transaction` in FFDC logs. This indicates Spring is using `JpaTransactionManager` instead of `JtaTransactionManager`. Ensure `applicationContext.xml` uses `JtaTransactionManager` and `jtaDataSource` (see Transaction Management section above).

## API Documentation

See [docs/openapi.yaml](docs/openapi.yaml) for the full OpenAPI 3.0 specification.
