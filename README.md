<img width="1912" height="3555" alt="Image" src="https://github.com/user-attachments/assets/e5784e83-efc6-46ba-b924-dbc6a6c760cb" />
<img width="1912" height="1029" alt="Image" src="https://github.com/user-attachments/assets/0920bfd6-4c87-4094-90d7-0001c8548351" />

# 🛍️ AI 쇼핑몰 - 대화형 상품 추천 시스템

> OpenAI GPT와 pgvector를 활용한 지능형 쇼핑몰 플랫폼
> 자연어 대화를 통한 개인화된 상품 추천 제공

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.java.net/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![OpenAI](https://img.shields.io/badge/OpenAI-GPT--4-black.svg)](https://openai.com/)

## 🚀 주요 특징

- **🤖 AI 대화형 추천**: OpenAI GPT-4 + text-embedding-3-small을 활용한 자연어 상품 추천
- **🔍 벡터 유사도 검색**: PostgreSQL pgvector + HNSW 인덱스로 고속 검색 (10-50ms)
- **⚡ 성능 최적화**: 비동기 처리 + Redis 캐싱으로 빠른 응답
- **🔐 보안**: JWT 인증 + Spring Security 기반 권한 관리

## 🛠️ 기술 스택

### Core
- **Backend**: Spring Boot 3.1.4 (Java 17)
- **Database**: PostgreSQL 16 + pgvector
- **Cache**: Redis 7
- **AI**: OpenAI GPT-4, text-embedding-3-small (1536차원)
- **Security**: Spring Security + JWT

### Support
- Spring Data JPA, MyBatis, QueryDSL 5.0
- MapStruct, Lombok, Firebase
- Docker & Docker Compose

## 🏗️ 아키텍처

```
사용자 쿼리 → OpenAI Embedding (1536차원 벡터)
    ↓
PostgreSQL pgvector 코사인 유사도 검색
    ↓
추천 상품 반환 + 대화 이력 저장
```

### 핵심 컴포넌트

| 컴포넌트 | 역할 | 위치 |
|---------|------|------|
| **ConversationalRecommendationService** | 대화형 추천 오케스트레이터 | `ai/recommendation/application/` |
| **ProductVectorService** | pgvector 검색 엔진 | `ai/recommendation/infrastructure/` |
| **EmbeddingApiClient** | OpenAI API 클라이언트 | `ai/embedding/` |
| **RecommendationEngine** | 추천 로직 | `ai/recommendation/application/` |

## 🚦 빠른 시작

### 1. 사전 요구사항
- Java 17+
- Docker & Docker Compose
- OpenAI API Key

### 2. 환경 설정
```bash
# OpenAI API 키 설정
echo "openai.api.key=sk-proj-your-key" > src/main/resources/application-secrets.properties
```

### 3. 실행
```bash
# 인프라 시작 (PostgreSQL + Redis)
docker-compose up -d

# 애플리케이션 시작
./gradlew bootRun
```

### 4. 접속
- **애플리케이션**: http://localhost:8080
- **헬스체크**: http://localhost:8080/actuator/health

## 📊 프로젝트 구조

```
src/main/java/com/example/crud/
├── ai/                          # AI 기능 모듈
│   ├── conversation/           # 대화 시스템
│   ├── embedding/              # OpenAI 임베딩
│   └── recommendation/         # 추천 엔진
├── common/                      # 공통 컴포넌트 (보안, 설정)
├── controller/                  # REST 컨트롤러
├── data/                        # 비즈니스 로직 (상품, 주문, 결제)
├── entity/                      # JPA 엔티티
└── repository/                  # 데이터 접근 계층
```

## 🔍 핵심 기능

### 벡터 검색 쿼리
```sql
SELECT p.number, p.name,
       (1 - (p.description_vector <=> CAST(? AS vector))) as similarity
FROM product p
WHERE (1 - (p.description_vector <=> CAST(? AS vector))) > 0.3
ORDER BY p.description_vector <=> CAST(? AS vector)
LIMIT ?;
```

### 성능 지표

| 항목 | 소요 시간 | 비고 |
|------|----------|------|
| 임베딩 생성 | 300-500ms | OpenAI API |
| Redis 캐시 | 5-10ms | 캐시 히트 시 |
| 벡터 검색 | 10-50ms | HNSW 인덱스 |
| 전체 응답 | 50-400ms | 캐시 여부에 따라 |

**Java 반복문 vs pgvector**: 200-600배 성능 향상 ⚡

## 🧪 API 테스트

```bash
# 헬스체크
curl http://localhost:8080/actuator/health

# 임베딩 생성
curl -X POST http://localhost:8080/api/test/recommendation/embedding \
  -H "Content-Type: application/json" \
  -d '{"text": "warm winter sweater"}'

# 상품 추천
curl -X POST http://localhost:8080/api/test/recommendation/text \
  -H "Content-Type: application/json" \
  -d '{"query": "comfortable knit clothing", "limit": 5}'
```

## 🔧 주요 설정

### 스레드 풀
```properties
# OpenAI API 전용
embeddingTaskExecutor: 코어 4, 최대 8, 큐 100

# DB 벡터 검색 전용
dbTaskExecutor: 코어 4, 최대 8, 큐 50
```

### Redis 캐싱
```java
String cacheKey = text.trim().toLowerCase().hashCode();
```
- 임베딩 결과 캐싱으로 API 호출 최소화

## 🗃️ 데이터베이스

### 주요 테이블
- **product**: 상품 정보 + description_vector (1536차원)
- **conversation**: 대화 이력
- **conversation_message**: USER/ASSISTANT 메시지
- **member**: 회원 정보 + JWT 인증

### pgvector 인덱스
```sql
CREATE INDEX idx_product_vector ON product
USING hnsw (description_vector vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

## 🔍 트러블슈팅

**OpenAI API 실패**
```bash
cat src/main/resources/application-secrets.properties
```

**pgvector 오류**
```bash
docker-compose exec db psql -U sungho -d app \
  -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

**Redis 연결 실패**
```bash
docker-compose exec redis redis-cli ping
```

## 📝 참고 문서

- [Spring Boot 3.1 Docs](https://docs.spring.io/spring-boot/docs/3.1.x/reference/html/)
- [OpenAI API Docs](https://platform.openai.com/docs/introduction)
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [HNSW Algorithm](https://arxiv.org/abs/1603.09320)

---

**🎯 핵심**: AI 기술을 활용한 개인화된 쇼핑 경험 제공
