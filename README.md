# Multi-Region 按数据归属划分 Master Demo

基于 **"每条数据只有一个 Master"** 思路的多 Region 数据同步 Demo，彻底避免冲突问题。

## 技术栈

| 组件 | 技术 | 用途 |
|---|---|---|
| 应用框架 | Spring Boot 3.2.4 (Java 17) | REST API + 业务逻辑 |
| 数据库 | MySQL 8.0 (每 Region 独立) | 持久化存储 Company + SyncEventLog |
| 消息队列 | Apache Kafka (Confluent 7.5) | 跨区同步事件传递 |
| CDC | Debezium 2.5 | MySQL binlog → Kafka (审计/监控) |
| ORM | Spring Data JPA / Hibernate | 数据库访问层 |

## 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                    Shared Infrastructure                         │
│  ┌────────────┐  ┌────────────┐  ┌───────────────────────────┐  │
│  │ Zookeeper  │  │   Kafka    │  │   Debezium Connect        │  │
│  │ :2181      │  │   :9092    │  │   (CDC: MySQL → Kafka)    │  │
│  └────────────┘  └────────────┘  │   :8083                   │  │
│                                  └───────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘

  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
  │   NA Region      │  │   EU Region      │  │   CN Region      │
  │  ┌────────────┐  │  │  ┌────────────┐  │  │  ┌────────────┐  │
  │  │ App :8081  │  │  │  │ App :8082  │  │  │  │ App :8084  │  │
  │  │ (Master:   │  │  │  │ (Master:   │  │  │  │ (Master:   │  │
  │  │  SysConfig │  │  │  │  EU Jobs   │  │  │  │  CN Jobs   │  │
  │  │  NA Jobs)  │  │  │  │  EU Talent)│  │  │  │  CN Talent)│  │
  │  ├────────────┤  │  │  ├────────────┤  │  │  ├────────────┤  │
  │  │ MySQL-NA   │  │  │  │ MySQL-EU   │  │  │  │ MySQL-CN   │  │
  │  │ :3306      │  │  │  │ :3307      │  │  │  │ :3308      │  │
  │  └────────────┘  │  │  └────────────┘  │  │  └────────────┘  │
  └──────────────────┘  └──────────────────┘  └──────────────────┘
```

## 同步流程

```
  Master Region 写入                    Slave Region 同步
  ─────────────────                    ────────────────────

  1. REST API 接收请求
         │
  2. 写入 MySQL                        4. Kafka Consumer 收到事件
     ├─ company 表                         │
     └─ sync_event_log 表             5. 版本比较（高版本胜）
         │                                │
  3. KafkaProducer 发送事件            6. 写入本地 MySQL
     → Kafka Topic                        ├─ company 表
       "region-sync-company"              └─ sync_event_log 表 (APPLIED)
         │
  ─ ─ ─ Debezium CDC ─ ─ ─ ─ ─ ─
  Debezium 监听 binlog
  → Kafka Topic (审计/监控)
    "dbserver-{region}.region_db.company"
