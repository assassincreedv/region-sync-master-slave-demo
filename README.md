# Multi-Region 按数据归属划分 Master Demo

基于 **"每条数据只有一个 Master"** 思路的多 Region 数据同步 Demo，彻底避免冲突问题。

## 架构思路

**核心原则：按数据类型/归属划分 Master，写操作路由到归属地区。**

```
┌──────────────────────────────────────────────────────────┐
│              按数据归属划分 Master                         │
├──────────────┬───────────────┬───────────────────────────┤
│ 数据类型      │ Master 地区    │ 其他地区角色              │
├──────────────┼───────────────┼───────────────────────────┤
│ 系统配置      │ NA (总部)      │ Slave (只读)             │
│ 权限/角色     │ NA (总部)      │ Slave (只读)             │
│ EU 的 Job     │ EU            │ Slave (只读/按需同步)     │
│ NA 的 Job     │ NA            │ Slave (只读/按需同步)     │
│ CN 的 Talent  │ CN            │ Slave (只读/按需同步)     │
│ Finance      │ 各自 Region    │ 不同步 (合规要求)         │
└──────────────┴───────────────┴───────────────────────────┘
```

## 架构图

```
                    ┌──────────────────────────────────┐
                    │   Global Config Master (NA)       │
                    │   system_config, roles, etc.      │
                    └──────────────┬───────────────────┘
                                   │ Replication
                    ┌──────────────┼──────────────┐
                    ▼              ▼              ▼
            ┌──────────┐   ┌──────────┐   ┌──────────┐
            │ EU       │   │ NA       │   │ CN       │
            │          │   │          │   │          │
            │ Local RW:│   │ Local RW:│   │ Local RW:│
            │ EU Jobs  │   │ NA Jobs  │   │ CN Jobs  │
            │ EU Talent│   │ NA Talent│   │ CN Talent│
            │          │   │          │   │          │
            │ Global   │   │ Global   │   │ Global   │
            │ RO Slave │   │ RW Master│   │ RO Slave │
            └──────────┘   └──────────┘   └──────────┘
```

## 写操作路由

当用户在任意 Region 发起写操作时：
1. 如果本节点是该数据的 Owner → **直接本地写入**
2. 如果本节点不是 Owner → **通过 REST 自动转发到 Owner Region**

```
场景: EU 用户想修改一个归属于 NA 的 Job
→ EU 节点发现 ownerRegion=NA，本节点不是 Owner
→ 通过 RegionWriteRouter 转发 REST 请求到 NA
→ NA 写入后通过 SyncService 将变更推送给 EU 和 CN
→ EU 和 CN 收到 SyncEvent 后更新本地只读副本
```

## 数据类型说明

| DataCategory | Master 归属 | 同步策略 | 说明 |
|---|---|---|---|
| `SYSTEM_CONFIG` | NA (固定) | 全量同步到所有 Region | 系统配置，全局统一管理 |
| `ROLE_PERMISSION` | NA (固定) | 全量同步到所有 Region | 权限角色，全局统一管理 |
| `JOB` | 各自 Region | 同步到所有 Region | 职位数据，按 Region 归属 |
| `TALENT` | 各自 Region | 同步到所有 Region | 人才数据，按 Region 归属 |
| `FINANCE` | 各自 Region | **不同步**（合规要求） | 财务数据，仅本地可见 |

## 项目结构

```
region-sync-master-slave-demo/
├── pom.xml                          # Maven 构建配置
├── Dockerfile                       # Docker 镜像定义
├── docker-compose.yml               # 3 Region 部署模拟 (NA/EU/CN)
├── src/main/java/com/example/regionsync/
│   ├── RegionSyncApplication.java        # Spring Boot 入口
│   ├── config/
│   │   ├── RegionConfig.java             # Region 配置（数据归属映射）
│   │   └── WebConfig.java               # REST 配置
│   ├── controller/
│   │   ├── DataController.java           # CRUD REST API（含写路由）
│   │   └── SyncController.java           # 同步 API
│   ├── model/
│   │   ├── DataCategory.java             # 数据类别枚举
│   │   ├── DataItem.java                 # 数据模型（含 category/ownerRegion）
│   │   ├── SyncEvent.java                # 同步事件
│   │   └── RegionInfo.java               # Region 状态
│   ├── service/
│   │   ├── DataService.java              # 数据 CRUD 逻辑
│   │   ├── SyncService.java              # Peer-to-Peer 同步
│   │   └── RegionWriteRouter.java        # 写操作路由器（核心）
│   └── store/
│       └── InMemoryDataStore.java        # 内存存储（FINANCE 不记事件日志）
└── src/test/java/com/example/regionsync/
    ├── InMemoryDataStoreTest.java        # 数据存储单元测试
    └── RegionSyncApplicationTests.java   # Spring Boot 上下文测试
```

## 快速开始

### 前提条件

- Java 17+
- Maven 3.8+
- Docker & Docker Compose（多 Region 模拟）

### 方式一：Docker Compose（推荐）

启动 3 个 Region 节点：NA（总部）、EU、CN。

```bash
docker-compose up --build

# 节点地址：
#   NA (总部): http://localhost:8081
#   EU:       http://localhost:8082
#   CN:       http://localhost:8083
```

### 方式二：本地 Maven 运行

