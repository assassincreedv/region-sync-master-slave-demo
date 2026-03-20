#!/bin/bash
# ═══════════════════════════════════════════════════════════
#  Debezium Connector 注册脚本
#  将 Debezium MySQL Source Connector 注册到 Kafka Connect
#
#  前置条件:
#    docker-compose up -d (所有服务已启动)
#
#  用法:
#    ./debezium/register-connectors.sh
#
#  注册后可在以下 Kafka Topic 中看到 CDC 事件:
#    - dbserver-na.region_db.company  (NA 的 company 表变更)
#    - dbserver-eu.region_db.company  (EU 的 company 表变更)
#    - dbserver-cn.region_db.company  (CN 的 company 表变更)
# ═══════════════════════════════════════════════════════════

DEBEZIUM_URL="http://localhost:8083"

echo "Waiting for Debezium Connect to be ready..."
until curl -s "$DEBEZIUM_URL/connectors" > /dev/null 2>&1; do
  echo "  Debezium Connect not ready, retrying in 5s..."
  sleep 5
done
echo "Debezium Connect is ready!"

# ── Register NA MySQL Connector ──
echo ""
echo "Registering Debezium connector for MySQL-NA..."
curl -s -X POST "$DEBEZIUM_URL/connectors" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mysql-connector-na",
    "config": {
      "connector.class": "io.debezium.connector.mysql.MySqlConnector",
      "tasks.max": "1",
      "database.hostname": "mysql-na",
      "database.port": "3306",
      "database.user": "root",
      "database.password": "root",
      "database.server.id": "10001",
      "topic.prefix": "dbserver-na",
      "database.include.list": "region_db",
      "table.include.list": "region_db.company,region_db.sync_event_log",
      "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
      "schema.history.internal.kafka.topic": "schema-changes-na",
      "include.schema.changes": "false",
      "transforms": "unwrap",
      "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
      "transforms.unwrap.drop.tombstones": "false",
      "transforms.unwrap.delete.handling.mode": "rewrite"
    }
  }' | python3 -m json.tool 2>/dev/null || echo "(registered)"

# ── Register EU MySQL Connector ──
echo ""
echo "Registering Debezium connector for MySQL-EU..."
curl -s -X POST "$DEBEZIUM_URL/connectors" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mysql-connector-eu",
    "config": {
      "connector.class": "io.debezium.connector.mysql.MySqlConnector",
      "tasks.max": "1",
      "database.hostname": "mysql-eu",
      "database.port": "3306",
      "database.user": "root",
      "database.password": "root",
      "database.server.id": "10002",
      "topic.prefix": "dbserver-eu",
      "database.include.list": "region_db",
      "table.include.list": "region_db.company,region_db.sync_event_log",
      "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
      "schema.history.internal.kafka.topic": "schema-changes-eu",
      "include.schema.changes": "false",
      "transforms": "unwrap",
      "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
      "transforms.unwrap.drop.tombstones": "false",
      "transforms.unwrap.delete.handling.mode": "rewrite"
    }
  }' | python3 -m json.tool 2>/dev/null || echo "(registered)"

# ── Register CN MySQL Connector ──
echo ""
echo "Registering Debezium connector for MySQL-CN..."
curl -s -X POST "$DEBEZIUM_URL/connectors" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mysql-connector-cn",
    "config": {
      "connector.class": "io.debezium.connector.mysql.MySqlConnector",
      "tasks.max": "1",
      "database.hostname": "mysql-cn",
      "database.port": "3306",
      "database.user": "root",
      "database.password": "root",
      "database.server.id": "10003",
      "topic.prefix": "dbserver-cn",
      "database.include.list": "region_db",
      "table.include.list": "region_db.company,region_db.sync_event_log",
      "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
      "schema.history.internal.kafka.topic": "schema-changes-cn",
      "include.schema.changes": "false",
      "transforms": "unwrap",
      "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
      "transforms.unwrap.drop.tombstones": "false",
      "transforms.unwrap.delete.handling.mode": "rewrite"
    }
  }' | python3 -m json.tool 2>/dev/null || echo "(registered)"

echo ""
echo "═══════════════════════════════════════════════════════"
echo " All Debezium connectors registered!"
echo ""
echo " Verify status:"
echo "   curl http://localhost:8083/connectors"
echo "   curl http://localhost:8083/connectors/mysql-connector-na/status"
echo ""
echo " CDC Topics:"
echo "   dbserver-na.region_db.company"
echo "   dbserver-eu.region_db.company"
echo "   dbserver-cn.region_db.company"
echo "═══════════════════════════════════════════════════════"
