# Bond Settlement System

A complete SWIFT bond settlement system built for IBM WebSphere + Oracle environment.

## Architecture

- **Frontend**: Vue.js 3 + Vite + Axios
- **Backend**: Spring MVC 6 REST API
- **SWIFT Messaging**: Dual-standard support (MT and MX)
  - **MT (FIN)**: Prowide Core — MT541 (send) / MT548 (receive)
  - **MX (ISO 20022)**: Prowide ISO 20022 — sese.023.001.09 (send) / sese.024.001.10 (receive)
  - **Strategy Pattern**: `SwiftMessageStrategy` interface with `MtStrategy` / `MxStrategy` implementations
  - **Canonical Data Model**: Format-independent `CanonicalSettlement` / `CanonicalStatusAdvice` decouples business logic from message formats
- **Message Queue**: IBM MQ via JMS
  - **Sending**: Spring JmsTemplate with application-managed MQ client connection
  - **Receiving**: Message-Driven EJB (MDB) via JCA activation spec on IBM MQ Resource Adapter
- **Persistence**: Hibernate 6 / JPA 3.1 on Oracle Database
- **Application Server**: IBM WebSphere Liberty (Jakarta EE 10)
- **Packaging**: EAR (WAR + EJB JAR + Common Library)

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 24+ (for frontend)
- Docker & Docker Compose (for local development)

### Runtime Stack (provided via Docker)

- IBM WebSphere Liberty 24.x (Jakarta EE 10)
- IBM MQ 9.3+ (Jakarta-compatible Resource Adapter)
- Oracle Database 19c+ (XE edition for dev)

## Settlement Flow
```mermaid
sequenceDiagram
    actor Trader
    participant UI as Vue.js Frontend
    participant API as Spring MVC<br/>(WAR)
    participant SVC as SettlementService
    participant ASYNC as AsyncSettlement<br/>Processor
    participant MQ as IBM MQ
    participant GW as SWIFT Alliance<br/>Gateway
    participant MDB as SwiftReplyMDB<br/>(EJB)
    participant REC as ReconciliationService
    participant DB as Oracle Database

    Trader->>UI: Submit settlement instruction
    UI->>API: POST /api/settlement
    API->>SVC: submitInstruction(request)

    rect rgb(230, 245, 255)
        Note over SVC,DB: Phase 1 (sync): Save instruction
        SVC->>SVC: CanonicalMapper → Strategy builds MT541 or sese.023
        SVC->>DB: Save instruction (PENDING) + SWIFT_MESSAGE
    end

    API-->>UI: 202 Accepted (tradeRef, status=PENDING)

    rect rgb(240, 230, 255)
        Note over ASYNC,MQ: Phase 2 (async XA): Send MT/MX
        SVC--)ASYNC: processSettlementAsync(tradeRef)
        ASYNC->>DB: Update status → SUBMITTING
        ASYNC->>MQ: JmsTemplate.send(SWIFT.SEND.QUEUE)
        ASYNC->>DB: Update status → SENT
        Note over ASYNC: On failure: exponential backoff retry<br/>(2s → 4s → 8s, max 3 attempts)
        alt Retry pending (not exhausted)
            ASYNC->>DB: Update status → RETRYING
        else All retries failed
            ASYNC->>DB: Update status → FAILED
            ASYNC--)ASYNC: Webhook alert
        end
    end

    rect rgb(255, 245, 230)
        Note over MQ,GW: SWIFT Network Processing
        MQ->>GW: MT541/sese.023 (Settlement Instruction)
        GW->>GW: SWIFTNet validation & routing
        GW->>MQ: MT548/sese.024 (Settlement Status)
    end

    rect rgb(230, 255, 230)
        Note over MQ,DB: Inbound: MDB receives MT548/sese.024 via JCA
        MQ->>MDB: JCA Activation Spec triggers onMessage()
        MDB->>MDB: ServiceRegistry.lookup("reconciliationService")
        MDB->>REC: processSwiftReply(correlationId, rawMessage)
        REC->>REC: Auto-detect MT/MX format via StrategyFactory
        REC->>REC: Parse status reply → CanonicalStatusAdvice
        REC->>DB: Save inbound message to SWIFT_MESSAGE
        REC->>DB: Find instruction by tradeRef
        alt Status = MATCHED
            REC->>DB: Update status → MATCHED
            REC->>DB: Update BondHolding position (authoritative)
            REC->>DB: Append SecurityMovement audit entry (CREDIT/DEBIT)
            REC->>DB: Audit log: SETTLEMENT_MATCHED
        else Status = FAILED
            REC->>DB: Update status → FAILED
            REC->>DB: Audit log: SETTLEMENT_FAILED
        else Status = PENDING
            REC->>DB: Audit log: SETTLEMENT_PENDING
        else Status = UNKNOWN (unparseable)
            REC->>DB: Audit log: SETTLEMENT_STATUS_UNKNOWN
            REC--)REC: Webhook alert (HIGH severity)
            Note over REC: Instruction status unchanged,<br/>requires manual review
        end
    end

    opt Manual retry (FAILED instructions)
        Trader->>UI: Click "Retry" button
        UI->>API: POST /api/settlement/{tradeRef}/retry
        API->>SVC: manualRetry(tradeRef)
        SVC->>DB: Reset status → PENDING, retryCount → 0
        SVC--)ASYNC: processSettlementAsync(tradeRef)
        API-->>UI: 202 Accepted (status=PENDING)
    end

    Trader->>UI: Check settlement status
    UI->>API: GET /api/settlement/{tradeRef}
    API->>DB: Query instruction
    DB-->>API: Instruction (status=MATCHED)
    API-->>UI: Settlement details + status
```

