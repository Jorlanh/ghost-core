-- Extensões de Elite
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. CONTEXTO DO USUÁRIO
CREATE TABLE IF NOT EXISTS users_context (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    firebase_uid VARCHAR(255) UNIQUE NOT NULL,
    nickname VARCHAR(100) DEFAULT 'Senhor Walker',
    god_mode BOOLEAN DEFAULT FALSE,
    last_lat DOUBLE PRECISION,
    last_lon DOUBLE PRECISION,
    current_mood VARCHAR(50) DEFAULT 'Neutral',
    last_interaction TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. MEMÓRIA DE LONGO PRAZO (VETORIAL)
CREATE TABLE IF NOT EXISTS ghost_memories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    firebase_uid VARCHAR(255) NOT NULL REFERENCES users_context(firebase_uid),
    content TEXT NOT NULL,
    embedding vector(1536), 
    importance_weight INTEGER DEFAULT 1 CHECK (importance_weight BETWEEN 1 AND 10),
    category VARCHAR(50),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Índice HNSW para busca semântica instantânea
CREATE INDEX ON ghost_memories USING hnsw (embedding vector_cosine_ops);

-- 3. CONFIGURAÇÕES DE API (HOT-SWAP)
CREATE TABLE IF NOT EXISTS api_configs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    service_name VARCHAR(50) UNIQUE NOT NULL, 
    api_key TEXT NOT NULL,
    base_url TEXT,
    priority_level INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. LOGS DE INTENÇÕES
CREATE TABLE IF NOT EXISTS intent_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    firebase_uid VARCHAR(255) NOT NULL,
    command_text TEXT NOT NULL,
    ai_response TEXT,
    intent_category VARCHAR(50), 
    latency_ms INTEGER,
    memory_stored BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- POPULANDO DADOS INICIAIS
INSERT INTO users_context (firebase_uid, nickname, god_mode) 
VALUES ('ID_FIREBASE_WALKER', 'Senhor Walker', TRUE) ON CONFLICT DO NOTHING;

INSERT INTO api_configs (service_name, api_key, priority_level) 
VALUES ('GEMINI_FLASH', 'SUA_CHAVE_AQUI', 1), ('GROQ_LLAMA', 'SUA_CHAVE_AQUI', 2) ON CONFLICT DO NOTHING;