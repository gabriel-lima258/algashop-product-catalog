package com.algaworks.algashop.product.catalog.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

// POR QUE ESTA CLASSE EXISTE: para o Redis fora do ar NAO derrubar o health do servico.
//
// O indicador nativo do Boot reporta DOWN quando o Redis nao responde, e DOWN e o status
// mais severo de todos - ele contamina o agregado e, num orquestrador, tira a instancia de
// rotacao. Para um BANCO isso e correto; para um CACHE nao e: sem Redis o servico continua
// respondendo tudo, so que indo ao banco. Degradado, nao morto.
//
// Por isso sao duas peças que so funcionam juntas:
//   management.health.redis.enabled: false   desliga o nativo (senao seriam DOIS
//                                            indicadores, e o dele venceria com DOWN)
//   esta classe                              entra no lugar reportando DEGRADED
//
// DEGRADED nao e um status do Spring - e uma string arbitraria. O Health.status(String)
// aceita qualquer codigo, e quem decide a severidade e o management.endpoint.health
// .status.order do YAML, onde DEGRADED foi posto ENTRE UNKNOWN e UP. Resultado: pior que
// saudavel, melhor que desconhecido, e longe de DOWN.
//
// ATENCAO ao nome do bean: @Component("cache") e o que faz este indicador aparecer como
// "cache" em /actuator/health. Sem o nome explicito, o Boot derivaria do nome da classe e
// sairia "customRedisCache". Renomear a classe mudaria o contrato do endpoint - o nome do
// bean aqui e API, nao detalhe interno.
//
// O @ConditionalOnProperty espelha o do RedisCacheConfig: sem cache configurado nao ha o
// que monitorar, e registrar o indicador criaria um componente que sempre falharia.
@Component("cache")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class CustomRedisCacheHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public @Nullable Health health() {
        try {
            // ping() e o menor teste possivel que ainda prova que HA conexao: ele abre (ou
            // reusa) uma conexao de verdade e faz um round trip. Checar so se a factory
            // existe nao provaria nada - ela existe mesmo com o Redis fora.
            redisConnectionFactory.getConnection().ping();
            return Health.up().build();
        } catch (Exception e) {
            // withDetail expoe a mensagem; withException guarda a excecao inteira, que o
            // Actuator so mostra quando show-details permite. E a diferenca entre saber
            // "o cache caiu" e saber POR QUE - timeout, auth recusada, host errado.
            return Health.status("DEGRADED")
                    .withDetail("error", e.getMessage())
                    .withException(e)
                    .build();
        }
    }
}