### Component Architecture

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
  "sqlplus settlement/settlement123@//localhost:1521/XEPDB1" < db/schema.sql
```

> **Note:** When using Docker Compose, the `db/` directory is mounted to `/docker-entrypoint-initdb.d` so the schema is automatically applied on first container startup.

### 5. Verify deployment

```bash
# Check MQ connectivity
curl http://localhost:9080/settlement/api/mq/health

# Submit a settlement instruction (MT format, default)
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

# Submit using MX (ISO 20022) format
curl -X POST http://localhost:9080/settlement/api/settlement \
  -H "Content-Type: application/json" \
  -d '{
    "isin": "DE0001102580",
    "quantity": 50000,
    "direction": "BUY",
    "counterparty": "Goldman Sachs",
    "bicCode": "GOLDUS33XXX",
    "accountId": "ACC-001",
    "settlementDate": "2026-06-15",
    "preferredStandard": "MX"
  }'

# Test MDB message delivery (MT548 reply)
curl -X POST "http://localhost:9080/settlement/api/mq/test-mdb?correlationId=TEST-001"

# Test MDB with MX (sese.024) reply
curl -X POST "http://localhost:9080/settlement/api/mq/test-mdb?correlationId=TR-XXX&standard=MX&status=matched"

# Check application health
curl http://localhost:9080/settlement/api/holdings
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
SELECT TRADE_REF, STATUS, PREFERRED_STANDARD, ISIN FROM SETTLEMENT_INSTRUCTION ORDER BY CREATED_AT DESC;
SELECT * FROM BOND_HOLDING;
SELECT TRADE_REF, EVENT_TYPE, DETAIL FROM AUDIT_LOG ORDER BY CREATED_AT DESC;

-- SWIFT messages (both MT and MX)
SELECT TRADE_REF, MESSAGE_STANDARD, MESSAGE_TYPE, DIRECTION, PARSED_STATUS
FROM SWIFT_MESSAGE ORDER BY CREATED_AT DESC FETCH FIRST 20 ROWS ONLY;

-- Message type registry
SELECT MESSAGE_TYPE, MESSAGE_STANDARD, DESCRIPTION, CATEGORY FROM MESSAGE_TYPE_REGISTRY;

-- Position journal: recent movements
SELECT ACCOUNT_ID, ISIN, MOVEMENT_TYPE, QUANTITY, BALANCE_AFTER, TRADE_REF, CREATED_AT
FROM SECURITY_MOVEMENT ORDER BY CREATED_AT DESC FETCH FIRST 20 ROWS ONLY;

