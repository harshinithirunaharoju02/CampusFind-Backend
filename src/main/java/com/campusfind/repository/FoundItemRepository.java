package com.campusfind.repository;

import com.campusfind.entity.FoundItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoundItemRepository extends JpaRepository<FoundItem, Long> {

}