# AI 쇼핑몰 추천 시스템 아키텍처

## 📋 목차
1. [시스템 개요](#시스템-개요)
2. [전체 아키텍처](#전체-아키텍처)
3. [핵심 컴포넌트](#핵심-컴포넌트)
4. [데이터 플로우](#데이터-플로우)
5. [기술 스택](#기술-스택)
6. [배포 및 모니터링](#배포-및-모니터링)
7. [성능 최적화](#성능-최적화)
8. [장애 처리](#장애-처리)

## 🎯 시스템 개요

이 프로젝트는 **AI 기반 적응형 하이브리드 대화형 상품 추천 서비스**입니다. 사용자의 대화 데이터를 실시간으로 분석하여 개인화된 상품 추천을 제공하는 마이크로서비스 아키텍처로 구성되어 있습니다.

### 주요 특징
- 🤖 **대화형 AI 추천**: ChatGPT와 연동한 자연어 처리
- 🔄 **실시간 학습**: 사용자 행동 패턴 실시간 분석
- 📊 **하이브리드 추천**: 콘텐츠 기반 + 협업 필터링 + AI 분석
- ⚡ **고성능**: Redis 캐싱과 비동기 처리
- 🛡️ **안정성**: Circuit Breaker, Outbox 패턴 적용

## 🏗️ 전체 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                          Frontend                                │
│                     (React/Vue.js)                              │
└─────────────────────┬───────────────────────────────────────────┘
                      │ HTTP/WebSocket
┌─────────────────────▼───────────────────────────────────────────┐
│                    Gateway/Load Balancer                        │
└─────────────────────┬───────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────┐
│                  Spring Boot Backend                            │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌───────────┐ │
│  │Conversation │ │Recommendation│ │   Outbox    │ │  Product  │ │
│  │   Service   │ │   Service    │ │  Pattern    │ │  Service  │ │
│  └─────┬───────┘ └─────┬───────┘ └─────┬───────┘ └─────┬─────┘ │
└────────┼─────────────────┼─────────────────┼─────────────────┼───┘
         │                 │                 │                 │
┌────────▼─────────────────▼─────────────────▼─────────────────▼───┐
│                      Apache Kafka                               │
│  Topic: conv-msg-created, product-viewed, order-completed...    │
└─────────────────────┬───────────────────────────────────────────┘
                      │
      ┌───────────────▼──────────────────┐
      │                                  │
┌─────▼─────┐                     ┌─────▼─────┐
│PostgreSQL │                     │   Redis   │
│(pgvector) │                     │ (Cache)   │
│ - 사용자   │                     │ - 추천    │
│ - 상품     │                     │ - 세션    │
│ - 대화     │                     │ - 통계    │
│ - 임베딩   │                     │           │
└───────────┘                     └───────────┘
      │
┌─────▼─────┐          ┌─────────────┐          ┌─────────────┐
│Elasticsearch│          │Python ML    │          │ Prometheus  │
│(검색/분석) │          │Service      │          │  Grafana    │
│ - 메시지   │◄────────►│- Embedding  │          │(모니터링)   │
│ - 로그     │          │- 벡터 생성  │          │             │
│ - 통계     │          │- 유사도 계산│          │             │
└───────────┘          └─────────────┘          └─────────────┘
```

## 🧩 핵심 컴포넌트

### 1. Spring Boot Backend (포트: 8080)

#### 1.1 Conversation Service
**역할**: 사용자 대화 관리 및 메시지 처리
- **주요 클래스**:
  - `ConversationController`: REST API 엔드포인트
  - `ConversationCommandService`: 대화 생성/수정
  - `ConversationQueryService`: 대화 조회
  - `MsgCreatedConsumer`: Kafka 메시지 소비
  - `PreferenceAnalysisConsumer`: 실시간 선호도 분석

**데이터 플로우**:
1. 사용자 메시지 입력 → Controller
2. 메시지 저장 → Database
3. 이벤트 발행 → Kafka (conv-msg-created)
4. ChatGPT API 호출 → 응답 생성
5. Elasticsearch 인덱싱 → 검색 가능

#### 1.2 Recommendation Service
**역할**: AI 기반 상품 추천 로직
- **주요 클래스**:
  - `IntegratedRecommendationService`: 통합 추천 엔진
  - `ConversationalRecommendationService`: 대화 기반 추천
  - `RecommendationCacheService`: 추천 결과 캐싱
  - `RecommendationEventProcessor`: 이벤트 기반 처리

**추천 알고리즘**:
1. **콘텐츠 기반**: 상품 속성 유사도
2. **협업 필터링**: 사용자 행동 패턴
3. **AI 분석**: ChatGPT 기반 의도 파악
4. **하이브리드**: 가중치 조합으로 최종 추천

#### 1.3 Embedding Service Integration
**역할**: Python ML 서비스와의 연동
- **주요 클래스**:
  - `EmbeddingClient`: HTTP 클라이언트
  - `ProductEmbeddingService`: 상품 벡터화
  - `EmbeddingBatchScheduler`: 배치 처리

**기능**:
- 텍스트 → 벡터 변환 (384차원)
- 배치 처리 지원 (최대 50개)
- Circuit Breaker 패턴 적용
- 자동 재시도 및 폴백

#### 1.4 Outbox Pattern
**역할**: 트랜잭션 안전성 보장
- **주요 클래스**:
  - `OutboxDispatcher`: 스케줄러 기반 메시지 발송
  - `OutboxCleanupScheduler`: 오래된 데이터 정리
  - `OutboxMetrics`: 모니터링 지표

**처리 과정**:
1. DB 트랜잭션과 함께 Outbox 테이블에 이벤트 저장
2. 별도 스케줄러가 미전송 메시지 조회
3. Kafka로 배치 전송 (100개씩)
4. 전송 성공 시 상태 업데이트
5. 실패 시 재시도 로직 실행

### 2. Python ML Service (포트: 8000)

#### 2.1 핵심 기능
```python
# 주요 엔드포인트
POST /embed              # 단일 텍스트 임베딩
POST /batch-embed        # 배치 임베딩 (최대 100개)
POST /similarity         # 벡터 유사도 계산
GET  /healthz            # 헬스 체크
GET  /stats              # 통계 정보
```

#### 2.2 모델 및 성능
- **모델**: `sentence-transformers/all-MiniLM-L6-v2`
- **차원**: 384차원 벡터
- **성능**: GPU 사용 시 초당 1000+ 임베딩 생성
- **캐싱**: Redis 기반 결과 캐싱 (TTL: 1시간)

#### 2.3 최적화 요소
- **Thread Pool**: 멀티스레딩으로 병렬 처리
- **Circuit Breaker**: aiobreaker 라이브러리 사용
- **배치 처리**: 큰 요청을 청크로 분할
- **메모리 최적화**: 모델 워밍업과 정규화

### 3. 데이터 저장소

#### 3.1 PostgreSQL + pgvector
```sql
-- 주요 테이블 구조
CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE conversation_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(20) NOT NULL, -- USER, AI
    embedding vector(384),     -- pgvector 확장
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE user_preferences (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT UNIQUE NOT NULL,
    preferences JSONB,         -- 동적 선호도 데이터
    last_updated TIMESTAMP DEFAULT NOW()
);
```

#### 3.2 Redis 캐시 구조
```
DB 0 (Java Spring Boot):
├── rec:user:{userId}           # 사용자별 추천 결과
├── rec:popular:category:{cat}  # 카테고리별 인기 상품
├── rec:trending               # 실시간 트렌딩 (ZSet)
├── rec:similar:{productId}    # 유사 상품
├── user:preference:{userId}   # 사용자 선호도
└── outbox:dispatcher:lock     # 분산 락

DB 1 (Python ML Service):
├── embed:{hash}               # 임베딩 결과 캐시
├── stats:hits                # 캐시 히트 통계
├── stats:misses              # 캐시 미스 통계
└── cb:state_change:last      # Circuit Breaker 상태
```

#### 3.3 Elasticsearch 인덱스
```json
{
  "mappings": {
    "properties": {
      "messageId": {"type": "long"},
      "conversationId": {"type": "long"},
      "userId": {"type": "long"},
      "content": {"type": "text", "analyzer": "korean"},
      "type": {"type": "keyword"},
      "timestamp": {"type": "date"},
      "embedding": {"type": "dense_vector", "dims": 384}
    }
  }
}
```

### 4. Apache Kafka

#### 4.1 토픽 구조
```yaml
토픽 목록:
- conv-msg-created           # 메시지 생성 이벤트
- product-viewed            # 상품 조회 이벤트
- order-completed           # 주문 완료 이벤트
- user-behavior            # 사용자 행동 이벤트
- recommendation-events     # 추천 관련 이벤트
- analytics-events         # 분석 이벤트
- purchase-pattern-analyzed # 구매 패턴 분석 결과
```

#### 4.2 Consumer 그룹
- **es-sync**: Elasticsearch 동기화
- **preference-analysis**: 실시간 선호도 분석
- **recommendation**: 추천 시스템 이벤트 처리
- **analytics**: 데이터 분석 및 통계

## 🔄 데이터 플로우

### 1. 사용자 메시지 처리 플로우
```
1. 사용자 메시지 입력
   ↓
2. ConversationController.sendMessage()
   ↓
3. 메시지 저장 (PostgreSQL) + Outbox 이벤트 생성
   ↓
4. OutboxDispatcher → Kafka (conv-msg-created)
   ↓
5. 병렬 처리:
   a) MsgCreatedConsumer → Elasticsearch 인덱싱
   b) PreferenceAnalysisConsumer → 선호도 분석
   c) RecommendationEventProcessor → 추천 업데이트
   ↓
6. ChatGPT API 호출 → AI 응답 생성
   ↓
7. 응답 저장 + 추천 상품 조회
   ↓
8. WebSocket으로 실시간 응답
```

### 2. 상품 추천 플로우
```
1. 추천 요청 (사용자 ID + 메시지)
   ↓
2. 캐시 확인 (Redis: rec:user:{userId})
   ↓ (캐시 미스시)
3. 사용자 선호도 조회 (PostgreSQL + Redis)
   ↓
4. 임베딩 생성 (Python ML Service)
   ↓
5. 유사 상품 검색 (pgvector)
   ↓
6. 하이브리드 스코어링:
   - 콘텐츠 유사도 (30%)
   - 협업 필터링 (30%)
   - AI 분석 결과 (40%)
   ↓
7. 결과 랭킹 + 캐싱
   ↓
8. 응답 반환
```

### 3. 실시간 선호도 업데이트
```
1. 사용자 행동 이벤트 (메시지, 클릭, 구매)
   ↓
2. Kafka 메시지 수신
   ↓
3. ChatGPT API로 의도 분석
   ↓
4. 기존 선호도와 병합:
   - 카테고리 가중치 업데이트
   - 스타일 선호도 조정
   - 가격대 범위 수정
   - 감정 분석 반영
   ↓
5. PostgreSQL 저장 + Redis 캐싱
   ↓
6. 관련 추천 캐시 무효화
```

## 🛠️ 기술 스택

### Backend (Java)
- **프레임워크**: Spring Boot 3.x
- **데이터베이스**: PostgreSQL 16 + pgvector
- **캐싱**: Redis 7 + Lettuce
- **메시징**: Apache Kafka 7.5
- **검색**: Elasticsearch 8.11
- **모니터링**: Micrometer + Prometheus
- **테스트**: JUnit 5 + TestContainers

### ML Service (Python)
- **프레임워크**: FastAPI + Uvicorn
- **ML 라이브러리**: sentence-transformers, torch
- **캐싱**: aioredis
- **모니터링**: prometheus-client
- **안정성**: aiobreaker

### Infrastructure
- **컨테이너**: Docker + Docker Compose
- **데이터 볼륨**: 영구 볼륨 매핑
- **네트워크**: 브리지 네트워크 (172.20.0.0/16)
- **모니터링**: Prometheus + Grafana

## 📊 배포 및 모니터링

### 1. Docker Compose 배포
```bash
# 전체 스택 시작
docker-compose up -d

# 서비스별 상태 확인
docker-compose ps

# 로그 모니터링
docker-compose logs -f backend embedding-service
```

### 2. 헬스 체크 엔드포인트
```bash
# Spring Boot 헬스 체크
curl http://localhost:8080/actuator/health

# ML Service 헬스 체크
curl http://localhost:8000/healthz

# Elasticsearch 상태
curl http://localhost:9200/_cluster/health
```

### 3. 모니터링 대시보드
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Elasticsearch**: http://localhost:9200

### 4. 주요 메트릭
```yaml
JVM 메트릭:
- jvm_memory_used_bytes
- jvm_gc_pause_seconds
- http_server_requests_seconds

애플리케이션 메트릭:
- embedding_requests_total
- embedding_response_time
- cache_hit_rate
- recommendation_generation_time

인프라 메트릭:
- kafka_consumer_lag
- redis_connected_clients
- postgresql_connections_active
```

## ⚡ 성능 최적화

### 1. 캐싱 전략
```java
// 다층 캐싱 구조
1. L1 Cache (JVM): @Cacheable 애노테이션
2. L2 Cache (Redis): 분산 캐시
3. L3 Cache (ML Service): 임베딩 결과 캐시
```

### 2. 데이터베이스 최적화
```sql
-- 인덱스 최적화
CREATE INDEX CONCURRENTLY idx_messages_embedding_cosine 
ON conversation_messages USING ivfflat (embedding vector_cosine_ops);

-- 연결 풀 최적화
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

### 3. 비동기 처리
```java
// 비동기 메서드 활용
@Async("taskExecutor")
public CompletableFuture<List<Product>> generateRecommendations()

// Reactive Streams
return webClient.post()
    .uri("/embed")
    .bodyValue(request)
    .retrieve()
    .bodyToMono(EmbedResponse.class);
```

## 🛡️ 장애 처리

### 1. Circuit Breaker 패턴
```java
// ML Service 호출 시 Circuit Breaker 적용
@Component
@Slf4j
public class EmbeddingClient {
    private final CircuitBreaker circuitBreaker;
    
    public Mono<float[]> embed(String text) {
        return webClient.post()
            .transform(CircuitBreakerOperator.of(circuitBreaker))
            .retryWhen(createRetrySpec())
            .onErrorResume(this::handleFallback);
    }
}
```

### 2. Bulkhead 패턴
```yaml
# application.yml
resilience4j:
  bulkhead:
    instances:
      embedding:
        maxConcurrentCalls: 10
        maxWaitDuration: 0
```

### 3. 재시도 정책
```java
private Retry createRetrySpec() {
    return Retry.backoff(3, Duration.ofSeconds(1))
        .maxBackoff(Duration.ofSeconds(5))
        .filter(throwable -> !(throwable instanceof IllegalArgumentException));
}
```

### 4. 데이터 일관성
```java
// Outbox 패턴으로 메시지 안전성 보장
@Transactional
public void createMessage(CreateMessageRequest request) {
    // 1. 메시지 저장
    Message message = messageRepository.save(newMessage);
    
    // 2. Outbox 이벤트 생성 (같은 트랜잭션)
    Outbox outbox = Outbox.of("conv-msg-created", 
        Json.encode(payload), Instant.now());
    outboxRepository.save(outbox);
    
    // 3. 별도 스케줄러가 Kafka로 전송
}
```

## 📈 성능 지표 및 SLA

### 1. 응답 시간 목표
- **일반 API**: < 200ms (95th percentile)
- **추천 생성**: < 1s (90th percentile)  
- **임베딩 생성**: < 500ms (95th percentile)
- **검색 쿼리**: < 100ms (95th percentile)

### 2. 처리량 목표
- **동시 사용자**: 1,000명
- **메시지/초**: 100개
- **추천 요청/초**: 50개
- **임베딩 생성/초**: 1,000개

### 3. 가용성 목표
- **전체 시스템**: 99.9% uptime
- **핵심 API**: 99.95% uptime
- **데이터 일관성**: 99.99%

## 🔧 개발 및 운영 가이드

### 1. 로컬 개발 환경 설정
```bash
# 1. 의존성 서비스 시작 (DB, Redis, Kafka만)
docker-compose up -d db redis kafka zookeeper elasticsearch

# 2. ML Service 시작
cd ml-service
pip install -r requirements.txt
uvicorn ml_app.main:app --reload --port 8000

# 3. Spring Boot 애플리케이션 시작
./gradlew bootRun
```

### 2. 테스트 실행
```bash
# 단위 테스트
./gradlew test

# 통합 테스트
./gradlew integrationTest

# Python 테스트
cd ml-service
pytest tests/
```

### 3. 배포 스크립트
```bash
#!/bin/bash
# deploy.sh

# 1. 이미지 빌드
docker-compose build --no-cache

# 2. 데이터베이스 마이그레이션
./gradlew flywayMigrate

# 3. 서비스 재시작 (무중단)
docker-compose up -d --no-deps backend embedding-service

# 4. 헬스 체크
./scripts/health-check.sh
```

## 🎯 향후 개선 계획

### 1. 단기 개선사항 (1-2개월)
- [ ] GraphQL API 도입
- [ ] 실시간 A/B 테스트 프레임워크
- [ ] 고급 추천 알고리즘 (Deep Learning)
- [ ] 다국어 지원

### 2. 중기 개선사항 (3-6개월)
- [ ] Kubernetes 클러스터 이전
- [ ] Event Sourcing 패턴 도입
- [ ] 실시간 스트리밍 분석 (Apache Flink)
- [ ] 개인정보 보호 강화

### 3. 장기 개선사항 (6개월+)
- [ ] MLOps 파이프라인 구축
- [ ] 멀티 리전 배포
- [ ] 자동 스케일링
- [ ] 고급 보안 기능

---

## 📞 문의 및 지원

- **개발팀 이메일**: dev-team@company.com
- **기술 문서**: [Internal Wiki](http://wiki.company.com)
- **이슈 트래킹**: [JIRA](http://jira.company.com)
- **모니터링**: [Grafana Dashboard](http://monitoring.company.com)

---
*마지막 업데이트: 2025-01-21*