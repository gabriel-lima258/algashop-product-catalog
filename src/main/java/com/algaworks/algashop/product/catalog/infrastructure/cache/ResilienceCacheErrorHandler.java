package com.algaworks.algashop.product.catalog.infrastructure.cache;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

// O que fazer quando o CACHE falha - que e uma pergunta diferente de "o que fazer
// quando o banco falha".
//
// Sem este handler, o comportamento padrao do Spring e propagar: Redis fora do ar
// derruba a requisicao inteira. Isso inverte a razao de existir do cache. Ele foi
// posto ali para o sistema aguentar MAIS carga; se a queda dele derruba o servico, o
// cache virou um ponto de falha novo em vez de uma protecao.
//
// Por isso todos os quatro metodos engolem a excecao e retornam: a operacao segue
// direto para o Mongo. E o que se chama de FAIL-OPEN - degrada em performance, nao em
// disponibilidade. O preco e que a queda do Redis fica silenciosa do lado do cliente,
// e so o log denuncia; por isso nenhum dos metodos deixa de logar.
//
// A distincao entre WARN e ERROR e o detalhe que vale entender. Quase tudo aqui e
// problema de INFRAESTRUTURA - Redis reiniciando, rede oscilando, timeout - e some
// sozinho: WARN, sem stacktrace, senao o log vira ruido durante uma indisponibilidade.
//
// SerializationException no PUT e outra coisa: e problema de CODIGO. Significa que
// alguem tentou cachear um objeto que nao implementa Serializable - o serializador
// default do RedisCacheConfig e o do Java, nao JSON. Isso nao melhora sozinho, nao
// depende do Redis estar bem, e vai continuar acontecendo em toda escrita ate alguem
// corrigir a classe. Por isso ERROR, e com o stacktrace: e a unica forma de descobrir
// QUAL campo da arvore de objetos nao e serializavel.
@Component
@Slf4j
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class ResilienceCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        String method = "GET";
        logWarn(exception, cache, key, method);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, @Nullable Object value) {
        String method = "PUT";
        if (exception instanceof SerializationException) {
            logError(exception, cache, key, method);
        } else {
            logWarn(exception, cache, key, method);
        }
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        String method = "EVICT";
        logWarn(exception, cache, key, method);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        String method = "CLEAR";
        logWarn(exception, cache, "", method);
    }

    private void logWarn(RuntimeException exception, Cache cache, Object key, String method) {
        log.warn("Cache {} error | cache '{}' | key '{}' | cause '{}'",
                method,
                cache.getName(),
                key,
                exception.getClass().getSimpleName()
        );
    }

    private void logError(RuntimeException exception, Cache cache, Object key, String method) {
        log.error("Cache {} error | cache '{}' | key '{}' | cause '{}'",
                method,
                cache.getName(),
                key,
                exception.getClass().getSimpleName(),
                exception
        );
    }
}
