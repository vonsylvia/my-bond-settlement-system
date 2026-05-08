# Bond Settlement System

A complete SWIFT bond settlement system built for IBM WebSphere + Oracle environment.

## Architecture

- **Frontend**: Vue.js 3 + Vite + Axios
- **Backend**: Spring MVC 6 (non-Boot) REST API
- **SWIFT Messaging**: Prowide Core (MT541 send / MT548 receive)
- **Message Queue**: IBM MQ via JMS
  - **Sending**: Spring JmsTemplate with application-managed MQ client connection
  - **Receiving**: Message-Driven EJB (MDB) via JCA activation spec on IBM MQ Resource Adapter
- **Persistence**: Hibernate 6 / JPA 3.1 on Oracle Database
- **Application Server**: IBM WebSphere Liberty (Jakarta EE 10)
- **Packaging**: EAR (WAR + EJB JAR + Common Library)

## Modules

| Module | Description |
|--------|-------------|
| `settlement-common` | Shared library with cross-module service bridge (ServiceRegistry) |
| `settlement-backend` | Spring MVC WAR with REST API, service layer, JMS sender |
| `settlement-ejb` | Message-Driven EJB for SWIFT MT548 reply processing |
| `settlement-ear` | Enterprise Archive packaging for WebSphere deployment |
| `settlement-frontend` | Vue.js 3 single-page application |

## Flow

1. Trader submits settlement instruction via Vue.js frontend
2. Spring MVC validates JSON request (Bean Validation)
3. Prowide builds MT541 message
4. JmsTemplate sends MT541 to IBM MQ send queue
5. SWIFT Alliance Gateway processes and returns MT548
6. **SwiftReplyMDB** (Message-Driven Bean) receives MT548 from reply queue via JCA activation spec
7. MDB retrieves ReconciliationService via ServiceRegistry bridge
8. ReconciliationService parses MT548 status, updates holdings in Oracle

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 20+ (for frontend)
- Docker & Docker Compose (for local development)

### Runtime Stack (provided via Docker)

- IBM WebSphere Liberty 24.x (Jakarta EE 10)
- IBM MQ 9.3+ (Jakarta-compatible Resource Adapter)
- Oracle Database 19c+ (XE edition for dev)

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
│       │   ├── MqClientConfig.java          # JMS connection factory + JmsTemplate
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

### Transaction Management (JTA)

Spring is configured to use **JTA transactions** (`JtaTransactionManager`) instead of local `JpaTransactionManager`. This is required because:

1. **MDB runs in a container-managed JTA transaction** — when the MDB calls `ReconciliationService.processSwiftReply()`, Spring's `@Transactional` must join the existing JTA transaction rather than attempting a local `Connection.commit()` (which would cause `DSRA9350E`)
2. **Hibernate uses `WebSphereLibertyJtaPlatform`** — auto-detected JTA platform for Liberty, configured via `hibernate.transaction.jta.platform`
3. **REST API calls** also use JTA — Liberty provides JTA even for non-EJB contexts, so `@Transactional` on service methods works consistently whether called from HTTP requests or MDB

Key configuration in `applicationContext.xml`:
```xml
<bean id="entityManagerFactory" class="org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean">
    <property name="jtaDataSource" ref="dataSource"/>  <!-- jtaDataSource, not dataSource -->
    <property name="jpaProperties">
        <props>
            <prop key="hibernate.transaction.jta.platform">
                org.hibernate.engine.transaction.jta.platform.internal.WebSphereLibertyJtaPlatform
            </prop>
        </props>
    </property>
</bean>
<bean id="transactionManager" class="org.springframework.transaction.jta.JtaTransactionManager"/>
```

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
