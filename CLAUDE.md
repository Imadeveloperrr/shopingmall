# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI-enhanced shopping mall system built with Spring Boot 3.1.4. The system integrates conversational AI and vector-based product recommendations through direct OpenAI API integration.

**Technology Stack:**
- **Backend**: Spring Boot 3.1.4, Spring Security, JPA, QueryDSL
- **AI**: OpenAI GPT-4 API for conversations and embeddings
- **Database**: PostgreSQL with pgvector extension (1536-dimensional embeddings)
- **Cache**: Redis
- **Infrastructure**: Docker Compose (PostgreSQL, Redis, Spring Boot)

## Essential Commands

### Build and Run
```bash
# Build project
./gradlew build

# Run application (Docker services must be running first)
./gradlew bootRun

# Build for Docker
./gradlew bootJar
```

### Docker Operations
```bash
# Start all services (REQUIRED before running Spring Boot)
docker-compose up -d

# Check service status
docker-compose ps

# View logs
docker-compose logs -f [service-name]

# Stop services
docker-compose stop

# Complete cleanup
docker-compose down -v
```

### Testing
```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests "com.example.crud.MemberControllerTest"
```

### Configuration
Add to `src/main/resources/application-secrets.properties`:
```properties
openai.api.key=sk-proj-your-key-here
chatgpt.api.key=sk-proj-your-key-here
```

## Architecture

### Core AI Flow
1. User sends message → `RecommendationTestController`
2. Message processing → `ConversationalRecommendationService`
3. AI embedding generation → `EmbeddingApiClient`
4. Vector similarity search → PostgreSQL pgvector
5. Product recommendations returned

### Key Components

**EmbeddingApiClient** (`src/main/java/com/example/crud/ai/embedding/EmbeddingApiClient.java`)
- Generates 1536-dimensional vectors using OpenAI text-embedding-3-small
- Handles API failures with proper exception handling

**ConversationalRecommendationService** (`src/main/java/com/example/crud/ai/recommendation/application/ConversationalRecommendationService.java`)
- Main orchestrator for AI recommendations
- Stores user messages and AI responses
- Generates contextual product recommendations

**RecommendationEngine** (`src/main/java/com/example/crud/ai/recommendation/application/RecommendationEngine.java`)
- Core recommendation logic using vector similarity
- PostgreSQL pgvector cosine similarity search

### Database Schema
- `product.description_vector`: Vector(1536) for product embeddings
- `conversation` + `conversation_message`: Chat history
- `user_preference`: JSONB user preferences

## Development Guidelines

### Running the System
1. Start Docker services: `docker-compose up -d`
2. Wait 30 seconds for services to be healthy
3. Configure OpenAI API key in `application-secrets.properties`
4. Run application: `./gradlew bootRun`

### AI API Testing
```bash
# Test embedding generation
curl -X POST http://localhost:8080/api/test/recommendation/embedding \
  -H "Content-Type: application/json" \
  -d '{"text": "sample product description"}'

# Test recommendations
curl -X POST http://localhost:8080/api/test/recommendation/text \
  -H "Content-Type: application/json" \
  -d '{"query": "blue shirt"}'
```

### Common Issues
- **OpenAI API failures**: Check API key in `application-secrets.properties`
- **Vector search issues**: Verify pgvector extension: `docker-compose exec db psql -U sungho -d app -c "SELECT * FROM pg_extension WHERE extname = 'vector';"`
- **Redis connection**: Test with `docker-compose exec redis redis-cli ping`