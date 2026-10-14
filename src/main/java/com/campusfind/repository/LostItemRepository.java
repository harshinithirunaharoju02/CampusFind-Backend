package com.campusfind.repository;

import com.campusfind.entity.LostItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LostItemRepository extends JpaRepository<LostItem, Long> {
}