# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an **AI-powered conversational shopping mall recommendation system** built with Spring Boot. The system provides personalized product recommendations through conversational AI, vector similarity search, and hybrid recommendation algorithms.

**Core Technology Stack:**
- **Backend**: Spring Boot 3.1.4 with Spring Security, JPA, WebFlux
- **AI/ML**: OpenAI GPT-4 API, HuggingFace API for embeddings (384-dim), Simple fallback embedding service
- **Databases**: PostgreSQL + pgvector, Redis for caching
- **Infrastructure**: Docker Compose with simplified service architecture

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
docker-compose up -d db redis backend

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

### AI Services Configuration
```bash
# Configure OpenAI API (preferred)
# Add to src/main/resources/application-secrets.properties:
openai.api.key=sk-proj-your-key-here

# OR configure HuggingFace API (free alternative)
huggingface.api.key=hf_your-key-here

# Test embedding service
curl -X POST http://localhost:8080/api/ai/embedding \
  -H "Content-Type: application/json" \
  -d '{"text": "sample product description"}'
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

# Application metrics
curl http://localhost:8080/actuator/metrics

# Redis connection
docker-compose exec redis redis-cli ping

# Check embedding service status
curl http://localhost:8080/actuator/health | grep embedding
```

## Architecture Overview

### High-Level System Flow
The system follows a **simplified AI-powered architecture** with the following data flow:

1. **User Interaction** → Spring Boot Controllers
2. **Conversation Processing** → ConversationService → Database Storage
3. **AI Integration**:
   - **Embedding Generation** → SimpleEmbeddingService → OpenAI/HuggingFace APIs
   - **Preference Analysis** → ChatGPT API Integration
   - **Vector Search** → pgvector similarity search
4. **Caching Strategy** → Redis for performance optimization

### Key Architectural Patterns

**API-First AI Integration**: Direct integration with external AI services
- `SimpleEmbeddingService` handles OpenAI and HuggingFace API calls
- Fallback to keyword-based embeddings when APIs unavailable
- Configurable timeout and retry mechanisms

**Circuit Breaker Pattern**: Protects against cascade failures
- `ResilienceConfig` defines service-specific circuit breakers
- ChatGPT Service: configurable failure threshold and timeout
- External API fallbacks prevent complete service disruption

**Multi-Layer Caching Strategy**:
- **L1 Cache**: In-memory embedding cache (ConcurrentHashMap)
- **L2 Cache**: Redis distributed cache for recommendations and preferences
- **Application Cache**: Spring `@Cacheable` for frequently accessed data

**Vector Similarity Search**:
- Products stored with 384-dimensional embeddings in `product.description_vector`
- Real-time similarity search using pgvector with cosine similarity
- Hybrid scoring combines vector similarity + preference matching + trending factors

### Core Domain Services

**ConversationService**: Manages user interactions and message flow
- `ConversationCommandService`: Creates conversations and messages
- `ConversationQueryService`: Retrieves conversation history

**RecommendationService**: Orchestrates the recommendation pipeline
- `IntegratedRecommendationService`: Main recommendation orchestrator
- `ConversationalRecommendationService`: Handles conversational context
- `RecommendationCacheService`: Multi-tier caching strategy

**EmbeddingService**: Direct API integration for vector embeddings
- `SimpleEmbeddingService`: Handles OpenAI, HuggingFace, and fallback embeddings
- `ProductEmbeddingService`: Batch processing for product embeddings
- In-memory caching with configurable size limits

**AI/ML Integration**:
- Direct OpenAI GPT-4 API integration for preference analysis and conversational AI
- HuggingFace API for embedding generation (sentence-transformers models)
- Keyword-based fallback embedding system for offline operation

### Database Schema Key Points

**PostgreSQL Tables**:
- `product`: Core product data with `description_vector vector(384)` for similarity search
- `conversation` + `conversation_message`: Chat history with vector embeddings
- `user_preference`: JSONB storage for dynamic preference learning
- `outbox`: Event store for reliable message publishing

**Redis Structure**:
- DB 0: Java application cache (recommendations, preferences, sessions)
- Distributed caching for performance optimization
- TTL-based cache expiration strategies

## Development Guidelines

### Running the Complete System
**IMPORTANT**: Always start Docker services before the Spring Boot application:

1. `docker-compose up -d` (wait ~30 seconds for all services to be healthy)
2. Verify services: `docker-compose ps` 
3. Configure AI API keys in `application-secrets.properties`
4. `./gradlew bootRun`

The application **cannot start** without PostgreSQL and Redis. AI features require OpenAI or HuggingFace API keys.

### Configuration Management
- **Local Development**: `application.properties` 
- **Docker Environment**: `application-docker.properties` (activated by `SPRING_PROFILES_ACTIVE=docker`)
- **Secrets**: `application-secrets.properties` (contains API keys, not in version control)

### Key Configuration Properties
```properties
# OpenAI API Integration (primary)
openai.api.key=sk-proj-...  # In secrets file

# HuggingFace API Integration (fallback)
huggingface.api.key=hf_...  # In secrets file

# ChatGPT Integration
chatgpt.api.key=sk-proj-...  # Same as OpenAI key
chatgpt.model=gpt-4o
chatgpt.timeout-sec=6
chatgpt.rate-limit-per-sec=8

# Redis Configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.database=0
spring.data.redis.timeout=2000ms
```

### Testing Strategy
- **Unit Tests**: Mock external dependencies (OpenAI API, HuggingFace API, ChatGPT API)
- **Integration Tests**: Use `@SpringBootTest` with test containers
- **Embedding Tests**: Test fallback mechanisms and similarity calculations

### Common Development Scenarios

**Adding New Embedding Sources**:
1. Extend `SimpleEmbeddingService` with new API integration
2. Add configuration properties for new service
3. Update fallback chain in `generateEmbedding()` method

**Modifying Recommendation Algorithms**:
1. Update `IntegratedRecommendationService` with new scoring logic
2. Modify vector similarity calculations in recommendation services
3. Update cache keys and TTL settings if needed

**Adding New AI Features**:
1. Extend ChatGPT integration with new prompts and models
2. Add new endpoints in AI-related controllers
3. Update circuit breaker configurations for new external services

### Performance Monitoring
- **Application Metrics**: http://localhost:8080/actuator/metrics
- **Application Health**: http://localhost:8080/actuator/health
- **Embedding Cache Stats**: Monitor hit rates and cache size in logs
- **API Rate Limiting**: Monitor ChatGPT and external API usage

### Troubleshooting Common Issues

**OpenAI API Connection Failures**:
- Verify API key is set in `application-secrets.properties`
- Check rate limits: ChatGPT has 8 requests/second limit
- Monitor application logs for API timeout errors
- System falls back to HuggingFace API or keyword embedding

**HuggingFace API Issues**:
- Verify API key configuration
- Check if inference endpoint is available
- Monitor timeout settings (10 seconds default)
- System falls back to keyword-based embedding

**Vector Search Performance**:
- Verify pgvector index exists: `\d+ product` in PostgreSQL
- Monitor query performance in application metrics
- Check if embeddings are generated for products: `SELECT COUNT(*) FROM product WHERE description_vector IS NOT NULL;`

**Cache Issues**:
- Check Redis connectivity: `docker-compose exec redis redis-cli ping`
- Monitor embedding cache size in SimpleEmbeddingService logs
- Clear Redis cache: `docker-compose exec redis redis-cli FLUSHDB`
- Clear in-memory embedding cache: restart application