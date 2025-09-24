# PostgreSQL Vector 저장 문제 해결 과정 문서

## 문제 상황

Spring Boot 기반 쇼핑몰 시스템에서 상품 등록 시 OpenAI 임베딩을 생성하여 PostgreSQL의 `description_vector` 필드에 저장하는 기능을 구현했으나, 상품 등록 후 해당 필드가 지속적으로 NULL 값으로 남아있는 문제가 발생했다.

### 기술 스택
- **Backend**: Spring Boot 3.1.4, Spring Data JPA
- **Database**: PostgreSQL with pgvector extension
- **AI Integration**: OpenAI text-embedding-3-small (1536차원)
- **Architecture Pattern**: Event-driven + Asynchronous processing

## 문제 분석 과정

### 1차 진단: 비동기 처리 실행 여부 확인

첫 번째 의심은 비동기 임베딩 생성 메서드가 아예 호출되지 않는 것이었다. 로그 분석 결과 `ProductEmbeddingService`의 비동기 메서드는 정상적으로 실행되고 있었지만, 임베딩 생성 후 데이터베이스 업데이트가 실패하고 있음을 확인했다.

### 2차 진단: 트랜잭션 타이밍 문제 발견

문제의 근본 원인이 트랜잭션 커밋 타이밍과 관련되어 있음을 파악했다. `@Async` 어노테이션으로 표시된 임베딩 생성 메서드가 주 트랜잭션이 커밋되기 전에 실행되어, 아직 데이터베이스에 존재하지 않는 상품을 조회하려 시도하는 레이스 컨디션이 발생하고 있었다.

## 해결 시도 과정

### 1차 시도: Thread.sleep을 이용한 지연 처리

**구현 내용**
```java
@Async
@Transactional
public void createAndSaveEmbeddingAsync(Long productNumber) {
    try {
        Thread.sleep(1000); // 임시 지연
        // ... 임베딩 생성 로직
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
```

**문제점 분석**
- 임의의 1초 지연은 근본적 해결책이 아님
- 시스템 성능 저하 및 스레드 블로킹 발생
- 트랜잭션 커밋이 1초보다 오래 걸릴 경우 여전히 실패 가능

**사용자 피드백**
"Thread.sleep 1000이 이 상황에서 근본적인 문제를 해결할 수 있는 최선의 선택인가? 스레드를 1초 동안 멈춰버리는 것은 상당히 좋지 않아 보인다."

### 2차 시도: Event-driven Architecture 도입

**설계 변경**
1. `ProductCreatedEvent` 이벤트 클래스 생성
2. 상품 저장 후 이벤트 발행
3. `@TransactionalEventListener`를 통한 트랜잭션 커밋 후 처리

**구현 내용**
```java
// 이벤트 클래스
@Getter
@AllArgsConstructor
public class ProductCreatedEvent {
    private final Long productId;
}

// 상품 서비스에서 이벤트 발행
Product savedProduct = productRepository.save(product);
eventPublisher.publishEvent(new ProductCreatedEvent(savedProduct.getNumber()));

// 이벤트 리스너
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleProductCreatedEvent(ProductCreatedEvent event) {
    // 임베딩 생성 로직
}
```

**결과**
트랜잭션 타이밍 문제는 해결되었으나, 새로운 문제가 발생했다.

### 3차 문제: Repository 메서드 설정 오류

**발견된 문제들**

1. **@Modifying 어노테이션 누락**
```java
// 수정 전: UPDATE 쿼리 실행 불가
@Query(value = "UPDATE product SET description_vector = :vectorString WHERE number = :productId", nativeQuery = true)
int updateDescriptionVector(@Param("productId") Long productId, @Param("vectorString") String vectorString);

// 수정 후: @Modifying 추가
@Modifying
@Query(value = "UPDATE product SET description_vector = :vectorString WHERE number = :productId", nativeQuery = true)
int updateDescriptionVector(@Param("productId") Long productId, @Param("vectorString") String vectorString);
```

2. **잘못된 @Param 임포트**
```java
// 잘못된 임포트: MyBatis용
import org.apache.ibatis.annotations.Param;

// 올바른 임포트: Spring Data JPA용
import org.springframework.data.repository.query.Param;
```

### 4차 문제: TransactionRequiredException

**문제 상황**
이벤트 리스너에서 UPDATE 쿼리 실행 시 다음과 같은 예외 발생:
```
jakarta.persistence.TransactionRequiredException: Executing an update/delete query
```

