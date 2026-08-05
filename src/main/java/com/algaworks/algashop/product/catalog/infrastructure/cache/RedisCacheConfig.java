// =============================================================================
// CONFIGURAÇÃO DO CACHE REDIS
// =============================================================================
//
// Esta classe NÃO cria o cache do zero. O Spring Boot, ao ver o Redis no
// classpath e spring.cache.type=redis, já monta um RedisCacheManager sozinho.
// O que fazemos aqui é AJUSTAR esse manager que o Boot criou — dois ajustes:
// o formato das chaves e a política de valores nulos.
// =============================================================================

package com.algaworks.algashop.product.catalog.infrastructure.cache;

import com.algaworks.algashop.product.catalog.application.util.CacheNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

@Configuration
// Liga a abstração de cache do Spring: sem isso, as anotações @Cacheable,
// @CacheEvict e @CachePut viram enfeite — o proxy que as intercepta não é criado.
@EnableCaching
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisCacheConfig implements CachingConfigurer {

    @Autowired
    private ResilienceCacheErrorHandler resilienceCacheErrorHandler;

    // O RedisCacheManagerBuilderCustomizer é o gancho oficial do Boot para
    // customizar o CacheManager sem substituí-lo: o Boot chama este bean com o
    // builder já pronto, e nós só alteramos o que interessa.
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        var defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                // Define como o nome do cache vira prefixo da chave no Redis.
                // O padrão do Spring é "nome::chave" (dois-pontos DUPLO); aqui
                // trocamos por um só, ficando "nome:chave" — a convenção usual
                // de namespace do Redis, que ferramentas como o redis-cli
                // agrupam melhor na navegação por KEYS/SCAN.
                .computePrefixWith(c -> c + ":")
                .entryTtl(Duration.ofMinutes(1)); // invalidando e apagando dados antigos com TTL

        return (builder) -> builder
                // cacheDefaults: vale para QUALQUER cache que for criado.
                .cacheDefaults(defaultCacheConfig)
                // withCacheConfiguration: configuração específica de UM cache,
                // identificado pelo nome usado em @Cacheable("algashop:products:v1").
                // Com o prefixo acima, a chave final no Redis fica assim:
                //   algashop:products:v1:<id-do-produto>
                .withCacheConfiguration(CacheNames.PRODUCTS,
                        // Por padrão o Spring guarda null no cache, para lembrar
                        // que um id não existe e não bater no Mongo de novo.
                        // Aqui isso é desligado: o custo é que buscas por ids
                        // inexistentes sempre chegam no banco.
                        //
                        // TTL de 1 minuto, e não 5, por duas razões que se somam:
                        //
                        // 1. É o mesmo max-age que o ProductController publica no
                        //    Cache-Control. Os dois TTLs mediam o MESMO dado em
                        //    camadas diferentes (Redis e navegador); deixá-los
                        //    discordar significa que o número maior manda, e o menor
                        //    vira ilusão de frescor.
                        //
                        // 2. Limita a janela do furo conhecido: quando uma categoria
                        //    é renomeada, o listener assíncrono reescreve a cópia
                        //    dentro dos produtos no Mongo, mas NADA invalida estas
                        //    entradas. Até o TTL expirar, o cache serve o nome antigo
                        //    da categoria. Um minuto é o preço aceito por não acoplar
                        //    o listener ao cache — ver
                        //    docs/01-arquitetura-design/cache.md
                        defaultCacheConfig.disableCachingNullValues().entryTtl(Duration.ofMinutes(1)));
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return resilienceCacheErrorHandler;
    }
}
