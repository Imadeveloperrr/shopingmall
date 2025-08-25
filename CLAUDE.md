# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an **AI-powered conversational shopping mall recommendation system** built with a microservices architecture. The system provides real-time, personalized product recommendations through conversational AI, vector similarity search, and hybrid recommendation algorithms.

**Core Technology Stack:**
- **Backend**: Spring Boot 3.1.4 with Spring Security, JPA, WebFlux, Kafka
- **AI/ML**: FastAPI service with Sentence Transformers (384-dim embeddings), OpenAI GPT-4
- **Databases**: PostgreSQL + pgvector, Redis, Elasticsearch  
- **Infrastructure**: Docker Compose with Kafka, Zookeeper, Prometheus, Grafana

## Essential Development Commands

### Build and Run
```bash
# Build the project
./gradlew build

# Run the main application (requires Docker services running first)
./gradlew bootRun

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=docker'

# Build JAR for Docker
./gradlew bootJar
```

### Docker Operations
```bash
# Start all infrastructure services (REQUIRED before running Spring Boot)
docker-compose up -d

# Start individual services
docker-compose up -d db redis kafka zookeeper elasticsearch embedding-service

# Check service health
docker-compose ps
docker-compose logs -f [service-name]

# Stop services (preserves data)
docker-compose stop

# Complete cleanup (destroys data volumes)
docker-compose down -v
```

### Testing
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.example.crud.MemberControllerTest"

# Run tests with coverage
./gradlew test jacocoTestReport
```

### ML Service (Python FastAPI)
```bash
# Navigate to ML service directory
cd ml-service

# Install dependencies
pip install -r requirements.txt

# Run ML service locally (for development)
uvicorn ml_app.main:app --reload --port 8000

# Run tests
pytest tests/
```

### Database Operations
```bash
# Connect to PostgreSQL container
docker-compose exec db psql -U sungho -d app

# Check vector extension
docker-compose exec db psql -U sungho -d app -c "SELECT * FROM pg_extension WHERE extname = 'vector';"

# View product vectors
docker-compose exec db psql -U sungho -d app -c "SELECT number, name, description_vector IS NOT NULL as has_vector FROM product LIMIT 5;"
```

### Monitoring and Health Checks
```bash
# Application health
curl http://localhost:8080/actuator/health

# ML service health  
curl http://localhost:8000/healthz

# Elasticsearch health
curl http://localhost:9200/_cluster/health

# Redis connection
docker-compose exec redis redis-cli ping