-- EOD snapshots (reconciliation baseline)
SELECT BUSINESS_DATE, ACCOUNT_ID, ISIN, BALANCE FROM EOD_POSITION_SNAPSHOT
ORDER BY BUSINESS_DATE DESC, ACCOUNT_ID, ISIN FETCH FIRST 20 ROWS ONLY;

-- Reconciliation: verify position == EOD snapshot + today's movements
SELECT h.ACCOUNT_ID, h.ISIN,
       h.QUANTITY AS POSITION,
       NVL(e.BALANCE, 0) AS EOD_BALANCE,
       NVL(SUM(CASE WHEN m.MOVEMENT_TYPE = 'CREDIT' THEN m.QUANTITY ELSE -m.QUANTITY END), 0) AS NET_MOVEMENT,
       NVL(e.BALANCE, 0) + NVL(SUM(CASE WHEN m.MOVEMENT_TYPE = 'CREDIT' THEN m.QUANTITY ELSE -m.QUANTITY END), 0) AS EXPECTED
FROM BOND_HOLDING h
LEFT JOIN EOD_POSITION_SNAPSHOT e
  ON h.ACCOUNT_ID = e.ACCOUNT_ID AND h.ISIN = e.ISIN
  AND e.BUSINESS_DATE = (SELECT MAX(BUSINESS_DATE) FROM EOD_POSITION_SNAPSHOT)
LEFT JOIN SECURITY_MOVEMENT m
  ON h.ACCOUNT_ID = m.ACCOUNT_ID AND h.ISIN = m.ISIN
  AND m.CREATED_AT > (SELECT MAX(BUSINESS_DATE) + 1 FROM EOD_POSITION_SNAPSHOT)
