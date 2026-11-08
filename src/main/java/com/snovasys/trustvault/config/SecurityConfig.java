package com.snovasys.trustvault.config;

import com.snovasys.trustvault.security.VaultUserDetailsService;
import com.snovasys.trustvault.service.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean DaoAuthenticationProvider authenticationProvider(VaultUserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return provider;
    }
    @Bean AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean SecurityFilterChain filterChain(HttpSecurity http, DaoAuthenticationProvider provider,
                                          SecurityContextRepository contextRepository, AuditService audit) throws Exception {
        HttpSessionCsrfTokenRepository csrf = new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-TOKEN");
        http.authenticationProvider(provider)
            .securityContext(c -> c.securityContextRepository(contextRepository))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .requestCache(c -> c.disable())
            .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/api/auth/csrf", "/api/auth/login", "/error", "/favicon.ico").permitAll()
                .anyRequest().authenticated())
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())
            .exceptionHandling(e -> e.authenticationEntryPoint((request, response, exception) -> {
                if (request.getRequestURI().startsWith("/api/")) {
                    audit.failure("anonymous", "AUTH_REQUIRED", "API", request.getRequestURI(), "Unauthenticated request denied");
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Authentication required\"}");
            }))
            .headers(h -> h
                .frameOptions(f -> f.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .cacheControl(Customizer.withDefaults())
                .httpStrictTransportSecurity(x -> x.maxAgeInSeconds(31536000).includeSubDomains(true))
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")));
        return http.build();
    }
}
