package org.austral.ing.arcraft.auth;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.entity.PlayerStats;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.austral.ing.arcraft.repository.PlayerStatsRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Custom OAuth2 user service for "Login with Minecraft". Instead of hitting a standard
 * OIDC userinfo endpoint, it uses the Microsoft access token to resolve the player's real
 * Minecraft profile (UUID + username), then links it to a local {@link Player} — creating
 * the account on first login. Existing accounts (e.g. created in-game by the mod or by an
 * admin) are matched by username so official and cracked logins converge on one identity.
 */
@Service
@RequiredArgsConstructor
public class MinecraftOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final MinecraftAuthService minecraftAuthService;
    private final PlayerRepository playerRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String msAccessToken = userRequest.getAccessToken().getTokenValue();

        MinecraftAuthService.MinecraftProfile profile;
        try {
            profile = minecraftAuthService.resolveProfile(msAccessToken);
        } catch (MinecraftAuthService.MinecraftAuthException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("minecraft_auth_failed", e.getMessage(), null), e.getMessage(), e);
        }

        Player player = playerRepository.findByUsername(profile.username()).orElse(null);
        if (player == null) {
            player = new Player();
            player.setUsername(profile.username());
            // Random, unusable local password — these users authenticate via Microsoft.
            player.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            player.setAdmin(false);
            player = playerRepository.save(player);

            PlayerStats stats = new PlayerStats();
            stats.setPlayer(player);
            playerStatsRepository.save(stats);
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(player.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER"));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", player.getUsername());
        attributes.put("uuid", profile.uuid().toString());
        return new DefaultOAuth2User(authorities, attributes, "username");
    }
}
