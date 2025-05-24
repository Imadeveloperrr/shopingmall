#!/bin/bash
# init-kafka-topics.sh - Kafka 토픽 생성 스크립트

# Docker 컨테이너 이름
KAFKA_CONTAINER="shopingmall-kafka-1"  # docker-compose ps로 확인

# 토픽 생성 함수
create_topic() {
    TOPIC_NAME=$1
    PARTITIONS=$2
    REPLICATION=$3

    echo "Creating topic: $TOPIC_NAME"
    docker exec $KAFKA_CONTAINER kafka-topics.sh --create \
        --bootstrap-server localhost:9092 \
        --topic $TOPIC_NAME \
        --partitions $PARTITIONS \
        --replication-factor $REPLICATION \
        --if-not-exists
}

# Kafka가 준비될 때까지 대기
echo "Waiting for Kafka to be ready..."
sleep 10

# 토픽 생성
create_topic "conv-msg-created" 3 1
create_topic "product-viewed" 3 1
create_topic "order-completed" 3 1
create_topic "user-behavior" 3 1
create_topic "recommendation-events" 3 1
create_topic "analytics-events" 3 1
create_topic "purchase-pattern-analyzed" 3 1

# 토픽 목록 확인
echo "Listing all topics:"
docker exec $KAFKA_CONTAINER kafka-topics.sh --list --bootstrap-server localhost:9092