GROUP BY h.ACCOUNT_ID, h.ISIN, h.QUANTITY, e.BALANCE;
```

## Modules

| Module | Description |
|--------|-------------|
| `settlement-common` | Shared library: ServiceRegistry bridge + Canonical Data Model (`CanonicalSettlement`, `CanonicalStatusAdvice`, etc.) |
| `settlement-backend` | Spring MVC WAR with REST API, MT/MX strategy pattern, JMS sender, reconciliation |
| `settlement-ejb` | Message-Driven EJB for SWIFT reply processing (MT548 / sese.024) |
| `settlement-ear` | Enterprise Archive packaging for WebSphere deployment |
| `settlement-frontend` | Vue.js 3 single-page application |

## Project Structure

```
my-bond-settlement-system/
├── pom.xml                          # Parent POM (dependency management)
├── docker-compose.yml               # Local dev environment
├── db/
│   └── schema.sql                   # Oracle DDL (all tables + constraints + indexes)
├── docker/
│   ├── liberty/
│   │   ├── server.xml               # Liberty config (MQ RA, JMS, activation spec)
│   │   ├── Dockerfile               # Liberty image with MQ Jakarta RA
│   │   └── jdbc/                    # JDBC driver (gitignored)
│   └── mq/
│       └── config.mqsc              # MQ queue definitions
├── settlement-common/               # Shared library
│   └── src/main/java/com/settlement/
│       ├── bridge/                          # ServiceRegistry cross-module bridge
│       └── canonical/                       # Canonical Data Model (format-independent)
│           ├── CanonicalSettlement.java      # Settlement instruction model
│           ├── CanonicalStatusAdvice.java    # Status reply model
│           ├── PartyInfo.java               # Party information (BIC, LEI, account)
│           ├── SettlementDirection.java      # RECEIVE / DELIVER
│           └── PaymentType.java             # AGAINST_PAYMENT / FREE_OF_PAYMENT
├── settlement-backend/              # Spring MVC WAR module
│   └── src/main/java/com/settlement/
│       ├── config/
│       │   ├── MqClientConfig.java          # JNDI JMS ConnectionFactory + JmsTemplate (XA)
│       │   └── ServiceRegistryInitializer.java  # Registers Spring beans for MDB access
│       ├── controller/
│       │   ├── SettlementController.java    # REST API
│       │   └── MqConnectivityController.java # MQ health & MDB test endpoints (MT + MX)
│       ├── strategy/                        # MT/MX Strategy Pattern
│       │   ├── SwiftMessageStrategy.java    # Strategy interface (build + parse)
│       │   ├── MtStrategy.java              # MT541 build / MT548 parse
│       │   ├── MxStrategy.java              # sese.023 build / sese.024 parse
│       │   ├── SwiftMessageStrategyFactory.java # Strategy resolver + auto-detect
│       │   └── CanonicalMapper.java         # Entity ↔ Canonical mapping
│       ├── service/                         # Business logic
│       ├── jms/SwiftMessageSender.java      # JMS sender (MT/MX agnostic)
│       ├── reconcile/
│       │   ├── ReconciliationService.java       # MT548/sese.024 processing & position updates
│       │   └── PositionReconciliationService.java # Incremental & daily-close reconciliation
│       ├── dao/                             # Data access (JPA)
│       ├── entity/                          # JPA entities (incl. SwiftMessage)
│       └── dto/                             # Request/Response DTOs
├── settlement-ejb/                  # EJB module
│   └── src/main/
│       ├── java/com/settlement/ejb/
│       │   └── SwiftReplyMDB.java           # Message-Driven Bean (MT548 receiver)
│       └── resources/META-INF/
│           ├── ejb-jar.xml                  # EJB deployment descriptor
│           └── ibm-ejb-jar-bnd.xml          # Liberty MDB activation spec binding
├── settlement-ear/                  # EAR packaging
└── settlement-frontend/             # Vue.js 3 frontend
```

## Database Schema

### SWIFT Message Storage (Normalised)

Messages are stored in a dedicated `SWIFT_MESSAGE` table, decoupled from the business entity `SETTLEMENT_INSTRUCTION`. This supports both MT (FIN) and MX (ISO 20022) formats and enables unlimited message types without schema changes.

| Table | Purpose |
|-------|---------|
| `SETTLEMENT_INSTRUCTION` | Business entity — format-agnostic, stores `PREFERRED_STANDARD` (MT/MX) |
| `SWIFT_MESSAGE` | All outbound/inbound SWIFT messages (MT541, MT548, sese.023, sese.024, etc.) with raw payload |
| `MESSAGE_TYPE_REGISTRY` | Metadata registry mapping MT ↔ MX equivalents and categories |

**Registered message types:**

| Type | Standard | Category | Equivalent |
|------|----------|----------|-----------|
| MT541 | MT | SETTLEMENT | sese.023.001.09 |
| MT548 | MT | STATUS | sese.024.001.10 |
| sese.023.001.09 | MX | SETTLEMENT | MT541 |
| sese.024.001.10 | MX | STATUS | MT548 |

## Position Management (CSD-Style)

Bond positions follow the architecture used by large Central Securities Depositories (CSDs like Euroclear, Clearstream, DTCC):

| Table | Role | Mutability |
|-------|------|------------|
| `BOND_HOLDING` | **Authoritative position** — the source of truth for current balances | Updated transactionally on each settlement |
| `SECURITY_MOVEMENT` | Immutable audit journal — records every position change | Append-only (INSERT) |
| `EOD_POSITION_SNAPSHOT` | End-of-day per-position snapshot — reconciliation baseline | Inserted once per business day |

### How It Works

When a settlement is confirmed (MT548 MATCHED or sese.024 MTCHD), the system performs two writes in a single transaction:

1. **Update `BondHolding`** — the authoritative position for the `(account, isin)` pair, protected by a pessimistic lock (`SELECT FOR UPDATE`) to serialise concurrent updates
2. **Append a `SecurityMovement`** — an immutable audit entry recording the CREDIT (BUY) or DEBIT (SELL), the quantity, and the resulting balance (`balanceAfter`)

```
BUY  → BondHolding(qty=2500) + SecurityMovement(CREDIT, qty=1000, balanceAfter=2500)
SELL → BondHolding(qty=2000) + SecurityMovement(DEBIT,  qty=500,  balanceAfter=2000)
```

Consistency between Position and Movement is guaranteed by the transaction boundary — no post-hoc SUM verification on the hot path. Drift detection is handled asynchronously by reconciliation.

### Concurrency Safety

| Layer | Mechanism | Purpose |
|-------|-----------|---------|
| Application | `@Transactional` | Atomic read-check-write |
| Application | `SELECT ... FOR UPDATE` on `BOND_HOLDING` | Serialise concurrent updates to same (account, isin) |
| Database | `UNIQUE(ACCOUNT_ID, ISIN)` on `BOND_HOLDING` | Prevent duplicate position rows |
| Database | `CHECK (QUANTITY >= 0)` on `BOND_HOLDING` | Prevent negative balances |
| Database | `CHECK (BALANCE_AFTER >= 0)` on `SECURITY_MOVEMENT` | Guarantee journal consistency |

### Position Reconciliation (Snapshot-Based)

Reconciliation follows the CSD industry pattern — verifying position integrity against bounded movement summation rather than scanning all historical data:

```
current_position == eod_snapshot(prev_day) + SUM(movements since snapshot)
```

| Mode | Trigger | Scope | SUM Range |
|------|---------|-------|-----------|
| **Incremental** | On-demand API call | Positions changed since last EOD | One day (bounded) |
| **Daily close** | End-of-day API call | All positions | One day (bounded) |
| **Bootstrap** | First-ever run (no snapshot exists) | All positions | All-time (one-time only) |

**Incremental** via `POST /api/positions/reconcile`:
- Finds the latest `EOD_POSITION_SNAPSHOT` business date as the baseline
- Queries `SECURITY_MOVEMENT` for distinct `(account, isin)` pairs with movements after that date
- For each changed position, verifies: `position == eod_balance + SUM(movements since EOD)`
- SUM is always bounded to at most one day of data — **O(daily_volume)** not O(all_history)
- If no EOD snapshot exists, falls back to bootstrap (one-time full scan)

**Daily close** via `POST /api/positions/daily-close`:
- Full verification of all positions against previous EOD snapshot + today's movements
- Persists per-position `EOD_POSITION_SNAPSHOT` records as the new baseline
- Also saves a `RECONCILIATION_SNAPSHOT` run record
- Typically triggered by an external job scheduler (cron, Autosys, etc.) at COB
- Idempotent: refuses to run if snapshots already exist for the business date

**Data lifecycle:**

```
Day 1 close: Snap EOD positions → EOD_POSITION_SNAPSHOT (date=Day1)
Day 2:       Settlements create movements (bounded to Day 2 only)
Day 2 recon: position == EOD(Day1) + SUM(Day2 movements)  ← bounded query
Day 2 close: Snap EOD positions → EOD_POSITION_SNAPSHOT (date=Day2)
```

**Response example:**

```json
{
  "reconciledAt": "2026-05-11T22:08:00",
  "type": "INCREMENTAL",
  "totalPositions": 3,
  "discrepancyCount": 1,
  "consistent": false,
  "discrepancies": [
    {
      "accountId": "ACC-001",
      "isin": "US0378331005",
      "cachedBalance": 1000000.00,
      "ledgerBalance": 900000.00,
      "difference": 100000.00
    }
  ]
}
```

## Messaging Architecture

### Canonical Data Model

The message layer is fully decoupled from JPA entities via a Canonical Data Model:

```
Entity → CanonicalMapper → CanonicalSettlement → Strategy → SWIFT Message (MT/MX)
SWIFT Message → Strategy → CanonicalStatusAdvice → Service → Entity
```

| Component | Role |
|-----------|------|
| `CanonicalSettlement` | Format-independent settlement instruction (superset of MT/MX fields) |
| `CanonicalStatusAdvice` | Format-independent status reply (outcome, statusCode, reason) |
| `CanonicalMapper` | Maps JPA Entity ↔ Canonical (the only class that knows both worlds) |
| `SwiftMessageStrategy` | Interface for building/parsing messages (MT or MX) |
| `SwiftMessageStrategyFactory` | Resolves strategy by standard; auto-detects from raw payload |

### Outbound (Send MT541 / sese.023)

```
SettlementService → CanonicalMapper → Strategy.build() → SWIFT_MESSAGE (DB)
    → SettlementXaExecutor → JmsTemplate.send(SWIFT.SEND.QUEUE) → SWIFT Gateway
