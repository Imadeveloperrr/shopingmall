#!/bin/bash
# docker/initdb/03_create_kafka_topics.sh

# Kafka가 시작될 때까지 대기
echo "Waiting for Kafka to be ready..."
sleep 30

# Kafka 토픽 생성
docker exec kafka kafka-topics.sh --create --bootstrap-server localhost:9092 --topic conv-msg-created --partitions 3 --replication-factor 1 --if-not-exists
docker exec kafka kafka-topics.sh --create --bootstrap-server localhost:9092 --topic product-viewed --partitions 3 --replication-factor 1 --if-not-exists
docker exec kafka kafka-topics.sh --create --bootstrap-server localhost:9092 --topic order-completed --partitions 3 --replication-factor 1 --if-not-exists
docker exec kafka kafka-topics.sh --create --bootstrap-server localhost:9092 --topic user-behavior --partitions 3 --replication-factor 1 --if-not-exists
docker exec kafka kafka-topics.sh --create --bootstrap-server localhost:9092 --topic recommendation-events --partitions 3 --replication-factor 1 --if-not-exists
docker exec kafka kafka-topics.sh --create --bootstrap-server localhost:9092 --topic analytics-events --partitions 3 --replication-factor 1 --if-not-exists
docker exec kafka kafka-topics.sh --create --bootstrap-server localhost:9092 --topic purchase-pattern-analyzed --partitions 3 --replication-factor 1 --if-not-exists

echo "Kafka topics created successfully!"

# 토픽 목록 확인
docker exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092