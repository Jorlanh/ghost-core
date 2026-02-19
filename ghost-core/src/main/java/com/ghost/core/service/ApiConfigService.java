package com.ghost.core.service;

import com.ghost.core.model.ApiConfig;
import com.ghost.core.repository.ApiConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ApiConfigService {

    @Autowired
    private ApiConfigRepository repository;

    @Autowired(required = false)  // Torna opcional – não falha se Redis não estiver configurado/rodando
    private StringRedisTemplate redisTemplate;

    private static final String REDIS_KEY_PREFIX = "ghost:api:";

    public String getApiKey(String serviceName) {
        String normalized = serviceName.toUpperCase();
        String cacheKey = REDIS_KEY_PREFIX + normalized;

        // Tenta pegar do cache apenas se Redis disponível
        String cached = null;
        if (redisTemplate != null) {
            try {
                cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    log.debug("Chave encontrada no Redis: {}", cacheKey);
                    return cached;
                }
            } catch (Exception e) {
                log.warn("Redis indisponível ou erro ao ler cache para '{}'. Usando banco direto. Erro: {}", 
                         cacheKey, e.getMessage());
            }
        }

        // Busca no banco
        return repository.findByServiceName(normalized)
                .map(config -> {
                    if (Boolean.FALSE.equals(config.getIsActive())) {
                        throw new IllegalStateException("Configuração inativa para serviço: " + serviceName);
                    }
                    String key = config.getApiKey();

                    // Tenta cachear se Redis estiver disponível
                    if (redisTemplate != null) {
                        try {
                            redisTemplate.opsForValue().set(cacheKey, key, 1, TimeUnit.HOURS);
                            log.debug("Chave cacheada no Redis: {}", cacheKey);
                        } catch (Exception e) {
                            log.warn("Falha ao cachear chave no Redis ({}): {}", cacheKey, e.getMessage());
                        }
                    }

                    return key;
                })
                .orElseThrow(() -> new RuntimeException("API Key não encontrada para o serviço: " + serviceName));
    }

    public void refreshCache() {
        if (redisTemplate == null) {
            log.info("Redis não disponível → refreshCache ignorado");
            return;
        }
        try {
            Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Cache de API keys limpo ({} entradas)", keys.size());
            }
        } catch (Exception e) {
            log.warn("Falha ao limpar cache Redis: {}", e.getMessage());
        }
    }

    public void invalidateCache(String serviceName) {
        if (redisTemplate == null) return;
        try {
            redisTemplate.delete(REDIS_KEY_PREFIX + serviceName.toUpperCase());
        } catch (Exception ignored) {}
    }
}