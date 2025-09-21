# 🔥 실제 결제 연동 가이드

## 📖 현재 상황
- **문제**: `DummyPaymentGatewayClient`가 항상 성공을 반환하여 실제 결제가 처리되지 않음
- **해결**: 실제 Toss Payments API 연동으로 전환

## 🚀 단계별 설정 방법

### 1단계: Toss Payments 계정 설정
1. [Toss Payments 개발자 센터](https://developers.tosspayments.com/) 회원가입
2. 애플리케이션 등록
3. **Secret Key** 발급 받기
   - 테스트: `test_sk_ㅁㅁㅁㅁ...`
   - 운영: `live_sk_ㅁㅁㅁㅁ...`

### 2단계: application.properties 설정

#### 🔧 개발/테스트 환경 (더미 결제)
```properties
# 더미 결제 모드 (기본값)
payment.gateway.mode=dummy
```

#### 🔧 운영 환경 (실제 결제)
```properties
# 실제 결제 모드
payment.gateway.mode=real

# Toss Payments Secret Key (필수)
toss.payments.secret-key=test_sk_당신의_시크릿_키

# 운영 환경에서는 live_sk_로 시작하는 키 사용
# toss.payments.secret-key=live_sk_당신의_시크릿_키
```

### 3단계: 프론트엔드 클라이언트 키 업데이트

**현재 (`productBuy.html:425`)**:
```javascript
const clientKey = "test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm"; // 예제 키
```

**운영 환경으로 변경**:
```javascript
const clientKey = "실제_발급받은_클라이언트_키";
```

### 4단계: 결제 플로우 업데이트

#### 현재 플로우:
```
프론트엔드 결제 → 백엔드 더미 처리 → 항상 성공
```

#### 업데이트된 플로우:
```
프론트엔드 결제 → Toss API 실제 처리 → 백엔드 확인 → 성공/실패
```

## 📝 배포 체크리스트

### ✅ 개발 환경에서 테스트
- [ ] `payment.gateway.mode=dummy`로 기존 기능 동작 확인
- [ ] `payment.gateway.mode=real`로 설정 후 테스트 키로 실제 결제 테스트

### ✅ 운영 환경 배포 전
- [ ] Toss Payments 실 계약 완료
- [ ] 운영용 Secret Key 발급 완료
- [ ] 프론트엔드 클라이언트 키 운영용으로 변경
- [ ] `payment.gateway.mode=real` 설정
- [ ] 결제 테스트 (소액) 완료

## 🔧 추가 설정 옵션

### environment별 설정 분리
```properties
# application-dev.properties (개발)
payment.gateway.mode=dummy

# application-prod.properties (운영)
payment.gateway.mode=real
toss.payments.secret-key=${TOSS_SECRET_KEY}
```

### Docker 환경변수 설정
```bash
# Docker 실행 시
docker run -e TOSS_SECRET_KEY=실제키 -e SPRING_PROFILES_ACTIVE=prod your-app
```

## ⚠️ 보안 주의사항

1. **Secret Key 절대 노출 금지**
   - GitHub 등 공개 저장소에 커밋 금지
   - 환경변수 또는 암호화된 설정 파일 사용

2. **로그 출력 주의**
   - Secret Key, 거래 정보 로그 출력 금지
   - 민감 정보 마스킹 처리

3. **HTTPS 필수**
   - 운영 환경에서는 반드시 HTTPS 사용
   - SSL 인증서 설정 필수

## 🎯 테스트 방법

### 개발 환경 테스트
```bash
# 1. 더미 모드로 기존 기능 확인
curl -X POST http://localhost:8080/payment/process \
  -H "Content-Type: application/json" \
  -d '{"orderId":1,"amount":10000,"paymentMethod":"CREDIT_CARD"}'

# 2. 실제 모드로 전환 후 테스트
# application.properties에서 payment.gateway.mode=real 설정 후 재시작
```

### 운영 환경 배포 후
1. 소액 결제 테스트 (100원)
2. 결제 취소 테스트
3. 실패 케이스 테스트

## 🔍 트러블슈팅

### 자주 발생하는 오류
1. **401 Unauthorized**: Secret Key 확인
2. **400 Bad Request**: 요청 데이터 형식 확인
3. **Network Error**: API 엔드포인트 URL 확인

### 로그 확인
```bash
# 결제 관련 로그 확인
grep -i "payment\|toss" logs/application.log
```

## 📞 지원
- Toss Payments 개발자 문의: [developers.tosspayments.com](https://developers.tosspayments.com/)
- 기술 문의: 개발팀 내부 채널