```bash
mvn clean package

# Terminal 1 - NA (port 8081)
java -jar target/region-sync-master-slave-demo-1.0.0-SNAPSHOT.jar \
  --server.port=8081 \
  --region.id=NA \
  --region.peer-urls.EU=http://localhost:8082 \
  --region.peer-urls.CN=http://localhost:8083

# Terminal 2 - EU (port 8082)
java -jar target/region-sync-master-slave-demo-1.0.0-SNAPSHOT.jar \
  --server.port=8082 \
  --region.id=EU \
  --region.peer-urls.NA=http://localhost:8081 \
  --region.peer-urls.CN=http://localhost:8083

# Terminal 3 - CN (port 8083)
java -jar target/region-sync-master-slave-demo-1.0.0-SNAPSHOT.jar \
  --server.port=8083 \
  --region.id=CN \
  --region.peer-urls.NA=http://localhost:8081 \
  --region.peer-urls.EU=http://localhost:8082
```

## API 参考

### 数据 API（CRUD）

| 方法 | 路径 | 说明 | 可用节点 |
|---|---|---|---|
| `POST` | `/api/data` | 创建数据（自动路由到 Owner Region） | 所有节点 |
| `GET` | `/api/data` | 获取所有数据 | 所有节点 |
| `GET` | `/api/data?category=JOB` | 按类别查询 | 所有节点 |
| `GET` | `/api/data/{key}` | 按 key 查询 | 所有节点 |
| `PUT` | `/api/data/{key}` | 更新数据（自动路由） | 所有节点 |
| `DELETE` | `/api/data/{key}` | 删除数据（自动路由） | 所有节点 |

### 同步 API（内部）

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/sync/receive` | 接收同步事件（Push） |
| `GET` | `/api/sync/events?afterSequence={seq}` | 拉取事件（Pull） |
| `GET` | `/api/sync/status` | 节点状态 |
| `GET` | `/api/sync/cluster-status` | 集群状态 |

### 请求体格式

```json
{
  "key": "eu-job-001",
  "value": "Software Engineer in Berlin",
  "category": "JOB",
  "ownerRegion": "EU"
}
```

## 使用示例

### 1. 在 NA 创建全局系统配置

```bash
curl -X POST http://localhost:8081/api/data \
  -H "Content-Type: application/json" \
  -d '{"key":"sys-config-1","value":"max_connections=1000","category":"SYSTEM_CONFIG","ownerRegion":"NA"}'
```

### 2. 在 EU 创建 EU 的 Job

```bash
curl -X POST http://localhost:8082/api/data \
  -H "Content-Type: application/json" \
  -d '{"key":"eu-job-001","value":"Engineer in Berlin","category":"JOB","ownerRegion":"EU"}'
```

### 3. 在 CN 读取 EU 的 Job（已自动同步）

```bash
curl http://localhost:8083/api/data/eu-job-001
```

### 4. 在 EU 创建 NA 的 Job（自动转发到 NA）

```bash
# EU 节点发现 ownerRegion=NA，自动转发到 NA 节点
curl -X POST http://localhost:8082/api/data \
  -H "Content-Type: application/json" \
  -d '{"key":"na-job-001","value":"Manager in NYC","category":"JOB","ownerRegion":"NA"}'
```

### 5. 在 CN 创建本地 Finance（不同步，合规要求）

```bash
curl -X POST http://localhost:8083/api/data \
  -H "Content-Type: application/json" \
  -d '{"key":"cn-finance-001","value":"CN Revenue Q1: $10M","category":"FINANCE","ownerRegion":"CN"}'

# 在 NA 或 EU 无法看到 CN 的 Finance 数据
curl http://localhost:8081/api/data/cn-finance-001
# 返回 404
```

### 6. 查看集群状态

```bash
curl http://localhost:8081/api/sync/cluster-status | jq .
```

## 配置说明

| 属性 | 默认值 | 说明 |
|---|---|---|
| `region.id` | `NA` | 本节点 Region ID |
| `region.category-owner-map.*` | 见 yml | 各数据类别的 Master Region 映射 |
| `region.peer-urls.*` | (空) | 其他 Region 的 URL |
| `region.sync-interval-ms` | `3000` | Pull 同步间隔（毫秒） |

### 数据归属映射配置

```yaml
region:
  category-owner-map:
    SYSTEM_CONFIG: NA        # 固定 Master = NA
    ROLE_PERMISSION: NA      # 固定 Master = NA
    JOB: REGIONAL            # 各 Region 各自为 Master
    TALENT: REGIONAL         # 各 Region 各自为 Master
    FINANCE: REGIONAL        # 各 Region 各自为 Master（不同步）
```

`REGIONAL` 表示"各 Region 是自己数据的 Master"。写操作时，系统根据 `ownerRegion` 字段判断是否本地处理或转发。

## 运行测试

```bash
mvn test
```

## 设计要点

### 为什么是"每条数据只有一个 Master"？

- 彻底避免写冲突（no conflict by design）
- 不需要复杂的冲突解决算法
- 每个 Region 仍然可以读取所有数据（本地副本）
- 写延迟只在跨 Region 转发时产生

### 为什么 FINANCE 不同步？

- 模拟真实场景中的合规要求（GDPR 等）
- 财务数据可能有数据驻留要求，不允许跨境传输
- 在 `InMemoryDataStore` 中通过不生成 `SyncEvent` 实现

### 写操作路由 vs 双向同步

| 方案 | 优点 | 缺点 |
|---|---|---|
| **本方案（写路由）** | 无冲突、实现简单、易于理解 | 写操作有跨 Region 延迟 |
| 双向同步 | 写操作低延迟 | 冲突解决复杂、可能丢数据 |

## 生产环境建议

本项目为 **Demo**，生产环境请考虑：

- 替换内存存储为数据库（PostgreSQL/MySQL）
- 使用消息队列（Kafka）替代 REST 推送
- 写路由层使用 Feign/gRPC 替代 RestTemplate
- 添加认证授权（sync 端点）
- 添加 TLS 加密
- 添加监控（Prometheus/Grafana）
- 添加熔断/限流（Resilience4j）
- 实现重试和指数退避
