# 🛍️ AI-Powered Shopping Mall

<img width="1912" height="3555" alt="Image" src="https://github.com/user-attachments/assets/e5784e83-efc6-46ba-b924-dbc6a6c760cb" />
<img width="1912" height="1029" alt="Image" src="https://github.com/user-attachments/assets/0920bfd6-4c87-4094-90d7-0001c8548351" />

> **AI 기반 대화형 추천 시스템을 탑재한 현대적인 쇼핑몰 플랫폼**  
> 실시간 사용자 행동 분석과 벡터 유사도 검색을 통한 개인화된 상품 추천 서비스

## 🚀 주요 특징

- **🤖 AI 대화형 추천**: GPT-4와 벡터 유사도 기반 개인화 추천
- **⚡ 실시간 처리**: Kafka 이벤트 스트리밍으로 실시간 사용자 행동 분석  
- **🛡️ 고가용성**: Circuit Breaker와 다층 캐싱으로 안정적인 서비스
- **📈 확장 가능**: 마이크로서비스 아키텍처와 독립적인 ML 파이프라인

## 🛠️ 기술 스택

### Frontend
| 기술 | 설명 |
|------|------|
| **Template Engine** | Thymeleaf |
| **CSS Framework** | Bootstrap 5 |
| **Ajax Communication** | XMLHttpRequest, Fetch API |

### Backend Core
| 카테고리 | 기술 스택 |
|----------|-----------|
| **Framework** | Spring Boot 3.1.4, Spring Security 6.x (JWT), Spring Data JPA, Spring WebFlux |
| **Database** | PostgreSQL + pgvector, MariaDB, Redis, Elasticsearch |
| **Storage** | Firebase Storage |
| **Message Queue** | Apache Kafka, Zookeeper |

### AI/ML Services
| 기술 | 용도 |
|------|------|
| **FastAPI** | Python 임베딩 서비스 |
| **Sentence Transformers** | 텍스트 임베딩 (384차원) |
| **OpenAI GPT-4** | 대화형 AI 및 선호도 분석 |

### Data Access & Mapping
- **ORM**: JPA/Hibernate, QueryDSL, MyBatis
- **Mapping**: MapStruct (DTO 매핑)

### Performance & Resilience
- **Circuit Breaker**: Resilience4j
- **Caching**: EHCache (로컬), Redis (분산)
- **Connection Pool**: Apache Commons Pool2

### Monitoring & DevOps
- **Metrics**: Micrometer, Prometheus, Spring Boot Actuator
- **Development**: Lombok, JUnit 5, Docker & Docker Compose

### Architecture Patterns
- Event-Driven Architecture (Kafka)
- Microservices Pattern (AI 서비스 분리)
- CQRS Pattern (Command/Query 분리)
- Outbox Pattern (트랜잭션 보장)
- Circuit Breaker Pattern (장애 대응)
- Cache-Aside Pattern (캐싱 전략)

## 🏗️ 인프라 구성

```yaml
Services:
  - Spring Boot Application (Port: 8080)
  - PostgreSQL + pgvector (Port: 5432)
  - Redis Cache (Port: 6379)
  - Kafka Broker (Port: 9092)
  - Elasticsearch (Port: 9200)
  - FastAPI ML Service (Port: 8000)
```

## 📊 핵심 기능

### 🤖 AI 기반 대화형 추천 시스템
- 실시간 사용자 행동 분석
- 벡터 유사도 기반 상품 추천
- 대화 컨텍스트 이해 및 선호도 분석

### ⚡ 실시간 이벤트 처리
- Kafka 기반 이벤트 스트리밍
- 비동기 메시지 처리
- 실시간 트렌드 분석

### 🛡️ 고가용성 아키텍처
- Circuit Breaker로 장애 격리
- 다층 캐싱 전략 (L1: EHCache, L2: Redis)
- 트랜잭션 아웃박스 패턴

### 🔧 확장 가능한 ML 파이프라인
- 독립적인 ML 서비스 (FastAPI)
- 임베딩 벡터 검색 (pgvector)
- GPT-4 API 통합

---

# 🏗️ 대화형 상품 추천 시스템 아키텍처

## 1. 시스템 구성 요소

### 🔷 Core Services
- **Spring Boot Backend**: 메인 애플리케이션 서버
- **ML Service (FastAPI)**: 임베딩 생성 서비스 (384차원)
- **PostgreSQL + pgvector**: 메인 DB + 벡터 검색
- **Elasticsearch**: 대화 내용 검색 및 분석
- **Redis**: 캐싱 및 실시간 데이터
- **Kafka**: 이벤트 스트리밍

## 2. 주요 데이터 플로우