# View Grafana dashboard
open http://localhost:3000 (admin/admin)
```

## Architecture Overview

### High-Level System Flow
The system follows an **event-driven microservices architecture** with the following data flow:

1. **User Interaction** → Spring Boot Controllers
2. **Message Processing** → ConversationService → Database + Outbox Pattern
3. **Event Publishing** → OutboxDispatcher → Kafka Topics
4. **Parallel Processing**:
   - **Elasticsearch Indexing** (MsgCreatedConsumer)
   - **Preference Analysis** (PreferenceAnalysisConsumer → ChatGPT API)
   - **Recommendation Updates** (RecommendationEventProcessor)
5. **AI Recommendation** → ML Service (FastAPI) → Vector Search (pgvector)

### Key Architectural Patterns

**Outbox Pattern**: Ensures transactional safety for event publishing
- `Outbox` entity stores events in the same transaction as business data
- `OutboxDispatcher` publishes events to Kafka asynchronously
- Guarantees at-least-once delivery with idempotency

**Circuit Breaker Pattern**: Protects against cascade failures
- `ResilienceConfig` defines service-specific circuit breakers
- ML Service: 50% failure threshold, 30s wait time
- ChatGPT Service: 40% failure threshold, 20s wait time
- Redis Cache: 70% failure threshold, 5s wait time

**Multi-Layer Caching Strategy**:
- **L1 Cache**: Spring `@Cacheable` (JVM level)
- **L2 Cache**: Redis distributed cache (application level)  
- **L3 Cache**: ML Service internal cache (model level)

**Vector Similarity Search**:
- Products stored with 384-dimensional embeddings in `product.description_vector`
- Real-time similarity search using pgvector with IVFFlat indexing
- Hybrid scoring combines vector similarity (40%) + preference matching (60%)

### Core Domain Services

**ConversationService**: Manages user interactions and message flow
- `ConversationCommandService`: Creates conversations and messages
- `ConversationQueryService`: Retrieves conversation history
- `MsgCreatedConsumer`: Syncs messages to Elasticsearch

**RecommendationService**: Orchestrates the recommendation pipeline
- `IntegratedRecommendationService`: Main recommendation orchestrator
- `ConversationalRecommendationService`: Handles conversational context
- `RecommendationCacheService`: Multi-tier caching strategy

**EmbeddingService**: Integrates with Python ML service
- `EmbeddingClient`: HTTP client with circuit breaker and retry logic
- `ProductEmbeddingService`: Batch processing for product embeddings
- `EmbeddingBatchScheduler`: Scheduled embedding updates

**AI/ML Integration**:
- FastAPI service (`ml-service/`) provides embedding generation
- OpenAI GPT-4 integration for preference analysis and conversational AI
- Real-time user preference learning through Kafka events

### Database Schema Key Points

**PostgreSQL Tables**:
- `product`: Core product data with `description_vector vector(384)` for similarity search
- `conversation` + `conversation_message`: Chat history with vector embeddings
- `user_preference`: JSONB storage for dynamic preference learning
- `outbox`: Event store for reliable message publishing

**Redis Structure**:
- DB 0: Java application cache (recommendations, preferences, sessions)
- DB 1: Python ML service cache (embeddings, model cache)
- Separate namespaces prevent cache collisions

**Kafka Topics**:
- `conv-msg-created`: New message events
- `product-viewed`, `order-completed`: User behavior events
- `recommendation-events`: Recommendation system events

## Development Guidelines

### Running the Complete System
**IMPORTANT**: Always start Docker services before the Spring Boot application:

1. `docker-compose up -d` (wait ~30 seconds for all services to be healthy)
2. Verify services: `docker-compose ps` 
3. `./gradlew bootRun`

The application **cannot start** without the required infrastructure services (PostgreSQL, Redis, Kafka, Elasticsearch, ML Service).

### Configuration Management
- **Local Development**: `application.properties` 
- **Docker Environment**: `application-docker.properties` (activated by `SPRING_PROFILES_ACTIVE=docker`)
- **Secrets**: `application-secrets.properties` (contains API keys, not in version control)

### Key Configuration Properties
```properties
# ML Service Integration
embedding.service.url=http://localhost:8000  # Local dev
embedding.service.url=http://embedding-service:8000  # Docker

# OpenAI Integration
chatgpt.api.key=sk-proj-...  # In secrets file
chatgpt.model=gpt-4o
chatgpt.timeout-sec=6

# Circuit Breaker Tuning
resilience4j.circuitbreaker.instances.embeddingService.failure-rate-threshold=50
resilience4j.bulkhead.instances.embeddingService.max-concurrent-calls=10
```

### Testing Strategy
- **Unit Tests**: Mock external dependencies (ML Service, ChatGPT API)
- **Integration Tests**: Use `@SpringBootTest` with test containers
- **ML Service Tests**: pytest with FastAPI TestClient

### Common Development Scenarios

**Adding New Recommendation Features**:
1. Extend `IntegratedRecommendationService` with new scoring algorithms
2. Update `RecommendationCacheService` for new cache keys
3. Add corresponding metrics in `RecommendationSystemMonitor`

**Modifying User Preference Analysis**:
1. Update `PreferenceAnalysisConsumer` for new analysis logic
2. Modify ChatGPT prompts in the preference analysis method
3. Update `UserPreference` entity JSON schema if needed

**Adding New Event Types**:
1. Create new Kafka topic in `docker-compose.yml`
2. Add corresponding consumer in the infrastructure layer
3. Update `OutboxDispatcher` if new outbox events are needed

### Performance Monitoring
- **Application Metrics**: http://localhost:8080/actuator/metrics
- **Prometheus**: http://localhost:9090
- **Grafana Dashboards**: http://localhost:3000
- **ML Service Stats**: http://localhost:8000/stats

### Troubleshooting Common Issues

**ML Service Connection Failures**:
- Check if embedding-service container is running: `docker-compose ps`
- Verify health: `curl http://localhost:8000/healthz`
- Check circuit breaker state in application logs

**Kafka Consumer Lag**:
- Monitor consumer groups: `docker-compose exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list`
- Check OutboxDispatcher for failed message processing

**Vector Search Performance**:
- Verify pgvector index exists: `\d+ product` in PostgreSQL
- Monitor query performance in application metrics
- Adjust IVFFlat `lists` parameter if needed

**Cache Issues**:
- Check Redis connectivity: `docker-compose exec redis redis-cli ping`
- Monitor cache hit rates in Grafana dashboards
- Clear cache: `docker-compose exec redis redis-cli FLUSHDB`