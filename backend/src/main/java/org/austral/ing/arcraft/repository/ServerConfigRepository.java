package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.ServerConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ServerConfigRepository extends JpaRepository<ServerConfig, UUID> {
}
