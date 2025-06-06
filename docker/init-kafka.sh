#!/bin/bash
# docker/init-kafka.sh

echo "Waiting for Kafka to be ready..."
cub kafka-ready -b kafka:9092 1 60

# Kafka 토픽 생성
echo "Creating Kafka topics..."

kafka-topics.sh --create --if-not-exists \
    --bootstrap-server kafka:9092 \
    --topic conv-msg-created \
    --partitions 3 \
    --replication-factor 1

kafka-topics.sh --create --if-not-exists \
    --bootstrap-server kafka:9092 \
    --topic product-viewed \
    --partitions 3 \
    --replication-factor 1

kafka-topics.sh --create --if-not-exists \
    --bootstrap-server kafka:9092 \
    --topic order-completed \
    --partitions 3 \
    --replication-factor 1

kafka-topics.sh --create --if-not-exists \
    --bootstrap-server kafka:9092 \
    --topic user-behavior \
    --partitions 3 \
    --replication-factor 1

kafka-topics.sh --create --if-not-exists \
    --bootstrap-server kafka:9092 \
    --topic recommendation-events \
    --partitions 3 \
    --replication-factor 1

kafka-topics.sh --create --if-not-exists \
    --bootstrap-server kafka:9092 \
    --topic analytics-events \
    --partitions 3 \
    --replication-factor 1

kafka-topics.sh --create --if-not-exists \
    --bootstrap-server kafka:9092 \
    --topic purchase-pattern-analyzed \
    --partitions 3 \
    --replication-factor 1

echo "Kafka topics created successfully!"

# 토픽 목록 확인
echo "Listing all topics:"
kafka-topics.sh --list --bootstrap-server kafka:9092