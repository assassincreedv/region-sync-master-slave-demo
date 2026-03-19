# Multi-Region Master-Slave Sync Demo

A demonstration project showcasing **multi-region data synchronization** using a **master-slave replication** pattern. Built with Spring Boot.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Multi-Region Architecture                        │
│                                                                         │
│  ┌─────────────────────┐     Push Sync      ┌─────────────────────┐    │
│  │   Region 1 (MASTER)  │ ──────────────────► │  Region 2 (SLAVE)   │    │
│  │                      │                     │                      │    │
│  │  ┌────────────────┐  │     Push Sync      │  ┌────────────────┐  │    │
│  │  │  Data Store    │  │ ──────────────┐    │  │  Data Store    │  │    │
│  │  │  (Read/Write)  │  │               │    │  │  (Read Only)   │  │    │
│  │  └────────────────┘  │               │    │  └────────────────┘  │    │
│  │                      │               │    │                      │    │
│  │  ┌────────────────┐  │  ◄─Pull Sync  │    │  ┌────────────────┐  │    │
│  │  │  Event Log     │  │               │    │  │  Health Check  │  │    │
│  │  └────────────────┘  │               │    │  └────────────────┘  │    │
│  └─────────────────────┘               │    └─────────────────────┘    │
│                                         │                               │
│                                         │    ┌─────────────────────┐    │
│                                         └──► │  Region 3 (SLAVE)   │    │
│                                              │                      │    │
│                                              │  ┌────────────────┐  │    │
│                                              │  │  Data Store    │  │    │
│                                              │  │  (Read Only)   │  │    │
│                                              │  └────────────────┘  │    │
│                                              │                      │    │
│                                              │  ┌────────────────┐  │    │
│                                              │  │  Health Check  │  │    │
│                                              │  └────────────────┘  │    │
│                                              └─────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

## Key Features

- **Master-Slave Replication**: Single master handles all writes; slaves serve reads
- **Dual Sync Mechanism**:
  - **Push-based**: Master pushes changes to slaves immediately after writes (low latency)
  - **Pull-based**: Slaves periodically poll master for missed events (reliability)
- **Version-based Conflict Resolution**: Higher version wins; same version uses last-write-wins
- **Automatic Failover**: Slaves detect master failure and self-promote to master
- **Health Monitoring**: Continuous health checks between all nodes
- **Event Sourcing**: All changes are captured as ordered events for reliable replication

## Project Structure

```
region-sync-master-slave-demo/
├── pom.xml                          # Maven build configuration
├── Dockerfile                       # Docker image definition
├── docker-compose.yml               # Multi-region deployment simulation
├── src/
│   ├── main/java/com/example/regionsync/
│   │   ├── RegionSyncApplication.java    # Spring Boot entry point
│   │   ├── config/
│   │   │   ├── RegionConfig.java         # Region configuration properties
│   │   │   └── WebConfig.java            # Web/REST configuration
│   │   ├── controller/
│   │   │   ├── DataController.java       # CRUD REST API
│   │   │   └── SyncController.java       # Internal sync API
│   │   ├── model/
│   │   │   ├── DataItem.java             # Core data model
│   │   │   ├── SyncEvent.java            # Sync replication event
│   │   │   └── RegionInfo.java           # Region status model
│   │   ├── service/
│   │   │   ├── DataService.java          # Business logic for CRUD
│   │   │   ├── SyncService.java          # Sync replication logic
│   │   │   └── FailoverService.java      # Health check & failover
│   │   └── store/
│   │       └── InMemoryDataStore.java    # Thread-safe in-memory storage
│   └── main/resources/
│       └── application.yml               # Application configuration
└── src/test/java/com/example/regionsync/
    ├── InMemoryDataStoreTest.java        # Data store unit tests
    └── RegionSyncApplicationTests.java   # Spring Boot context test
```

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose (for multi-region simulation)

### Option 1: Run with Docker Compose (Recommended)

This starts 3 region nodes: 1 master + 2 slaves.

```bash
# Build and start all regions
docker-compose up --build

# Regions will be available at:
#   Master:  http://localhost:8081
#   Slave 1: http://localhost:8082
#   Slave 2: http://localhost:8083
```

### Option 2: Run Locally with Maven

```bash
# Build the project
mvn clean package

# Terminal 1 - Start Master (port 8081)
java -jar target/region-sync-master-slave-demo-1.0.0-SNAPSHOT.jar \
  --server.port=8081 \
  --region.id=region-master \
  --region.role=MASTER \
  --region.slave-urls=http://localhost:8082,http://localhost:8083

# Terminal 2 - Start Slave 1 (port 8082)
java -jar target/region-sync-master-slave-demo-1.0.0-SNAPSHOT.jar \
  --server.port=8082 \
  --region.id=region-slave-1 \
  --region.role=SLAVE \
  --region.master-url=http://localhost:8081

# Terminal 3 - Start Slave 2 (port 8083)
java -jar target/region-sync-master-slave-demo-1.0.0-SNAPSHOT.jar \
  --server.port=8083 \
  --region.id=region-slave-2 \
  --region.role=SLAVE \
  --region.master-url=http://localhost:8081
```

