# 🚀 프로젝트 빠른 시작 가이드

## ⚡ 한 번에 시작하기
```bash
# 1. 전체 Docker 스택 시작
docker-compose up -d

# 2. 서비스 상태 확인 (30초 대기 후)
docker-compose ps

# 3. Spring Boot 애플리케이션 실행
./gradlew bootRun
```

## 🔍 서비스 접속 확인
- **메인 애플리케이션**: http://localhost:8080
- **Grafana 모니터링**: http://localhost:3000 (admin/admin)
- **Elasticsearch**: http://localhost:9200
- **ML Service**: http://localhost:8000/docs

## ❌ 문제 해결

### Docker 서비스가 시작되지 않을 때:
```bash
# 포트 충돌 확인
netstat -ano | findstr :5432
netstat -ano | findstr :6379
netstat -ano | findstr :9092

# Docker 완전 재시작
docker-compose down -v
docker-compose up -d
```

### Spring Boot 실행 오류:
```bash
# 1. 모든 Docker 서비스 상태 확인
docker-compose ps

# 2. 헬스체크
curl http://localhost:5432  # PostgreSQL
curl http://localhost:6379  # Redis
curl http://localhost:9092  # Kafka
curl http://localhost:8000/healthz  # ML Service

# 3. 로그 확인
docker-compose logs db
docker-compose logs redis
docker-compose logs kafka
docker-compose logs embedding-service
```

## 📊 정상 동작 확인
1. **데이터베이스**: 테이블 자동 생성 확인
2. **Kafka**: 토픽 생성 및 메시지 처리
3. **ML Service**: 임베딩 생성 테스트
4. **Redis**: 캐싱 동작 확인
5. **웹 UI**: 추천 시스템 동작 테스트

## 🛑 종료하기
```bash
# 애플리케이션만 종료 (Ctrl+C)

# Docker 서비스 종료 (데이터 보존)
docker-compose stop

# 완전 정리 (데이터 삭제 주의!)
docker-compose down -v
```