```

### Inbound (Receive MT548 / sese.024)

```
SWIFT Gateway → SWIFT.REPLY.QUEUE → JCA Activation Spec (container-managed)
    → SwiftReplyMDB.onMessage()
    → ServiceRegistry.lookup("reconciliationService")
    → StrategyFactory.detectStrategy(rawMessage)  [auto-detect MT/MX]
    → Strategy.parseStatusReply() → CanonicalStatusAdvice
    → ReconciliationService updates status + holdings
```

### Cross-Module Bridge

The WAR (Spring) and EJB modules run in separate classloaders within the EAR.
`ServiceRegistry` in `settlement-common` (EAR lib/) provides a thread-safe bridge:
- **WAR startup**: `ServiceRegistryInitializer` registers Spring-managed `ReconciliationService`
- **MDB runtime**: `SwiftReplyMDB` retrieves it via `ServiceRegistry.lookup()` + reflection

### Transaction Management (JTA + XA Two-Phase Commit)

The system uses **JTA with XA two-phase commit** to guarantee atomic consistency between Oracle Database and IBM MQ. Liberty's JTA transaction manager coordinates both resources.

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

1. **XA consistency (outbound)** — `AsyncSettlementProcessor.executeSettlement()` updates Oracle AND sends MT541/sese.023 to MQ in the same `@Transactional` method. Using a container-managed JNDI `ConnectionFactory` (`jms/SwiftQueueCF`), both resources are enlisted in the JTA transaction. If either fails, both roll back atomically.
2. **MDB compatibility (inbound)** — MDB runs in a container-managed JTA transaction. Spring's `@Transactional` on `ReconciliationService.processSwiftReply()` joins this existing JTA transaction instead of attempting a local `Connection.commit()` (which would cause `DSRA9350E`).
3. **Hibernate JTA platform** — configured via `hibernate.transaction.jta.platform` = `WebSphereLibertyJtaPlatform`.

**XA Transaction Timeout:**

Configured in `server.xml` with explicit values:

| Setting | Value | Description |
|---------|-------|-------------|
| `totalTranLifetimeTimeout` | 30s | Max lifetime for XA global transaction |
| `propogatedOrBMTTranLifetimeTimeout` | 30s | Max lifetime for propagated / BMT transactions |
| `clientInactivityTimeout` | 10s | Max idle time before transaction times out |
| `LPSHeuristicCompletion` | ROLLBACK | Heuristic decision on failure: rollback for safety |

Key configuration:

| File | Setting | Purpose |
|------|---------|---------|
| `applicationContext.xml` | `JtaTransactionManager` | Spring delegates to Liberty's JTA |
| `applicationContext.xml` | `jtaDataSource` (not `dataSource`) | DB connection enlisted in JTA |
| `MqClientConfig.java` | `InitialContext.doLookup("jms/SwiftQueueCF")` | MQ connection enlisted in JTA |
| `server.xml` | `<jmsQueueConnectionFactory>` | Container-managed XA connection factory |
| `server.xml` | `<transaction>` | XA timeout & heuristic config |

### Async Processing & Retry

Settlement submission follows a **two-phase async pattern** to minimize client latency:

**Phase 1 (sync, fast):** HTTP request saves the instruction with `PENDING` status to Oracle (no MQ involved), returns `202 Accepted` immediately.

**Phase 2 (async, XA):** A thread from `settlementExecutor` (a bounded `ThreadPoolTaskExecutor`) runs `AsyncSettlementProcessor.doProcess()` which performs the XA transaction (DB update + JMS send). Each `@Transactional` call inside starts a fresh JTA transaction via Spring's `JtaTransactionManager`; no JTA context is inherited from the caller.

**Thread pools (configured in `applicationContext.xml`):**

| Bean | Class | Core / Max / Queue | Usage |
|------|-------|--------------------|-------|
| `settlementExecutor` | `ThreadPoolTaskExecutor` | 5 / 20 / 100 | Settlement retry processing; `CallerRunsPolicy` backpressure when full |
| `alertExecutor` | `ThreadPoolTaskExecutor` | 2 / 5 / 50 | Webhook alert HTTP calls; `DiscardPolicy` (alert loss is acceptable) |

**Retry on failure:**

| Aspect | Detail |
|--------|--------|
| Strategy | Exponential backoff: 2s → 4s → 8s (max 30s) |
| Max attempts | 3 |
| Retry trigger | Inline in the async thread (no DB polling needed) |
| Intermediate state | `RETRYING` with `failureReason` and `retryCount` recorded (not yet exhausted) |
| Final state | `FAILED` with `failureReason` and `retryCount` recorded (all retries exhausted) |
| Webhook alert | Submitted to `alertExecutor` when all retries exhausted (configurable URL) |
| Manual retry | Traders can retry via `POST /api/settlement/{tradeRef}/retry` |
| Crash recovery | Scheduler scans for orphaned `SUBMITTING` (stuck > 5min) and `RETRYING`/`PENDING` every 120s |

**Status flow:**

```
PENDING → SUBMITTING → SENT → MATCHED          (happy path)
PENDING → SUBMITTING → RETRYING → ... → FAILED (all 3 retries failed)
PENDING → SUBMITTING → RETRYING → SENT         (succeeded on retry)
FAILED  → PENDING → SUBMITTING → SENT          (manual retry success)
SENT    → (status unchanged)                    (MT548/sese.024 unparseable → UNKNOWN, needs manual review)
```

> **Note:** During retries, the status is `RETRYING` (not `FAILED`) so API consumers can distinguish between a transient retry and a terminal failure.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/holdings` | List all bond holdings (cached balances) |
| `GET` | `/api/holdings/{accountId}` | Get holdings for account |
| `POST` | `/api/settlement` | Submit settlement instruction (supports `preferredStandard`: MT/MX) |
| `GET` | `/api/settlement/{tradeRef}` | Get instruction status (includes `retryCount`, `failureReason`) |
| `GET` | `/api/settlement?page=&size=` | List settlement instructions (paginated) |
| `POST` | `/api/settlement/{tradeRef}/retry` | Manual retry for FAILED instructions |
| `POST` | `/api/positions/reconcile` | Incremental reconciliation (only changed positions since last snapshot) |
| `POST` | `/api/positions/daily-close` | Daily close: full reconciliation + persist snapshot as new baseline |
| `GET` | `/api/mq/health` | IBM MQ connection health check |
| `POST` | `/api/mq/test-mdb` | Send test reply (MT548 or sese.024) to verify MDB processing |
| `GET` | `/api/mq/stats` | MDB + reconciliation metrics + live queue depth/consumer status |

