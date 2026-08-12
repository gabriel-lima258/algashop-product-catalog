package com.algaworks.algashop.product.catalog.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
// Resource server: quem EXIGE e VALIDA o token. O authorization server emite; este
// serviço nunca vê senha nem segredo, só a chave publica que baixa do /oauth2/jwks.
//
// @EnableMethodSecurity liga o @PreAuthorize das meta-anotacoes de SecurityAnnotations.
// CSRF protege contra credencial que o NAVEGADOR envia sozinho - cookie de
// sessao. Um header Authorization: Bearer nao e enviado automaticamente.
// Sem suporte a CORS: nao ha navegador chamando esta API hoje. No dia em que
// houver front, isto volta - e configurado, nao apenas religado.
// STATELESS: nenhuma sessao, nenhum JSESSIONID. Cada requisicao se identifica
// sozinha pelo token.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ProductCatalogSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health/**").permitAll()
                    .anyRequest().authenticated()) // Fecha por padrao: rota nova nasce protegida
            // Liga a validacao de JWT. O issuer vem de
            // spring.security.oauth2.resourceserver.jwt.issuer-uri
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
