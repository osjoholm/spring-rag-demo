# Spring RAG Demo

A Retrieval-Augmented Generation (RAG) implementation for legal documents using Spring Boot and Spring AI.

## Overview

This project demonstrates how to build a question-answering system that can search and answer questions about Åland legislation. The system indexes PDF documents from the Åland law collection (Lagsamling) and enables semantic search combined with answer generation.

## Features

- **Document ingestion** – Reads PDF documents with two-column layout support
- **Semantic search** – Finds relevant law sections based on meaning, not just keywords
- **Q&A API** – REST API for asking questions and receiving answers with citations
- **Streaming responses** – Server-Sent Events for real-time response streaming
- **Web interface** – Simple chat UI for interaction

## Tech Stack

- Java 21
- Spring Boot 3.5
- Spring AI
- PostgreSQL with pgvector
- Gradle

## Getting Started

### Recommended: VS Code Dev Container

The easiest way to run this project is using the included Dev Container in VS Code:

1. Install [VS Code](https://code.visualstudio.com/) and the [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)
2. Open the project folder in VS Code
3. Click "Reopen in Container" when prompted (or run `Dev Containers: Reopen in Container` from the command palette)
4. The container includes PostgreSQL/pgvector and all dependencies

### Manual Setup

#### Prerequisites

- Java 21+
- Docker (for PostgreSQL/pgvector)
- An LLM provider (see [Configure LLM](#configure-llm))

#### Start the Database

```bash
docker compose up -d
```

### Configure LLM

The project supports multiple LLM providers. The default configuration uses Ollama.

#### Ollama (Local)

1. Install [Ollama](https://ollama.ai)
2. Pull models:
   ```bash
   ollama pull llama3.1
   ollama pull nomic-embed-text
   ```
3. Start Ollama (usually runs automatically)

#### OpenAI

Update `application.yaml`:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
      embedding:
        options:
          model: text-embedding-3-small
```

Add to `build.gradle`:
```groovy
implementation 'org.springframework.ai:spring-ai-starter-model-openai'
```

### Build and Run

```bash
./gradlew bootRun
```

The application starts at `http://localhost:8080`.

## Usage

### Ingest Documents

```bash
curl -X POST http://localhost:8080/ingestions -d "start"
```

### Ask a Question

The chat interface responds in Swedish. Example:

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "När kan man bli skiljd från förtroendeuppdrag?"}'
```

### Web Interface

Open `http://localhost:8080` in your browser.

## Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `rag.top-k` | Number of documents to retrieve | 5 |
| `rag.similarity-threshold` | Minimum similarity score (0-1) | 0.3 |

## API Documentation

Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

## Project Structure

```
src/main/java/ax/sjoholm/srd/
├── api/                    # API definitions
├── configuration/          # Spring configuration
├── interfaces/             # REST controllers
└── services/
    ├── chat/               # Chat service and DTOs
    └── ingestion/          # Document processing
```

## License

MIT