**원인 분석**
`@Async`와 `@TransactionalEventListener`를 함께 사용할 때, 비동기 스레드에서는 원래 트랜잭션 컨텍스트가 전파되지 않아 UPDATE/DELETE 쿼리 실행이 불가능한 상황이었다.

### 최종 해결: 독립적 트랜잭션 생성

**해결 방법**
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void createAndSaveEmbedding(Product product) {
    // 임베딩 생성 및 저장 로직
}
```

**핵심 포인트**
- `Propagation.REQUIRES_NEW`: 기존 트랜잭션과 무관하게 새로운 독립적인 트랜잭션 생성
- 비동기 스레드에서도 자체적인 트랜잭션 컨텍스트 보장
- UPDATE 쿼리 정상 실행 가능

**추가 최적화**
```java
@Modifying(clearAutomatically = true)
@Query(value = "UPDATE product SET description_vector = :vectorString WHERE number = :productId", nativeQuery = true)
int updateDescriptionVector(@Param("productId") Long productId, @Param("vectorString") String vectorString);
```
`clearAutomatically = true` 옵션으로 영속성 컨텍스트 자동 클리어 설정

## 최종 코드 구조

### ProductEmbeddingService.java
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductEmbeddingService {

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductCreatedEvent(ProductCreatedEvent event) {
        try {
            log.debug("상품 생성 이벤트 수신: productId={}", event.getProductId());
            Product product = productRepository.findById(event.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + event.getProductId()));
            createAndSaveEmbedding(product);
        } catch (Exception e) {
            log.error("상품 임베딩 생성 실패: productId={}", event.getProductId(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAndSaveEmbedding(Product product) {
        // 임베딩 생성 및 저장 로직
    }
}
```

### ProductRepository.java
```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE product
        SET description_vector = :vectorString
        WHERE number = :productId
        """, nativeQuery = true)
    int updateDescriptionVector(@Param("productId") Long productId, @Param("vectorString") String vectorString);
}
```

### ProductServiceImpl.java
```java
@Service
public class ProductServiceImpl implements ProductService {

    public ProductResponseDto getAddProduct(ProductDto productDto, MultipartFile image) {
        // ... 상품 저장 로직
        Product savedProduct = productRepository.save(product);

        // 트랜잭션 커밋 후 임베딩 생성하기 위한 이벤트 발행
        eventPublisher.publishEvent(new ProductCreatedEvent(savedProduct.getNumber()));

        return convertToProductResponseDTO(savedProduct);
    }
}
```

## 결론

### 해결된 아키텍처
1. **상품 등록**: 동기적 데이터베이스 저장
2. **이벤트 발행**: 트랜잭션 커밋 후 `ProductCreatedEvent` 발행
3. **임베딩 생성**: 독립적 트랜잭션에서 비동기 처리
4. **벡터 저장**: PostgreSQL TEXT 필드에 JSON 형태로 저장 후 검색 시 vector 타입으로 캐스팅

### 학습된 교훈
1. **트랜잭션 전파**: 비동기 처리에서는 트랜잭션 컨텍스트가 자동으로 전파되지 않음
2. **이벤트 기반 설계**: 복잡한 비즈니스 로직의 분리와 확장성 확보
3. **JPA 어노테이션**: `@Modifying`, `@Param` 등의 정확한 사용법 중요
4. **디버깅 접근법**: 성능 문제보다는 근본 원인 해결 우선

### 성능 개선 효과
- **트랜잭션 타이밍**: 레이스 컨디션 완전 해결
- **시스템 안정성**: Thread.sleep 제거로 성능 향상
- **확장성**: 이벤트 기반 아키텍처로 추가 기능 확장 용이

이 문제 해결 과정을 통해 Spring Boot의 트랜잭션 관리, 이벤트 기반 아키텍처, 그리고 비동기 처리에 대한 깊이 있는 이해를 얻을 수 있었다.

## 참고사항

### 관련 에러 메시지
```
jakarta.persistence.TransactionRequiredException: Executing an update/delete query
org.springframework.dao.InvalidDataAccessApiUsageException: Executing an update/delete query
```

### 핵심 어노테이션
- `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
- `@Transactional(propagation = Propagation.REQUIRES_NEW)`
- `@Modifying(clearAutomatically = true)`
- `@Async`

### 테스트 시나리오
1. 상품 등록 요청
2. 상품 데이터베이스 저장 확인
3. 임베딩 생성 로그 확인
4. `description_vector` 필드 NULL이 아닌 값 확인
5. 벡터 유사도 검색 기능 정상 작동 확인