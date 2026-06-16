package org.austral.ing.arcraft.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The password encoder lives in its own config (not SecurityConfig) so that beans which need
 * it — e.g. the OAuth2 user service that SecurityConfig itself depends on — can be created
 * without a circular reference back to SecurityConfig.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
