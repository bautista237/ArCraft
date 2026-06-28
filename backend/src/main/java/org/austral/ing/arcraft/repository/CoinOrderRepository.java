package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.CoinOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CoinOrderRepository extends JpaRepository<CoinOrder, UUID> {
}