## API Reference

### Data API (CRUD)

| Method   | Endpoint           | Description                | Allowed On   |
|----------|-------------------|----------------------------|-------------|
| `POST`   | `/api/data`        | Create a new data item     | MASTER only |
| `GET`    | `/api/data`        | List all data items        | ALL nodes   |
| `GET`    | `/api/data/{key}`  | Get a data item by key     | ALL nodes   |
| `PUT`    | `/api/data/{key}`  | Update a data item         | MASTER only |
| `DELETE` | `/api/data/{key}`  | Delete a data item         | MASTER only |

### Sync API (Internal)

| Method | Endpoint                                  | Description                    |
|--------|------------------------------------------|--------------------------------|
| `POST` | `/api/sync/receive`                       | Receive sync event from master |
| `GET`  | `/api/sync/events?afterSequence={seq}`    | Pull events after sequence     |
| `GET`  | `/api/sync/status`                        | Get node status/health         |
| `GET`  | `/api/sync/cluster-status`                | Get cluster-wide status        |

## Usage Examples

### 1. Write Data to Master

```bash
# Create a data item on master
curl -X POST http://localhost:8081/api/data \
  -H "Content-Type: application/json" \
  -d '{"key": "user:1001", "value": "Alice"}'
```

### 2. Read Data from Slaves

```bash
# Read from Slave 1 (data is automatically synced)
curl http://localhost:8082/api/data/user:1001

# Read from Slave 2
curl http://localhost:8083/api/data/user:1001
```

### 3. Update Data

```bash
# Update on master
curl -X PUT http://localhost:8081/api/data/user:1001 \
  -H "Content-Type: application/json" \
  -d '{"value": "Alice Updated"}'
```

### 4. Verify Write Protection on Slaves

```bash
# Try to write to a slave (will be rejected with 403)
curl -X POST http://localhost:8082/api/data \
  -H "Content-Type: application/json" \
  -d '{"key": "user:1002", "value": "Bob"}'
# Response: {"error": "CREATE operation not allowed on SLAVE node..."}
```

### 5. Check Cluster Status

```bash
# Check master status
curl http://localhost:8081/api/sync/cluster-status

# Check slave status
curl http://localhost:8082/api/sync/status
```

### 6. Test Failover

```bash
# Stop the master container
docker stop region-master

# Wait ~15 seconds for health checks to fail

# Check slave 1 - it should have promoted itself to MASTER
curl http://localhost:8082/api/sync/status
# Response should show: "role": "MASTER"

# Now you can write to the promoted slave
curl -X POST http://localhost:8082/api/data \
  -H "Content-Type: application/json" \
  -d '{"key": "user:1002", "value": "Bob"}'
```

## Design Decisions

### Why Dual Sync (Push + Pull)?

- **Push** provides low-latency replication for normal operations
- **Pull** serves as a fallback to catch any events missed during network issues
- This combination ensures both **performance** and **reliability**

### Why Version-based Conflict Resolution?

- Each data item has a monotonically increasing version number
- When syncing, higher version always wins
- For same-version conflicts, last-write-wins (by timestamp) is used
- Simple, deterministic, and easy to reason about

### Why Soft Delete?

- Soft deletes are replicated as normal sync events
- Prevents "resurrection" of deleted items during sync
- Allows audit trail of all operations

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `region.id` | `region-1` | Unique identifier for this node |
| `region.role` | `MASTER` | Node role: `MASTER` or `SLAVE` |
| `region.master-url` | (empty) | URL of master node (for slaves) |
| `region.slave-urls` | (empty) | Comma-separated slave URLs (for master) |
| `region.sync-interval-ms` | `3000` | Pull sync interval (ms) |
| `region.health-check-interval-ms` | `5000` | Health check interval (ms) |
| `region.health-check-timeout-ms` | `2000` | Health check timeout (ms) |
| `region.max-health-check-failures` | `3` | Failures before failover |

## Running Tests

```bash
mvn test
```

## Production Considerations

This is a **demo project** for learning purposes. For production use, consider:

- Replace in-memory store with a real database (e.g., PostgreSQL, MySQL)
- Use a message queue (e.g., Kafka, RabbitMQ) for reliable event replication
- Implement proper consensus protocol for failover (e.g., Raft)
- Add authentication/authorization for sync endpoints
- Implement data encryption in transit (TLS)
- Add metrics and monitoring (Prometheus, Grafana)
- Implement proper retry logic with exponential backoff
- Handle network partitions (split-brain scenarios)