### 📌 Flow 1: 상품 등록 → 임베딩 생성
```
1. ProductController.addProduct()
   ↓
2. ProductService.getAddProduct()
   - 상품 정보 DB 저장
   - ProductEmbeddingService.createAndSaveEmbeddingAsync() 호출
   ↓
3. ProductEmbeddingService (비동기)
   - EmbeddingClient → ML Service 호출
   - 384차원 벡터 생성
   - product.description_vector 업데이트
   ↓
4. pgvector 인덱스 자동 업데이트
```

### 📌 Flow 2: 사용자 대화 → 선호도 분석
```
1. ConversationController.sendMessage()
   ↓
2. ConversationCommandService.addMessage()
   - DB 저장 (conversation_message)
   - Outbox 패턴으로 이벤트 저장
   ↓
3. OutboxDispatcher (스케줄러)
   - Kafka로 "conv-msg-created" 발행
   ↓
4. Kafka Consumers (병렬 처리)
   ├─ MsgCreatedConsumer
   │  └─ Elasticsearch 인덱싱
   ├─ PreferenceAnalysisConsumer
   │  └─ ChatGPT로 선호도 분석
   │  └─ user_preference 테이블 업데이트
   │  └─ Redis 캐싱
   └─ RecommendationEventProcessor
      └─ 실시간 추천 업데이트
```

### 📌 Flow 3: 추천 생성 프로세스
```
1. ConversationalRecommendationService.processUserMessage()
   ↓
2. EnhancedRecommendationService.recommendForUser()
   ├─ Redis 캐시 확인
   ├─ 사용자 선호도 조회 (user_preference)
   ├─ 메시지 임베딩 생성 (ML Service)
   └─ 다중 전략 추천
      ├─ 벡터 유사도 (40%) - ProductVectorRepository
      ├─ 카테고리 선호도 (30%) - 선호도 기반
      ├─ 가격대 필터링 (10%)
      ├─ 트렌딩 가산점 (10%) - Redis ZSET
      └─ 협업 필터링 (10%) - 유사 사용자
   ↓
3. RecommendationCacheService
   - 결과 캐싱 (6시간)
   - 추천 히스토리 저장
```

## 3. 캐싱 전략

### 🔸 L1 Cache (Application Level)
- **EHCache**: ChatGPT 응답 (30초 TTL)

### 🔸 L2 Cache (Redis)
- **대화 내용**: ZSET으로 시계열 저장 (6시간)
- **사용자 선호도**: String으로 JSON 저장 (24시간)
- **추천 결과**: List로 저장 (6시간)
- **트렌딩 상품**: ZSET으로 점수 관리
- **유사 사용자**: List로 저장

## 4. 실시간 처리 컴포넌트

### 🔹 Event Processors
1. **PreferenceAnalysisConsumer**
   - GPT 기반 실시간 선호도 분석
   - 가중치 기반 선호도 병합

2. **RecommendationEventProcessor**
   - 상품 조회/구매 이벤트 처리
   - 트렌딩 점수 업데이트
   - 추천 재계산 스케줄링

3. **ConversationSearchService**
   - Elasticsearch 기반 대화 검색
   - 시간대별 활동 패턴 분석
   - 트렌딩 키워드 추출

## 5. 모니터링 및 관리

### 🔸 RecommendationSystemMonitor
- 시스템 헬스 체크 (1분 주기)
- 성능 메트릭 수집 (5분 주기)
- 추천 품질 분석 (일일)

### 🔸 Metrics
- 캐시 히트율
- 평균 응답 시간
- 추천 다양성 점수
- 사용자 만족도

## 6. 장애 대응

### 🛡️ Circuit Breaker
- ML Service 장애 → 빈 벡터 반환
- ChatGPT 장애 → 캐시/기본값 사용
- ES 장애 → Redis 캐시 우선

### 🛡️ Outbox Pattern
- 트랜잭션 보장
- 이벤트 전달 신뢰성
- 배치 처리 (500개 단위)

## 7. 데이터 일관성

### 🔄 동기화
- **conversation_message** ↔ **Elasticsearch**
  - Kafka를 통한 비동기 동기화
  - Outbox 패턴으로 신뢰성 보장

- **product** ↔ **description_vector**
  - 상품 등록/수정 시 자동 생성
  - 배치 스케줄러로 누락 처리

- **user_preference** ↔ **Redis Cache**
  - TTL 기반 자동 갱신
  - 업데이트 시 즉시 무효화

## 8. 확장성 고려사항

### 📈 수평 확장
- Kafka 파티션 증가 (현재 3개)
- Redis 클러스터 구성
- ES 샤드 증가
- 애플리케이션 인스턴스 증가

### 📊 성능 최적화
- pgvector 인덱스 튜닝 (lists 파라미터)
- 배치 임베딩 처리
- 비동기 처리 확대
- 캐시 워밍 전략

## 9. 보안 고려사항
- API Rate Limiting
- 사용자별 요청 제한
- 민감 정보 마스킹
- JWT 기반 인증

## 10. 향후 개선사항
- A/B 테스트 프레임워크
- 실시간 피드백 반영
- 다국어 지원
- 고급 협업 필터링