**Settlement submission** (`POST /api/settlement`):

```json
{
  "isin": "US0378331005",
  "quantity": 1000,
  "direction": "BUY",
  "counterparty": "Deutsche Bank",
  "bicCode": "DEUTDEFFXXX",
  "accountId": "ACC-001",
  "settlementDate": "2026-06-01",
  "preferredStandard": "MX"
}
```

The `preferredStandard` field accepts `"MT"` (default) or `"MX"`. MT generates MT541 (FIN), MX generates sese.023.001.09 (ISO 20022 XML).

**MDB test** (`POST /api/mq/test-mdb`):

| Parameter | Default | Description |
|-----------|---------|-------------|
| `correlationId` | `TEST-MDB-001` | Trade reference to match against |
| `standard` | `MT` | `MT` (sends MT548) or `MX` (sends sese.024.001.10) |
| `status` | matched | MT: `MATC`, `REJT`, `NMAT`, `PDNG`. MX: `matched`, `rejected`, `unmatched`, `pending` |

Example MX test:

```bash
curl -X POST "http://localhost:9080/settlement/api/mq/test-mdb?correlationId=TR-XXX&standard=MX&status=matched"
```

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

Application properties (`settlement.properties`):

| Property | Default | Description |
|----------|---------|-------------|
| `settlement.alert.webhook.enabled` | `false` | Enable webhook alerts for retry exhaustion and unparseable MT548 |
| `settlement.alert.webhook.url` | (empty) | Webhook URL (Slack, PagerDuty, DingTalk, etc.) |
| `mq.monitor.host` | `${MQ_HOST:localhost}` | MQ host for PCF admin queries |
| `mq.monitor.port` | `${MQ_PORT:1414}` | MQ port for PCF admin queries |
| `mq.monitor.channel` | `DEV.ADMIN.SVRCONN` | MQ admin channel for monitoring (requires PCF authority) |
| `mq.monitor.queueManager` | `SETTLEMENT_QM` | Queue manager name |
| `mq.monitor.user` | `admin` | MQ admin user for PCF queries |
| `mq.monitor.password` | `passw0rd` | MQ admin password |

