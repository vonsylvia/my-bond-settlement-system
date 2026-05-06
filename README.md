# Bond Settlement System

A complete SWIFT bond settlement system built for IBM WebSphere + Oracle environment.

## Architecture

- **Frontend**: Vue.js 3 + Vite + Axios
- **Backend**: Spring MVC 6 (non-Boot) REST API
- **SWIFT Messaging**: Prowide Core (MT541 send / MT548 receive)
- **Message Queue**: IBM MQ via JMS (Spring JmsTemplate + Message-Driven EJB)
- **Persistence**: Hibernate 6 / JPA 3.1 on Oracle Database
- **Application Server**: IBM WebSphere Liberty (Jakarta EE 10)
- **Packaging**: EAR (WAR + EJB JAR)

## Modules

| Module | Description |
|--------|-------------|
| `settlement-backend` | Spring MVC WAR with REST API, service layer, JMS sender |
| `settlement-ejb` | Message-Driven EJB listening on SWIFT reply queue |
| `settlement-ear` | Enterprise Archive packaging for WebSphere deployment |
| `settlement-frontend` | Vue.js 3 single-page application |

## Flow

1. Trader submits settlement instruction via Vue.js frontend
2. Spring MVC validates JSON request (Bean Validation)
3. Prowide builds MT541 message
4. JmsTemplate sends MT541 to IBM MQ send queue
5. SWIFT Alliance Gateway processes and returns MT548
6. MDB receives MT548 from reply queue
7. ReconciliationService parses MT548 status, updates holdings in Oracle

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 20+
- IBM WebSphere Liberty 24.x
- IBM MQ 9.3+
- Oracle Database 19c+

## Build

```bash
# Backend (produces EAR)
mvn clean package

# Frontend
cd settlement-frontend
npm install
npm run build
```

## Database Setup

Run the DDL script against your Oracle instance:

```bash
sqlplus user/pass@//host:1521/service @db/V1__create_schema.sql
```

## WebSphere Configuration

Configure the following resources in WebSphere Admin Console:

1. **JDBC DataSource** (`jdbc/OracleDS`) - Oracle connection pool
2. **JMS Queue Connection Factory** (`jms/SwiftQueueCF`) - IBM MQ connection
3. **JMS Queue** (`jms/SwiftSendQueue`) - SWIFT send queue
4. **JMS Queue** (`jms/SwiftReplyQueue`) - SWIFT reply queue
5. **JMS Activation Specification** (`jms/SwiftReplyActivationSpec`) - MDB trigger

## Deployment

Deploy `settlement-ear/target/bond-settlement.ear` to WebSphere Liberty.

## API Documentation

See [docs/openapi.yaml](docs/openapi.yaml) for the full OpenAPI 3.0 specification.
