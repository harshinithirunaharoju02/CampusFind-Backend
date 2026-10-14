package com.campusfind.service;

import com.campusfind.entity.Claim;
import com.campusfind.repository.ClaimRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;

    public ClaimService(ClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    public Claim saveClaim(Claim claim) {
        return claimRepository.save(claim);
    }

    public List<Claim> getAllClaims() {
        return claimRepository.findAll();
    }

    public Optional<Claim> getClaimById(Long id) {
        return claimRepository.findById(id);
    }
}