```

## 数据库表结构

### company 表（业务数据）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | VARCHAR(64) | 主键 (UUID) |
| `biz_key` | VARCHAR(128) | 业务唯一键 (跨区同步标识) |
| `name` | VARCHAR(256) | 公司名称 |
| `address` | VARCHAR(512) | 地址 |
| `industry` | VARCHAR(128) | 行业 |
| `category` | VARCHAR(32) | 数据类别 (JOB/TALENT/SYSTEM_CONFIG等) |
| `owner_region` | VARCHAR(16) | 归属 Region (Master) |
| `source_region` | VARCHAR(16) | 最后修改 Region |
| `version` | BIGINT | 版本号 (乐观锁/冲突解决) |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |
| `deleted` | BOOLEAN | 软删除标记 |

### sync_event_log 表（同步事件日志）
| 字段 | 类型 | 说明 |
|---|---|---|
| `event_id` | VARCHAR(64) | 主键 (UUID) |
| `event_type` | VARCHAR(16) | CREATE/UPDATE/DELETE |
| `entity_type` | VARCHAR(32) | COMPANY |
| `entity_id` | VARCHAR(64) | 关联实体 ID |
| `entity_biz_key` | VARCHAR(128) | 关联业务键 |
| `source_region` | VARCHAR(16) | 事件源 Region |
| `target_region` | VARCHAR(16) | 目标 Region |
| `sequence_number` | BIGINT | 序列号 |
| `payload` | TEXT | 事件 JSON 负载 |
| `status` | VARCHAR(16) | SENT/APPLIED/FAILED/SKIPPED |
| `kafka_topic` | VARCHAR(128) | Kafka Topic |
| `kafka_partition` | INT | Kafka Partition |
| `kafka_offset` | BIGINT | Kafka Offset |
| `created_at` | DATETIME | 创建时间 |
| `processed_at` | DATETIME | 处理时间 |
| `error_message` | VARCHAR(1024) | 错误信息 |

## 数据归属规则

| DataCategory | Master 归属 | 同步策略 | 说明 |
|---|---|---|---|
| `SYSTEM_CONFIG` | NA (固定) | Kafka 全量同步 | 系统配置，全局统一管理 |
| `ROLE_PERMISSION` | NA (固定) | Kafka 全量同步 | 权限角色，全局统一管理 |
| `JOB` | 各自 Region | Kafka 同步 | 职位数据，按 Region 归属 |
| `TALENT` | 各自 Region | Kafka 同步 | 人才数据，按 Region 归属 |
| `FINANCE` | 各自 Region | **不同步**（合规要求） | 财务数据，仅本地可见 |

## 快速开始

### 前提条件

- Java 17+
- Maven 3.8+
- Docker & Docker Compose

### 启动全部服务

```bash
# 构建并启动 (MySQL + Kafka + Debezium + 3 Region 应用)
docker-compose up --build

# 节点地址：
#   NA (总部):    http://localhost:8081   MySQL: localhost:3306
#   EU:          http://localhost:8082   MySQL: localhost:3307
#   CN:          http://localhost:8084   MySQL: localhost:3308
#   Kafka:       localhost:29092 (外部) / kafka:9092 (内部)
#   Debezium:    http://localhost:8083
```

### 注册 Debezium Connector（可选，用于 CDC 监控）

```bash
# 等待服务启动后执行
./debezium/register-connectors.sh
```

## API 参考

### Company API（使用真实数据库）

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/company` | 创建 Company（自动路由到 Owner Region） |
| `GET` | `/api/company` | 获取所有 Company |
| `GET` | `/api/company?category=JOB` | 按类别查询 |
| `GET` | `/api/company?ownerRegion=NA` | 按归属 Region 查询 |
| `GET` | `/api/company/{bizKey}` | 按 bizKey 查询 |
| `PUT` | `/api/company/{bizKey}` | 更新 Company（自动路由） |
| `DELETE` | `/api/company/{bizKey}` | 删除 Company（自动路由） |
| `GET` | `/api/company/sync-logs` | 查看同步事件日志 |
| `GET` | `/api/company/sync-logs?status=APPLIED` | 按状态查看日志 |

### Data API（内存存储，兼容旧接口）

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/data` | 创建数据 |
| `GET` | `/api/data/{key}` | 按 key 查询 |
| `PUT` | `/api/data/{key}` | 更新数据 |
| `DELETE` | `/api/data/{key}` | 删除数据 |

### 同步 API

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/sync/status` | 节点状态（含数据库计数） |
| `GET` | `/api/sync/cluster-status` | 集群状态 |

## 使用示例

### 1. 在 NA 创建 Company（写入 MySQL + 发送 Kafka）

```bash
curl -X POST http://localhost:8081/api/company \
  -H "Content-Type: application/json" \
  -d '{
    "bizKey": "na-tech-001",
    "name": "TechCorp",
    "address": "New York, USA",
    "industry": "Technology",
    "category": "JOB",
    "ownerRegion": "NA"
  }'
```

### 2. 在 EU 创建 Company

```bash
curl -X POST http://localhost:8082/api/company \
  -H "Content-Type: application/json" \
  -d '{
    "bizKey": "eu-fin-001",
    "name": "FinanceEU",
    "address": "London, UK",
    "industry": "Finance",
    "category": "JOB",
    "ownerRegion": "EU"
  }'
```

### 3. 在 CN 查看已同步的数据

```bash
# 由 NA 创建的 Company 通过 Kafka 自动同步到 CN
curl http://localhost:8084/api/company/na-tech-001
```

### 4. 查看同步事件日志

```bash
# 查看所有同步日志
curl http://localhost:8081/api/company/sync-logs | jq .

# 查看已应用的同步日志
curl http://localhost:8084/api/company/sync-logs?status=APPLIED | jq .
```

