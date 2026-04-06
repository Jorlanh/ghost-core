-- ==========================================================
-- PROJETO GHOST: SCRIPT DE INFRAESTRUTURA DE DADOS (STEALTH)
-- Versão: 1.0 - Singularidade (Nível 10)
-- Autor: Jorlan Heider "Walker"
-- ==========================================================

-- 1. EXTENSÕES DE ELITE (Necessárias para Busca Semântica e IDs Únicos)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. CONTEXTO DO USUÁRIO (Cérebro Social)
CREATE TABLE IF NOT EXISTS users_context (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    firebase_uid VARCHAR(255) UNIQUE NOT NULL,
    nickname VARCHAR(100) DEFAULT 'Senhor Walker',
    god_mode BOOLEAN DEFAULT FALSE,
    last_lat DOUBLE PRECISION,
    last_lon DOUBLE PRECISION,
    current_mood VARCHAR(50) DEFAULT 'Neutral',
    preferred_language VARCHAR(10) DEFAULT 'pt-BR',
    last_interaction TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_users_firebase_uid ON users_context(firebase_uid);

-- 3. MEMÓRIA DE LONGO PRAZO (VETORIAL - Córtex Semântico)
CREATE TABLE IF NOT EXISTS ghost_memories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    firebase_uid VARCHAR(255) NOT NULL REFERENCES users_context(firebase_uid) ON DELETE CASCADE,
    content TEXT NOT NULL,
    embedding VECTOR(768), -- Otimizado para Google Gemini Flash
    importance_weight INTEGER DEFAULT 1 CHECK (importance_weight BETWEEN 1 AND 10),
    category VARCHAR(50),
    metadata JSONB DEFAULT '{}' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Busca rápida por similaridade de cosseno (HNSW)
CREATE INDEX IF NOT EXISTS idx_ghost_memories_embedding ON ghost_memories 
    USING hnsw (embedding vector_cosine_ops) 
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_ghost_memories_created_at ON ghost_memories(created_at DESC);

-- 4. CONFIGURAÇÕES DINÂMICAS DE API (Hot-Swap System)
CREATE TABLE IF NOT EXISTS api_configs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    service_name VARCHAR(50) UNIQUE NOT NULL,
    api_key TEXT NOT NULL,
    base_url TEXT,
    priority_level INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. ECOSSISTEMA IoT (Dominação de Ambiente)
CREATE TABLE IF NOT EXISTS iot_devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_name VARCHAR(100) NOT NULL,
    device_type VARCHAR(50) NOT NULL,
    room VARCHAR(50),
    status VARCHAR(50) DEFAULT 'OFFLINE',
    current_state JSONB DEFAULT '{}',
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. LOGS TÁTICOS (Nervo Analítico e Auditoria)
CREATE TABLE IF NOT EXISTS intent_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    firebase_uid VARCHAR(255) NOT NULL REFERENCES users_context(firebase_uid) ON DELETE CASCADE,
    command_text TEXT NOT NULL,
    ai_response TEXT,
    intent_category VARCHAR(50),
    latency_ms INTEGER,
    tokens_used INTEGER,
    success BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    memory_stored BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_logs_uid_time ON intent_logs(firebase_uid, created_at DESC);

-- 7. CATÁLOGO DE SKILLS (O Registro das Habilidades Criadas pela IA)
CREATE TABLE IF NOT EXISTS ghost_skills_registry (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    skill_name VARCHAR(100) UNIQUE NOT NULL,
    language VARCHAR(20) NOT NULL, -- python, powershell, javascript
    description TEXT,
    execution_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 8. GATILHOS (TRIGGERS - Automação Interna)
CREATE OR REPLACE FUNCTION update_last_interaction()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE users_context
    SET last_interaction = CURRENT_TIMESTAMP
    WHERE firebase_uid = NEW.firebase_uid;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trig_update_last_interaction ON intent_logs;
CREATE TRIGGER trig_update_last_interaction
    AFTER INSERT ON intent_logs
    FOR EACH ROW EXECUTE FUNCTION update_last_interaction();

-- ==========================================
-- DATA SEEDING (Semeando a Inteligência Inicial)
-- ==========================================

-- Inserindo o Criador (Substitua o UID pelo seu real do Firebase)
INSERT INTO users_context (firebase_uid, nickname, god_mode, preferred_language)
VALUES ('ID_FIREBASE_WALKER', 'Senhor Walker', TRUE, 'pt-BR')
ON CONFLICT (firebase_uid) DO UPDATE
SET nickname = EXCLUDED.nickname, god_mode = EXCLUDED.god_mode;

-- Configurações Iniciais de API (Atualize as chaves via pgAdmin após o boot)
INSERT INTO api_configs (service_name, api_key, base_url, priority_level, is_active)
VALUES
    ('GEMINI_FLASH', 'CHAVE_AQUI', 'https://generativelanguage.googleapis.com', 1, TRUE),
    ('GROQ_LLAMA', 'CHAVE_AQUI', 'https://api.groq.com/openai/v1', 2, TRUE),
    ('OPENWEATHER', 'CHAVE_AQUI', 'https://api.openweathermap.org/data/2.5', 3, TRUE)
ON CONFLICT (service_name) DO NOTHING;

-- Dispositivos de Teste
INSERT INTO iot_devices (device_name, device_type, room, current_state)
VALUES
    ('Vortex LED', 'LIGHT', 'Escritório', '{"color": "cyan", "effect": "pulse"}'),
    ('Walker Station', 'PC', 'Escritório', '{"power": "ON"}')
ON CONFLICT DO NOTHING;