package org.austral.ing.arcraft.controller;

import lombok.RequiredArgsConstructor;
import org.austral.ing.arcraft.entity.Player;
import org.austral.ing.arcraft.repository.PlayerRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

/**
 * Injects per-request values shared by all pages: the logged-in player's coin balance
 * (for the navbar) and whether "Login with Minecraft" is available (for the login page).
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final PlayerRepository playerRepository;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    @ModelAttribute("navCoins")
    public Long navCoins(Principal principal) {
        if (principal == null) return null;
        return playerRepository.findByUsername(principal.getName())
                .map(Player::getCoins)
                .orElse(null);
    }

    @ModelAttribute("minecraftLoginEnabled")
    public boolean minecraftLoginEnabled() {
        return clientRegistrations.getIfAvailable() != null;
    }

    /** Current player's username and clan tag, for the navbar "My Profile" / "My Clan" buttons. */
    @ModelAttribute("navUsername")
    public String navUsername(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    @ModelAttribute("navClanTag")
    public String navClanTag(Principal principal) {
        if (principal == null) return null;
        return playerRepository.findClanTagByUsername(principal.getName()).orElse(null);
    }
}
