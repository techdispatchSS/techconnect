package silver.solutions.techdispatch.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.SecretKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import silver.solutions.techdispatch.security.JwtService;
import silver.solutions.techdispatch.security.TechDispatchJwtAuthenticationConverter;

/**
 * Self-issued JWT security (PRD FR-01, §8.1, §9.2). No external identity provider is
 * involved: the same HMAC secret both signs and verifies tokens.
 *
 * <p>All paths below are relative to the {@code /api} context path, so
 * {@code /v1/auth/login} is served at {@code /api/v1/auth/login} per §8.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** §9.2: bcrypt with a minimum cost factor of 12. */
    private static final int BCRYPT_COST = 12;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_COST);
    }

    @Bean
    SecretKey jwtSecretKey(TechDispatchProperties properties) {
        return JwtService.secretKey(properties.jwt().secret());
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http, TechDispatchJwtAuthenticationConverter jwtConverter)
            throws Exception {

        return http
                // Stateless bearer-token API: there is no session or form login to protect,
                // and no cookie for a cross-site request to ride on.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Unauthenticated by necessity: these are how a user gets a token
                        // or recovers an account in the first place.
                        .requestMatchers(HttpMethod.POST,
                                "/v1/auth/login",
                                "/v1/auth/activate",
                                "/v1/auth/forgot-password",
                                "/v1/auth/reset-password")
                        .permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                        .permitAll()
                        // FR-01: administration of controllers and technicians is the
                        // Manager's alone (M-06).
                        .requestMatchers("/v1/admin/**").hasRole("MANAGER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .build();
    }
}
