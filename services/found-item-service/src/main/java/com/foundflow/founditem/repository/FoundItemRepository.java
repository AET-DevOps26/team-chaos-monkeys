package com.foundflow.founditem.repository;

import com.foundflow.founditem.domain.FoundItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FoundItemRepository extends JpaRepository<FoundItem, UUID> {
}