## Liberty Server Configuration

Key server.xml elements for MDB activation:

| Element | Purpose |
|---------|---------|
| `<resourceAdapter id="mqJmsRa">` | IBM MQ Jakarta Resource Adapter |
| `<jmsQueueConnectionFactory>` | Container-managed MQ connection factory |
| `<jmsQueue>` | SWIFT send/reply queue definitions |
| `<jmsActivationSpec id="jms/SwiftReplyActivationSpec">` | MDB activation spec bound to SWIFT.REPLY.QUEUE |

**MDB Concurrency:**

The activation spec configures `maxPoolDepth="5"`, which controls the maximum number of concurrent MDB instances consuming from `SWIFT.REPLY.QUEUE`. This is appropriate for bond settlement where reliability and ordering take priority over throughput. Increase this value if queue depth (`GET /api/mq/stats`) shows sustained backlog.

**MQ Monitoring:**

`GET /api/mq/stats` provides live operational visibility:
- **MDB counters** — total received/success/failed messages with timestamps (in-memory, reset on restart)
- **Reconciliation counters** — MT548/sese.024 processing outcomes: matched/failed/pending/unknown/unmatched (in-memory, reset on restart). A non-zero `totalUnknown` indicates messages that could not be parsed and require manual review
- **Queue status** — current depth, max depth, open input/output handles, last put/get times (live from MQ via PCF admin commands)

