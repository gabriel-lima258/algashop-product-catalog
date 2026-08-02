package com.algaworks.algashop.product.catalog.infrastructure.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Liga o processamento de @Async na aplicacao. Sem esta anotacao o @Async do
// CategoryEventListener e silenciosamente ignorado - o metodo roda na mesma thread, sem
// nenhum erro, e a unica pista e a propagacao da categoria acontecer sincrona.
//
// Nao ha executor declarado: fica o que o Spring Boot autoconfigura
// (applicationTaskExecutor). O default tem FILA ILIMITADA, entao uma rajada de updates de
// categoria enfileira em memoria em vez de rejeitar - com carga real vale dimensionar
@Configuration
@EnableAsync
public class AsyncConfig {
}
