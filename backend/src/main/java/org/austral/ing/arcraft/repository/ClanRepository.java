package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.Clan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClanRepository extends JpaRepository<Clan, UUID> {
    Optional<Clan> findByName(String name);
    Optional<Clan> findByTag(String tag);
    boolean existsByName(String name);
}