## Monitoring (Prometheus + Grafana)

The project includes a full observability stack via Docker:

| Service | URL | Credentials |
|---------|-----|-------------|
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | (none) |
| Liberty Metrics | http://localhost:9080/metrics | (none) |
| MQ Metrics | http://localhost:9157/metrics | (none) |

**Architecture:**

```
┌─────────────┐     /metrics      ┌────────────┐     scrape     ┌─────────┐
│   Liberty   │──────────────────▶│ Prometheus │◀───────────────│  MQ     │
│ (mpMetrics) │                   │            │                │ Exporter│
└─────────────┘                   └─────┬──────┘                └────┬────┘
                                        │                            │
                                        ▼                            │
                                  ┌──────────┐              ┌───────▼───────┐
                                  │ Grafana  │              │    IBM MQ     │
                                  │Dashboard │              │ (PCF queries) │
                                  └──────────┘              └───────────────┘
```

**Pre-configured Grafana Dashboard includes:**
- MQ queue depth (SWIFT.REPLY.QUEUE, SWIFT.SEND.QUEUE)
- Dead Letter Queue depth (with color thresholds)
- MQ consumer count (open input handles)
- Message put/get rates
- Liberty HTTP request rate
- Liberty connection pool usage
- JVM heap memory
- Settlement status breakdown (matched/failed/pending/unknown) — covers both MT548 and sese.024

**Usage:**

```bash
# Start core services only (Oracle + MQ + Liberty)
docker compose up -d

# Start with monitoring stack (adds Prometheus + Grafana)
docker compose --profile monitoring up -d
```

**Liberty MicroProfile Metrics** (`mpMetrics-5.1` + `monitor-1.0`) automatically exposes:
- JVM metrics (heap, GC, threads)
- HTTP servlet request counts and response times
- Connection pool statistics (Oracle DS, MQ CF)
- REST request metrics

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

See the [API Endpoints](#api-endpoints) section above for the complete REST API reference.
