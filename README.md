<img width="1912" height="3555" alt="Image" src="https://github.com/user-attachments/assets/e5784e83-efc6-46ba-b924-dbc6a6c760cb" />
<img width="1912" height="1029" alt="Image" src="https://github.com/user-attachments/assets/0920bfd6-4c87-4094-90d7-0001c8548351" />

# 🛍️ AI 쇼핑몰 - 대화형 상품 추천 시스템

> **OpenAI GPT와 벡터 유사도 검색을 활용한 지능형 쇼핑몰 플랫폼**
> 사용자와의 자연어 대화를 통해 개인화된 상품 추천을 제공하는 현대적인 전자상거래 시스템

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.java.net/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![OpenAI](https://img.shields.io/badge/OpenAI-GPT--4-black.svg)](https://openai.com/)

## 📋 목차

- [주요 특징](#-주요-특징)
- [기술 스택](#-기술-스택)
- [시스템 아키텍처](#-시스템-아키텍처)
- [핵심 기능](#-핵심-기능)
- [프로젝트 구조](#-프로젝트-구조)
- [실행 방법](#-실행-방법)
- [API 테스트](#-api-테스트)
- [성능 지표](#-성능-지표)
- [개발자 가이드](#-개발자-가이드)

## 🚀 주요 특징

### 🤖 AI 대화형 추천
- **OpenAI 통합**: GPT-4와 text-embedding-3-small을 활용한 자연어 상품 추천
- **대화 컨텍스트 관리**: 사용자별 대화 이력 저장 및 추적
- **지능형 응답 생성**: 검색 결과에 따른 맞춤형 응답 메시지 자동 생성

### 🔍 벡터 유사도 검색
- **PostgreSQL pgvector**: 1536차원 벡터 공간에서 고속 검색
- **HNSW 인덱스**: 대용량 데이터에서도 빠른 유사도 검색 (10-50ms)
- **동적 임계값**: 7단계 임계값 시스템으로 단일 상품에서도 안정적인 추천

### ⚡ 성능 최적화
- **비동기 처리**: CompletableFuture 기반 논블로킹 처리
- **2-Tier 캐싱**: Redis + EHCache를 활용한 다층 캐시 전략
- **연결 풀 최적화**: 별도의 임베딩 전용 스레드 풀로 Tomcat 리소스 보호

### 🛡️ 엔터프라이즈 기능
- **JWT 인증**: Stateless 토큰 기반 보안 (Access + Refresh Token)
- **역할 기반 권한**: Spring Security를 활용한 세밀한 접근 제어
- **다국어 지원**: i18n 메시지 소스 (한국어, 영어, 일본어)
- **모니터링**: Spring Actuator를 통한 헬스체크 및 메트릭 수집

## 🛠️ 기술 스택

### Backend Core
| 카테고리 | 기술 스택 | 버전 |
|----------|-----------|------|
| **Framework** | Spring Boot | 3.1.4 |
| **Language** | Java | 17 |
| **Security** | Spring Security + JWT | 6.x |
| **Data Access** | Spring Data JPA + MyBatis | - |
| **Database** | PostgreSQL + pgvector | 16 |
| **Cache** | Redis + EHCache | 7 + 3.10.8 |
| **AI/ML** | OpenAI API | GPT-4, text-embedding-3-small |

### Supporting Technologies
| 기술 | 용도 | 버전 |
|------|------|------|
| **QueryDSL** | 타입 안전한 동적 쿼리 | 5.0.0 |
| **MapStruct** | DTO 매핑 자동화 | 1.5.5 |
| **Firebase** | 파일 스토리지 | 9.2.0 |
| **Spring Retry** | API 호출 재시도 로직 | - |
| **Spring WebFlux** | 비동기 HTTP 클라이언트 | - |
| **Thymeleaf** | 서버 사이드 템플릿 엔진 | - |

### Development & Testing
- **Build**: Gradle 8.x
- **Testing**: JUnit 5, TestContainers, H2
- **DevOps**: Docker & Docker Compose
- **Code Quality**: Lombok, Spring Boot DevTools

## 🏗️ 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend Layer                        │
│                  (Thymeleaf + JavaScript)                   │
└───────────────────────┬─────────────────────────────────────┘
                        │ HTTP/REST
┌───────────────────────▼─────────────────────────────────────┐
│                   Spring Boot Backend                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Presentation Layer (Controllers)                    │  │
│  │  - ProductController, CartController                 │  │
│  │  - ConversationController, PaymentController         │  │
│  └──────────────────────┬───────────────────────────────┘  │
│  ┌──────────────────────▼───────────────────────────────┐  │
│  │  Application Layer (Services)                        │  │
│  │  - ConversationalRecommendationService               │  │
│  │  - RecommendationEngine                             │  │
│  │  - ProductService, OrderService, PaymentService      │  │
│  └──────────────────────┬───────────────────────────────┘  │
│  ┌──────────────────────▼───────────────────────────────┐  │
│  │  Infrastructure Layer                                │  │
│  │  - ProductVectorService (pgvector 검색)             │  │
│  │  - EmbeddingApiClient (OpenAI 통신)                 │  │
│  │  - ProductRepository, ConversationRepository         │  │
│  └──────────────────────┬───────────────────────────────┘  │
└───────────────────────┼─────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
┌───────▼──────┐ ┌─────▼─────┐ ┌──────▼──────┐
│ PostgreSQL   │ │   Redis   │ │ OpenAI API  │
│ + pgvector   │ │   Cache   │ │  GPT-4 +    │
│              │ │           │ │  Embeddings │
└──────────────┘ └───────────┘ └─────────────┘
```

### 데이터 흐름 (AI 추천)

```
1. 사용자 쿼리
   "따뜻한 겨울 니트 추천해주세요"
          ↓
2. EmbeddingApiClient
   OpenAI API 호출 → 1536차원 벡터 생성
   [Redis 캐싱 체크/저장]
          ↓
3. ProductVectorService
   PostgreSQL pgvector 코사인 유사도 검색
   SELECT ... WHERE similarity > 0.3
          ↓
4. RecommendationEngine
   상위 N개 상품 선택 및 필터링
          ↓
5. ConversationalRecommendationService
   - 대화 메시지 저장 (USER)
   - AI 응답 생성 및 저장 (ASSISTANT)
   - ProductResponseDto 변환
          ↓
6. 사용자에게 응답 반환
   {conversationId, aiResponse, recommendations[]}
```

## 📊 핵심 기능

### 🤖 AI 대화형 추천 시스템

#### 핵심 워크플로우

**ConversationalRecommendationService** (`src/main/java/com/example/crud/ai/recommendation/application/ConversationalRecommendationService.java:52`)
- 대화형 추천의 메인 오케스트레이터
- 사용자 메시지와 AI 응답을 비동기로 저장
- 상품 추천 결과를 `ProductResponseDto`로 변환

**핵심 메서드**:
```java
public CompletableFuture<RecommendationResponseDto> processUserMessage(Long id, String message)
```

**처리 과정**:
1. 사용자 메시지를 비동기로 대화 이력에 저장
2. `RecommendationEngine`을 통해 상품 추천 요청
3. 추천 결과를 기반으로 AI 응답 메시지 생성
4. AI 응답을 비동기로 대화 이력에 저장
5. `RecommendationResponseDto` 반환

#### 응답 메시지 저장 이유

**추적 및 분석**:
- **노출 추적**: 사용자에게 어떤 상품을 보여줬는지 정확히 기록
- **클릭/구매 매칭**: 노출된 상품 중 어떤 것이 실제 행동으로 이어졌는지 분석
- **선호도 학습**: 카테고리, 브랜드, 가격대 등 속성별 가중치 업데이트

**지표 계산**:
- **CTR (Click Through Rate)**: 클릭 수 / 노출 수
- **CVR (Conversion Rate)**: 구매 수 / 노출 수
- **A/B 테스트**: 모델 버전별 성능 비교

**상담 연계**:
- AI가 처리하지 못한 경우 상담사에게 핸드오버
- 상담사가 대화 컨텍스트와 노출 상품을 즉시 확인
- 중복 제안 없이 정확한 대안 제시

### 🔍 벡터 검색 엔진

**ProductVectorService** (`src/main/java/com/example/crud/ai/recommendation/infrastructure/ProductVectorService.java:23`)
- pgvector를 활용한 고성능 벡터 유사도 검색
- 임베딩 생성과 DB 검색을 별도 스레드 풀에서 처리

**핵심 기능**:

**1. 텍스트 기반 상품 검색**
```java
public CompletableFuture<List<ProductSimilarity>> findSimilarProducts(String queryText, int limit)
```
- OpenAI 임베딩 생성 (`embeddingTaskExecutor` 스레드 풀)
- PostgreSQL 벡터 검색 (`dbTaskExecutor` 스레드 풀)
- 고정 임계값 0.3으로 인덱스 최적화

**2. 상품 기반 유사 상품 검색**
```java
public List<ProductSimilarity> findSimilarProductsByProduct(Long productId, int limit)
```
- 특정 상품과 유사한 다른 상품 찾기 (상품 상세 페이지용)
- 높은 임계값 0.4 사용 (더 정확한 매칭)
- 자기 자신은 결과에서 제외

#### PostgreSQL 벡터 검색 쿼리

**Repository** (`src/main/java/com/example/crud/repository/ProductRepository.java`)
```sql
SELECT p.number as productId,
       p.name as productName,
       p.description,
       (1 - (p.description_vector <=> CAST(? AS vector))) as similarity
FROM product p
WHERE p.description_vector IS NOT NULL
  AND (1 - (p.description_vector <=> CAST(? AS vector))) > ?
ORDER BY p.description_vector <=> CAST(? AS vector)
LIMIT ?
```

**연산자 설명**:
- `<=>`: 코사인 거리 연산자 (0=완전히 동일, 2=완전히 반대)
- `1 - (vector1 <=> vector2)`: 코사인 유사도 (0~1 범위)

**인덱스 최적화**:
```sql
CREATE INDEX idx_product_vector ON product
USING hnsw (description_vector vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

#### 성능 비교: Java vs pgvector

| 방식 | 시간 복잡도 | 실제 소요 시간 | 메모리 사용 |
|------|------------|--------------|------------|
| **Java 반복문** | O(N) | 10-30초 (10만개) | 높음 (전체 로딩) |
| **pgvector + HNSW** | O(log N) | 10-50ms | 낮음 (필요한 것만) |

**성능 향상**: 약 **200-600배** 빠름

### ⚡ 비동기 처리 및 캐싱

#### 스레드 풀 구성

**AsyncConfig** (`src/main/java/com/example/crud/common/config/AsyncConfig.java`)

| 스레드 풀 | 코어 | 최대 | 큐 | 용도 |
|-----------|------|------|-----|------|
| **embeddingTaskExecutor** | 4 | 8 | 100 | OpenAI API 호출 |
| **dbTaskExecutor** | 4 | 8 | 50 | PostgreSQL 벡터 검색 |
| **기본 Async** | 2 | 10 | 50 | 일반 비동기 작업 |

**분리 이유**:
- OpenAI API 호출은 I/O 대기가 길어 별도 풀 필요
- DB 쿼리도 무거운 벡터 연산으로 격리
- Tomcat 스레드 풀 고갈 방지

#### 캐싱 전략

**EmbeddingApiClient** (`src/main/java/com/example/crud/ai/embedding/EmbeddingApiClient.java:21`)

**L1 캐시 (Redis)**:
- 임베딩 결과 캐싱 (key: `text.hashCode()`)
- 동일한 텍스트 재요청 시 API 호출 없이 즉시 반환
- 평균 응답 시간: 300-500ms → **5-10ms**

**캐시 키 생성**:
```java
String cacheKey = String.valueOf(text.trim().toLowerCase().hashCode());
```
- 대소문자 무시 및 공백 제거로 캐시 히트율 향상

**Redis 설정** (`application.properties`)
```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.lettuce.pool.max-active=32
spring.data.redis.lettuce.pool.max-idle=8
```

### 🔐 보안 및 인증

**SecurityConfig** (`src/main/java/com/example/crud/common/security/SecurityConfig.java:26`)

#### JWT 토큰 인증
- **Stateless**: 세션을 사용하지 않는 토큰 기반 인증
- **Access Token**: 1시간 유효 (짧은 수명으로 보안 강화)
- **Refresh Token**: 7일 유효 (자동 갱신용)

#### 권한 기반 접근 제어
```java
.authorizeHttpRequests(authorize -> authorize
    .requestMatchers("/", "/register", "/login").permitAll()    // 공개
    .requestMatchers("/api/test/**").permitAll()                // 테스트 API
    .requestMatchers("/mypage/**").hasRole("USER")              // 회원 전용
    .requestMatchers("/cart/**", "/product/**").hasRole("USER") // 회원 전용
    .anyRequest().authenticated()                               // 나머지 인증 필요
)
```

#### 예외 처리
- **CustomAuthenticationEntryPoint**: 인증 실패 처리
- **CustomAccessDeniedHandler**: 권한 부족 처리

### 🛒 전자상거래 기능

#### 주요 도메인

**Product** (`src/main/java/com/example/crud/entity/Product.java:19`)
```java
@Entity
public class Product {
    private Long number;
    private String name;
    private String brand;
    private Integer price;
    private String imageUrl;
    private String description;
    private Category category;

    // 1536차원 벡터 (OpenAI text-embedding-3-small)
    @Column(columnDefinition = "vector(1536)")
    private String descriptionVector;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;  // 판매자

    @OneToMany(cascade = CascadeType.ALL)
    private List<ProductOption> productOptions;
}
```

**Conversation** (`src/main/java/com/example/crud/ai/conversation/domain/entity/Conversation.java:27`)
```java
@Entity
public class Conversation {
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;

    @OneToMany(cascade = CascadeType.ALL)
    @OrderBy("timestamp ASC")
    private List<ConversationMessage> messages;

    @Enumerated(EnumType.STRING)
    private ConversationStatus status;  // ACTIVE, CLOSED

    @Version
    private Integer version;  // Optimistic Lock
}
```

**Member** (`src/main/java/com/example/crud/entity/Member.java:19`)
```java
@Entity
public class Member {
    private Long number;
    private String email;
    private String password;  // BCrypt 암호화
    private String name;
    private String nickname;
    private String address;
    private String phoneNumber;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> roles;  // USER, ADMIN
}
```

#### 컨트롤러

| 컨트롤러 | 경로 | 주요 기능 |
|---------|------|----------|
| **IndexController** | `/` | 메인 페이지 |
| **AuthController** | `/login`, `/register` | 회원가입, 로그인 |
| **ProductController** | `/product/**` | 상품 목록, 상세, 등록 |
| **CartController** | `/cart/**` | 장바구니 관리 |
| **OrderController** | `/order/**` | 주문 처리 |
| **PaymentController** | `/payment/**` | 결제 처리 |
| **ConversationController** | `/conversation/**` | AI 대화 관리 |
| **MyPageController** | `/mypage/**` | 회원 정보 관리 |

## 📁 프로젝트 구조

```
src/main/java/com/example/crud/
├── ai/                                    # AI 기능 모듈
│   ├── common/
│   │   └── VectorFormatter.java          # 벡터 포맷 변환 유틸리티
│   ├── config/
│   │   ├── ChatGptProperties.java        # ChatGPT 설정
│   │   └── WebClientConfig.java          # WebClient 설정 (OpenAI API용)
│   ├── conversation/                     # 대화 시스템
│   │   ├── application/
│   │   │   └── command/
│   │   │       └── ConversationCommandService.java  # 대화 커맨드
│   │   ├── domain/
│   │   │   ├── entity/
│   │   │   │   ├── Conversation.java     # 대화 엔티티
│   │   │   │   ├── ConversationMessage.java  # 대화 메시지
│   │   │   │   └── UserPreference.java   # 사용자 선호도 (JSONB)
│   │   │   └── repository/
│   │   │       └── ConversationMessageRepository.java
│   │   └── infrastructure/
│   │       └── event/                    # 도메인 이벤트
│   ├── embedding/                        # 임베딩 서비스
│   │   ├── EmbeddingApiClient.java       # OpenAI API 클라이언트
│   │   ├── EmbeddingServiceException.java
│   │   ├── application/
│   │   │   └── EmbeddingService.java     # 상품 임베딩 생성
│   │   └── domain/
│   │       └── ProductTextBuilder.java   # 상품 텍스트 조합
│   └── recommendation/                   # 추천 시스템
│       ├── application/
│       │   ├── ConversationalRecommendationService.java  # 대화형 추천 오케스트레이터
│       │   └── RecommendationEngine.java  # 추천 엔진
│       ├── domain/
│       │   ├── converter/
│       │   │   └── ProductResponseDtoConverter.java  # DTO 변환
│       │   └── dto/
│       │       ├── ProductMatch.java      # 추천 결과
│       │       ├── RecommendationResponseDto.java
│       │       └── UserMessageRequestDto.java
│       ├── infrastructure/
│       │   └── ProductVectorService.java  # pgvector 검색
│       └── presentation/
│           └── RecommendationTestController.java  # 테스트 API
│
├── common/                               # 공통 컴포넌트
│   ├── config/
│   │   ├── AsyncConfig.java             # 비동기 스레드 풀 설정
│   │   ├── RedisConfig.java             # Redis 설정
│   │   ├── HibernateConfig.java         # JPA 설정
│   │   ├── MessageSourceConfig.java     # 다국어 메시지
│   │   ├── QuerydslConfig.java          # QueryDSL 설정
│   │   └── FirebaseConfig.java          # Firebase 스토리지
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java  # 전역 예외 처리
│   │   ├── BaseException.java
│   │   ├── ErrorCode.java
│   │   └── ValidationException.java
│   ├── security/
│   │   ├── SecurityConfig.java          # Spring Security 설정
│   │   ├── JwtTokenProvider.java        # JWT 토큰 생성/검증
│   │   ├── JwtAuthenticationFilter.java # JWT 필터
│   │   └── CustomUserDetailsService.java
│   └── utility/
│       ├── NativeQueryResultExtractor.java  # Native Query 결과 파싱
│       └── MaskingFilter.java           # 민감정보 마스킹
│
├── controller/                          # 메인 컨트롤러
│   ├── IndexController.java            # 메인 페이지
│   ├── AuthController.java             # 인증 (로그인/회원가입)
│   ├── ProductController.java          # 상품 관리
│   ├── CartController.java             # 장바구니
│   ├── OrderController.java            # 주문
│   ├── PaymentController.java          # 결제
│   ├── ConversationController.java     # AI 대화
│   └── MyPageController.java           # 회원 정보
│
├── data/                               # 비즈니스 로직 계층
│   ├── member/
│   │   ├── dto/
│   │   │   ├── MemberDto.java
│   │   │   └── MemberResponseDto.java
│   │   └── service/
│   │       └── impl/
│   │           └── MemberServiceImpl.java
│   ├── product/
│   │   ├── dto/
│   │   │   ├── ProductDto.java
│   │   │   ├── ProductResponseDto.java
│   │   │   ├── ProductOptionDto.java
│   │   │   └── ProductSizeDto.java
│   │   └── service/
│   │       └── impl/
│   │           └── ProductServiceImpl.java
│   ├── order/
│   │   └── service/
│   │       └── impl/
│   │           └── OrderServiceImpl.java
│   ├── payment/
│   │   ├── dto/
│   │   │   ├── PaymentDto.java
│   │   │   └── PaymentGatewayResponse.java
│   │   └── service/
│   │       ├── PaymentService.java
│   │       ├── PaymentGatewayClient.java
│   │       └── impl/
│   │           ├── PaymentServiceImpl.java
│   │           └── DummyPaymentGatewayClient.java
│   └── cart/
│       └── service/
│           └── impl/
│               └── CartServiceImpl.java
│
├── entity/                             # JPA 엔티티
│   ├── Product.java                   # 상품
│   ├── Member.java                    # 회원
│   ├── Cart.java                      # 장바구니
│   ├── CartItem.java                  # 장바구니 상품
│   ├── Orders.java                    # 주문
│   ├── OrderItem.java                 # 주문 상품
│   ├── PaymentHistory.java            # 결제 이력
│   ├── ProductOption.java             # 상품 옵션
│   └── RefreshToken.java              # JWT 갱신 토큰
│
├── repository/                        # 데이터 접근 계층
│   ├── ProductRepository.java        # 벡터 검색 쿼리 포함
│   ├── MemberRepository.java
│   ├── CartRepository.java
│   ├── OrderRepository.java
│   └── PaymentHistoryRepository.java
│
├── enums/                            # 열거형
│   ├── Category.java                # 상품 카테고리
│   ├── MessageType.java             # USER, ASSISTANT
│   ├── ConversationStatus.java      # ACTIVE, CLOSED
│   ├── OrderStatus.java             # 주문 상태
│   └── PaymentMethodType.java       # 결제 수단
│
└── CrudApplication.java             # 메인 애플리케이션
```

### 디렉토리 설계 원칙

**1. AI 모듈 (`ai/`)** - DDD 레이어드 아키텍처
- `presentation`: 컨트롤러 (외부 인터페이스)
- `application`: 서비스 (유즈케이스 오케스트레이션)
- `domain`: 도메인 엔티티 및 비즈니스 로직
- `infrastructure`: 외부 시스템 연동 (DB, API)

**2. 공통 모듈 (`common/`)** - 횡단 관심사
- 보안, 설정, 예외 처리, 유틸리티

**3. 비즈니스 로직 (`data/`)** - 도메인별 서비스
- 회원, 상품, 주문, 결제, 장바구니 등

**4. 인프라 (`entity/`, `repository/`)** - 영속성 계층
- JPA 엔티티와 리포지토리

## 🗃️ 데이터베이스 설계

### 핵심 테이블

#### 상품 테이블
```sql
CREATE TABLE product (
    number BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(255) NOT NULL,
    price INTEGER NOT NULL,
    image_url TEXT NOT NULL,
    intro TEXT NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    sub_category VARCHAR(100),
    member_id BIGINT REFERENCES member(number),
    description_vector vector(1536),  -- OpenAI 임베딩
    CONSTRAINT fk_member FOREIGN KEY (member_id) REFERENCES member(number)
);

-- pgvector 인덱스 (HNSW 알고리즘)
CREATE INDEX idx_product_vector ON product
USING hnsw (description_vector vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 카테고리 인덱스
CREATE INDEX idx_product_category ON product(category);
```

#### 대화 테이블
```sql
CREATE TABLE conversation (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    start_time TIMESTAMP DEFAULT NOW(),
    last_updated TIMESTAMP DEFAULT NOW(),
    status VARCHAR(20) NOT NULL,  -- ACTIVE, CLOSED
    version INTEGER DEFAULT 0,     -- Optimistic Lock
    CONSTRAINT fk_conversation_member FOREIGN KEY (member_id) REFERENCES member(number)
);

CREATE INDEX idx_conv_member ON conversation(member_id);
CREATE INDEX idx_conv_update ON conversation(last_updated);

CREATE TABLE conversation_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    message_type VARCHAR(20) NOT NULL,  -- USER, ASSISTANT
    content TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_message_conv FOREIGN KEY (conversation_id) REFERENCES conversation(id)
);

CREATE INDEX idx_message_conv ON conversation_message(conversation_id);
CREATE INDEX idx_message_time ON conversation_message(timestamp);
```

#### 회원 테이블
```sql
CREATE TABLE member (
    number BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,  -- BCrypt
    name VARCHAR(100) NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    address TEXT,
    phone_number VARCHAR(20),
    mobile_number VARCHAR(20),
    introduction TEXT,
    create_at TIMESTAMP DEFAULT NOW(),
    update_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE member_roles (
    member_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    CONSTRAINT fk_member_roles FOREIGN KEY (member_id) REFERENCES member(number)
);
```

#### 사용자 선호도 (JSONB)
```sql
CREATE TABLE user_preference (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL UNIQUE,
    preferences JSONB NOT NULL DEFAULT '{}',
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_pref_member FOREIGN KEY (member_id) REFERENCES member(number)
);

-- JSONB 인덱스
CREATE INDEX idx_pref_jsonb ON user_preference USING GIN (preferences);
```

**선호도 JSON 예시**:
```json
{
  "categoryWeights": {
    "OUTER": 1.5,
    "KNIT": 2.0,
    "TSHIRT": 1.2
  },
  "priceRange": {
    "min": 50000,
    "max": 150000
  },
  "recentViews": [123, 456, 789],
  "clickHistory": {
    "product_123": 5,
    "product_456": 3
  }
}
```

### ER 다이어그램 (주요 관계)

```
┌──────────┐      ┌───────────┐      ┌──────────────┐
│  Member  │──┬───│  Product  │──────│ ProductOption│
└──────────┘  │   └───────────┘      └──────────────┘
              │         │
              │         │ (1:N)
              │   ┌─────▼──────────┐
              │   │  CartItem      │
              │   └────────────────┘
              │
              │ (1:N)
        ┌─────▼──────────┐     (1:N)    ┌────────────────────┐
        │ Conversation   │◄─────────────│ConversationMessage │
        └────────────────┘              └────────────────────┘
              │
              │ (1:N)
        ┌─────▼──────────┐     (1:N)    ┌────────────────┐
        │    Orders      │◄─────────────│   OrderItem    │
        └────────────────┘              └────────────────┘
              │
              │ (1:N)
        ┌─────▼──────────┐
        │PaymentHistory  │
        └────────────────┘
```

## 🚦 실행 방법

### 1. 사전 요구사항
- **Java 17 이상**
- **Docker & Docker Compose**
- **OpenAI API Key** ([API Keys 페이지](https://platform.openai.com/api-keys)에서 발급)

### 2. 환경 설정

**OpenAI API 키 설정**:
```bash
# src/main/resources/application-secrets.properties 파일 생성
echo "openai.api.key=sk-proj-your-key-here" > src/main/resources/application-secrets.properties
echo "chatgpt.api.key=sk-proj-your-key-here" >> src/main/resources/application-secrets.properties
```

### 3. Docker 인프라 실행

```bash
# PostgreSQL + Redis 시작
docker-compose up -d

# 서비스 상태 확인 (모두 healthy 될 때까지 대기)
docker-compose ps

# 로그 확인
docker-compose logs -f db redis
```

**서비스 헬스체크 대기 시간**:
- PostgreSQL: 약 20-30초
- Redis: 약 10초

### 4. 애플리케이션 실행

**방법 1: Gradle로 직접 실행**
```bash
./gradlew bootRun
```

**방법 2: Docker Compose로 전체 실행**
```bash
# 백엔드 포함 전체 실행
docker-compose up -d backend

# 로그 확인
docker-compose logs -f backend
```

**방법 3: IDE에서 실행**
- `CrudApplication.java` 우클릭 → Run

### 5. 접속 정보

| 서비스 | URL | 계정 |
|--------|-----|------|
| **애플리케이션** | http://localhost:8080 | - |
| **헬스체크** | http://localhost:8080/actuator/health | - |
| **PostgreSQL** | localhost:5432 | sungho / 0000 |
| **Redis** | localhost:6379 | - |

### 6. 초기 데이터 설정 (선택)

```bash
# PostgreSQL 접속
docker-compose exec db psql -U sungho -d app

# 테스트 회원 생성
INSERT INTO member (email, password, name, nickname)
VALUES ('test@example.com', '$2a$10$encoded_password', 'Test User', 'testuser');

# 테스트 상품 생성 (임베딩은 자동 생성됨)
INSERT INTO product (name, brand, price, image_url, intro, description, category)
VALUES ('따뜻한 겨울 니트', 'BrandA', 89000, '/img/product1.jpg',
        '부드러운 소재의 겨울 니트', '고급 메리노 울 소재로 제작된 따뜻하고 부드러운 니트입니다.', 'KNIT');
```

### 7. 서비스 중지

```bash
# 전체 중지
docker-compose stop

# 완전 삭제 (데이터 포함)
docker-compose down -v
```

## 🧪 API 테스트

### 헬스체크
```bash
curl http://localhost:8080/actuator/health
```

**응답**:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"}
  }
}
```

### 임베딩 생성 테스트
```bash
curl -X POST http://localhost:8080/api/test/recommendation/embedding \
  -H "Content-Type: application/json" \
  -d '{"text": "warm winter sweater"}'
```

**응답** (1536차원 벡터):
```json
{
  "dimension": 1536,
  "vector": [0.123, -0.456, 0.789, ...],
  "cacheHit": false
}
```

### 텍스트 기반 상품 추천
```bash
curl -X POST http://localhost:8080/api/test/recommendation/text \
  -H "Content-Type: application/json" \
  -d '{"query": "comfortable knit clothing", "limit": 5}'
```

**응답**:
```json
{
  "conversationId": null,
  "aiResponse": "총 3개의 상품을 찾았습니다. 모든 상품을 소개해드릴게요:\n\n• 따뜻한 겨울 니트 (85.3% 일치 - 높은 관련성)\n• 캐시미어 스웨터 (72.1% 일치 - 높은 관련성)\n• 울 카디건 (65.8% 일치 - 높은 관련성)\n\n더 자세한 정보나 다른 상품을 원하시면 언제든 말씀해 주세요!",
  "recommendations": [
    {
      "productId": 1,
      "name": "따뜻한 겨울 니트",
      "score": 0.853
    },
    {
      "productId": 2,
      "name": "캐시미어 스웨터",
      "score": 0.721
    }
  ],
  "recommendedProducts": [...],
  "totalRecommendations": 3
}
```

### 대화형 추천 (실제 사용)
```bash
# 1. 대화 시작
curl -X POST http://localhost:8080/api/conversation/start \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# 응답: {"conversationId": 1}

# 2. 메시지 전송
curl -X POST http://localhost:8080/api/conversation/1/message \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message": "따뜻한 겨울 옷 추천해주세요"}'

# 3. 대화 이력 조회
curl http://localhost:8080/api/conversation/1/messages \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## 📈 성능 지표

### 추천 시스템 성능

| 단계 | 평균 소요 시간 | 비고 |
|------|--------------|------|
| **임베딩 생성** | 300-500ms | OpenAI API 호출 |
| **Redis 캐시 히트** | 5-10ms | 95% 히트율 목표 |
| **벡터 검색** | 10-50ms | HNSW 인덱스 사용 |
| **전체 추천 응답** | 200-400ms | 캐시 미스 시 |
| **전체 추천 응답 (캐시)** | 50-100ms | 캐시 히트 시 |

### 벡터 검색 성능 (pgvector)

| 데이터 규모 | 검색 시간 (avg) | 인덱스 크기 |
|------------|----------------|------------|
| 1,000개 | 5-10ms | ~5MB |
| 10,000개 | 10-20ms | ~50MB |
| 100,000개 | 20-50ms | ~500MB |
| 1,000,000개 | 50-100ms | ~5GB |

**HNSW 인덱스 파라미터**:
- `m = 16`: 각 레이어의 연결 수 (기본값)
- `ef_construction = 64`: 인덱스 구축 시 탐색 크기

### 데이터베이스 연결 풀

**HikariCP 설정** (기본값):
- `maximum-pool-size`: 10
- `minimum-idle`: 5
- `connection-timeout`: 30000ms

**벡터 검색 전용 풀** (`dbTaskExecutor`):
- `core-pool-size`: 4
- `max-pool-size`: 8
- `queue-capacity`: 50

### 확장성

**수평 확장**:
- Spring Boot 다중 인스턴스 배포 가능
- Redis를 통한 세션 공유
- PostgreSQL Read Replica 구성 가능

**수직 확장**:
- JVM 힙 메모리: 1GB (기본) → 4-8GB 권장
- PostgreSQL shared_buffers: 256MB (기본) → 1-2GB 권장
- Redis maxmemory: 512MB (기본) → 2-4GB 권장

## 🔧 개발자 가이드

### 새로운 상품 추가 시 임베딩 생성

**EmbeddingService** (`src/main/java/com/example/crud/ai/embedding/application/EmbeddingService.java`)

```java
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingApiClient embeddingApiClient;
    private final ProductRepository productRepository;

    @Async
    @EventListener
    public void onProductCreated(ProductCreatedEvent event) {
        Long productId = event.getProductId();

        // 상품 정보 조회
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품이 존재하지 않습니다"));

        // 상품 설명 텍스트 생성
        String text = ProductTextBuilder.build(product);

        // 임베딩 생성 (비동기)
        embeddingApiClient.generateEmbeddingAsync(text)
            .thenAccept(embedding -> {
                // PostgreSQL vector 형식으로 변환
                String vectorString = VectorFormatter.formatForPostgreSQL(embedding);

                // 상품에 벡터 저장
                product.setDescriptionVector(vectorString);
                productRepository.save(product);

                log.info("상품 임베딩 저장 완료: productId={}", productId);
            })
            .exceptionally(e -> {
                log.error("상품 임베딩 생성 실패: productId={}", productId, e);
                return null;
            });
    }
}
```

### 커스텀 추천 로직 추가

**1. 카테고리 필터링 추가**

```java
public CompletableFuture<List<ProductMatch>> getRecommendationsByCategory(
        String message, String category, int limit) {

    return vectorService.findSimilarProducts(message, limit * 2)
        .thenApply(results ->
            results.stream()
                .filter(p -> p.category().equals(category))
                .limit(limit)
                .collect(Collectors.toList())
        );
}
```

**2. 가격대 필터링 추가**

```java
public CompletableFuture<List<ProductMatch>> getRecommendationsByPriceRange(
        String message, int minPrice, int maxPrice, int limit) {

    return recommendationEngine.getRecommendations(message, limit * 3)
        .thenApply(results ->
            results.stream()
                .filter(p -> {
                    Product product = productRepository.findById(p.productId()).orElse(null);
                    return product != null
                        && product.getPrice() >= minPrice
                        && product.getPrice() <= maxPrice;
                })
                .limit(limit)
                .collect(Collectors.toList())
        );
}
```

**3. 사용자 선호도 가중치 적용**

```java
@Service
public class PersonalizedRecommendationService {

    public CompletableFuture<List<ProductMatch>> getPersonalizedRecommendations(
            Long memberId, String message, int limit) {

        // 사용자 선호도 조회
        UserPreference preference = userPreferenceRepository.findByMemberId(memberId)
            .orElse(UserPreference.createDefault());

        // 추천 생성
        return recommendationEngine.getRecommendations(message, limit * 2)
            .thenApply(results ->
                results.stream()
                    .map(match -> applyUserPreference(match, preference))
                    .sorted(Comparator.comparing(ProductMatch::score).reversed())
                    .limit(limit)
                    .collect(Collectors.toList())
            );
    }

    private ProductMatch applyUserPreference(ProductMatch match, UserPreference preference) {
        Product product = productRepository.findById(match.productId()).orElse(null);
        if (product == null) return match;

        // 카테고리 가중치 적용
        double categoryWeight = preference.getCategoryWeight(product.getCategory());
        double adjustedScore = match.score() * categoryWeight;

        return new ProductMatch(match.productId(), match.name(), adjustedScore);
    }
}
```

### 테스트 작성

**ProductVectorServiceTest** (`src/test/java/com/example/crud/ai/ProductVectorServiceTest.java`)

```java
@SpringBootTest
@Testcontainers
class ProductVectorServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
        .withInitScript("init-pgvector.sql");

    @Autowired
    private ProductVectorService vectorService;

    @Test
    void 벡터_유사도_검색_테스트() {
        // given
        String query = "warm winter sweater";
        int limit = 5;

        // when
        CompletableFuture<List<ProductSimilarity>> future =
            vectorService.findSimilarProducts(query, limit);
        List<ProductSimilarity> results = future.join();

        // then
        assertThat(results).isNotEmpty();
        assertThat(results).hasSizeLessThanOrEqualTo(limit);
        assertThat(results.get(0).similarity()).isGreaterThan(0.3);
    }
}
```

### 배포

**1. JAR 빌드**
```bash
./gradlew clean bootJar
```

**2. Docker 이미지 빌드**
```bash
docker build -t shopping-mall:latest -f docker/Dockerfile .
```

**3. Docker Compose로 배포**
```bash
docker-compose -f docker-compose.prod.yml up -d
```

**4. 환경 변수 설정 (프로덕션)**
```bash
export SPRING_PROFILES_ACTIVE=prod
export OPENAI_API_KEY=sk-proj-...
export JWT_SECRET=your-secret-key
export SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/app
export SPRING_DATASOURCE_PASSWORD=strong-password
```

## 🔍 트러블슈팅

### 1. OpenAI API 호출 실패

**증상**:
```
임베딩 서비스를 일시적으로 사용할 수 없습니다
```

**해결 방법**:
```bash
# API 키 확인
cat src/main/resources/application-secrets.properties

# API 키 테스트
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer YOUR_KEY"

# 로그 확인
docker-compose logs -f backend | grep -i "openai"
```

### 2. pgvector 인덱스 오류

**증상**:
```
ERROR: operator does not exist: vector <=>
```

**해결 방법**:
```bash
# pgvector 확장 확인
docker-compose exec db psql -U sungho -d app -c "SELECT * FROM pg_extension WHERE extname = 'vector';"

# 없으면 설치
docker-compose exec db psql -U sungho -d app -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 인덱스 재생성
docker-compose exec db psql -U sungho -d app -c "CREATE INDEX idx_product_vector ON product USING hnsw (description_vector vector_cosine_ops);"
```

### 3. Redis 연결 실패

**증상**:
```
Unable to connect to Redis
```

**해결 방법**:
```bash
# Redis 상태 확인
docker-compose ps redis

# Redis 접속 테스트
docker-compose exec redis redis-cli ping

# Redis 로그 확인
docker-compose logs -f redis

# Redis 재시작
docker-compose restart redis
```

### 4. 메모리 부족

**증상**:
```
java.lang.OutOfMemoryError: Java heap space
```

**해결 방법**:
```bash
# JVM 힙 메모리 증가
export JAVA_OPTS="-Xms1g -Xmx2g"
./gradlew bootRun

# 또는 docker-compose.yml 수정
environment:
  JAVA_OPTS: "-Xms1g -Xmx2g -XX:+UseG1GC"
```

### 5. 임베딩 생성 속도 느림

**해결 방법**:
```properties
# application.properties
# 스레드 풀 크기 증가
spring.task.execution.pool.max-size=20

# Redis 타임아웃 증가
spring.data.redis.timeout=5000ms

# WebClient 타임아웃 증가
chatgpt.timeout-sec=10
```

## 📚 참고 자료

### 공식 문서
- [Spring Boot 3.1 Documentation](https://docs.spring.io/spring-boot/docs/3.1.x/reference/html/)
- [OpenAI API Documentation](https://platform.openai.com/docs/introduction)
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [PostgreSQL Vector Documentation](https://github.com/pgvector/pgvector#vector-functions)

### 관련 논문 및 아티클
- [Hierarchical Navigable Small World (HNSW)](https://arxiv.org/abs/1603.09320)
- [OpenAI Embeddings Guide](https://platform.openai.com/docs/guides/embeddings)
- [Vector Similarity Search at Scale](https://www.pinecone.io/learn/vector-similarity/)

## 🤝 기여 방법

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 라이센스

이 프로젝트는 MIT 라이센스 하에 배포됩니다.

## 📞 문의사항

프로젝트 관련 문의사항이 있으시면 [Issues](https://github.com/your-repo/issues)를 통해 연락해 주세요.

---

**🎯 핵심 가치**: AI 기술을 활용한 개인화된 쇼핑 경험 제공

**Made with ❤️ by [Your Name]**
