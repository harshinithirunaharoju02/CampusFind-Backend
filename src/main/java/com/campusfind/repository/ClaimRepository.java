package com.campusfind.repository;

import com.campusfind.entity.Claim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
}