package org.austral.ing.arcraft.config;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.auth.MinecraftOAuth2UserService;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final PlayerRepository playerRepository;
    private final MinecraftOAuth2UserService minecraftOAuth2UserService;

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> playerRepository.findByUsername(username)
                .map(player -> new org.springframework.security.core.userdetails.User(
                        player.getUsername(),
                        player.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority(player.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER"))
                ))
                .orElseThrow(() -> new UsernameNotFoundException("Player not found: " + username));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/store/coins/webhook").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/store/coins/webhook"))
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );

        // "Login with Minecraft" is only wired in when a Microsoft client is configured
        // (spring.security.oauth2.client.registration.microsoft.*). Without it the app
        // runs with plain username/password login, so cracked servers are unaffected.
        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .defaultSuccessUrl("/dashboard", true)
                    .userInfoEndpoint(userInfo -> userInfo.userService(minecraftOAuth2UserService))
            );
        }

        return http.build();
    }
}