### 5. 创建 FINANCE 数据（不同步，合规要求）

```bash
curl -X POST http://localhost:8084/api/company \
  -H "Content-Type: application/json" \
  -d '{
    "bizKey": "cn-revenue-q1",
    "name": "CN Revenue Q1",
    "address": "Shanghai",
    "industry": "Finance",
    "category": "FINANCE",
    "ownerRegion": "CN"
  }'

# 在 NA 无法看到 CN 的 FINANCE 数据
curl http://localhost:8081/api/company/cn-revenue-q1
# 返回 404
```

### 6. 检查 Debezium CDC 状态

```bash
# 查看所有 connector
curl http://localhost:8083/connectors | jq .

# 查看 NA connector 状态
curl http://localhost:8083/connectors/mysql-connector-na/status | jq .
```

## 运行测试

```bash
mvn test
```

## 项目结构

```
region-sync-master-slave-demo/
├── pom.xml                              # Maven (JPA + MySQL + Kafka)
├── Dockerfile                           # Docker 镜像
├── docker-compose.yml                   # 全套部署 (MySQL×3 + Kafka + Debezium + App×3)
├── debezium/
│   └── register-connectors.sh           # Debezium connector 注册脚本
├── src/main/java/com/example/regionsync/
│   ├── RegionSyncApplication.java
│   ├── config/
│   │   ├── RegionConfig.java            # Region 配置（数据归属映射）
│   │   └── WebConfig.java               # REST 配置
│   ├── controller/
│   │   ├── CompanyController.java       # Company CRUD API（MySQL）
│   │   ├── DataController.java          # 旧接口（内存存储，兼容）
│   │   └── SyncController.java          # 同步状态 API
│   ├── entity/
│   │   ├── Company.java                 # Company JPA 实体
│   │   └── SyncEventLog.java            # 同步事件日志 JPA 实体
│   ├── kafka/
│   │   ├── KafkaProducerService.java    # Kafka 生产者（发布同步事件）
│   │   ├── KafkaConsumerService.java    # Kafka 消费者（接收并写入本地 DB）
│   │   └── DebeziumEventConsumer.java   # Debezium CDC 事件消费（审计/监控）
│   ├── model/
│   │   ├── DataCategory.java            # 数据类别枚举
│   │   ├── DataItem.java                # 内存数据模型
│   │   ├── SyncEvent.java               # 内存同步事件
│   │   └── RegionInfo.java              # Region 状态
│   ├── repository/
│   │   ├── CompanyRepository.java       # Company JPA Repository
│   │   └── SyncEventLogRepository.java  # SyncEventLog JPA Repository
│   ├── service/
│   │   ├── CompanyService.java          # Company 业务逻辑（DB + Kafka）
│   │   ├── DataService.java             # 旧数据服务（内存）
│   │   ├── SyncService.java             # REST 同步服务
│   │   └── RegionWriteRouter.java       # 写操作路由器
│   └── store/
│       └── InMemoryDataStore.java       # 内存存储（保留兼容）
└── src/test/
    ├── java/com/example/regionsync/
    │   ├── InMemoryDataStoreTest.java   # 内存存储测试
    │   └── RegionSyncApplicationTests.java
    └── resources/
        └── application.yml              # 测试配置（H2 + EmbeddedKafka）
```

## 设计要点

### 为什么选择 Kafka 而不是 REST 推送？

| 方面 | REST Push | Kafka |
|---|---|---|
| 可靠性 | 低（网络故障丢失） | 高（持久化 + 重试） |
| 顺序性 | 无保证 | 同一 partition key 有序 |
| 扩展性 | 有限 | 水平扩展 |
| 审计 | 需额外实现 | Topic 天然保留历史 |

### Debezium CDC 的作用

- **审计日志**：通过 binlog 捕获所有数据库变更，不依赖应用层
- **监控**：实时追踪各 Region 的数据库变更
- **兜底机制**：即使应用层 Kafka 发送失败，CDC 仍可捕获变更
- **数据一致性验证**：对比应用层事件和 CDC 事件，确保一致

### FINANCE 数据合规

FINANCE 类数据不生成 Kafka 同步事件，确保：
- 财务数据不跨境传输（模拟 GDPR 等合规要求）
- 仅在本地 MySQL 中可见
