package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.austral.ing.arcraft.entity.Clan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {
    Optional<Player> findByUsername(String username);
    boolean existsByUsername(String username);
    List<Player> findByClan(Clan clan);
    long countByClan(Clan clan);
    List<Player> findByEmailIsNotNull();

    // Players whose current verification code still needs its email dispatched.
    List<Player> findByVerificationCodeIsNotNullAndVerificationSentFalse();

    // Players with a verified email — recipients for event reminders.
    List<Player> findByEmailVerifiedTrue();

    @Query("select p.clan.tag from Player p where p.username = :username")
    Optional<String> findClanTagByUsername(@Param("username") String username);
}
