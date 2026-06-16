package org.austral.ing.arcraft.repository;

import org.austral.ing.arcraft.entity.StoreItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoreItemRepository extends JpaRepository<StoreItem, UUID> {
    List<StoreItem> findAllByOrderByCategoryAscPriceAsc();
    List<StoreItem> findByCategory(StoreItem.Category category);
}
