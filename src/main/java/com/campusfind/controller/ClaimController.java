package com.campusfind.controller;

import com.campusfind.entity.Claim;
import com.campusfind.service.ClaimService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/claims")
@CrossOrigin(origins = "http://localhost:5174")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public Claim createClaim(@RequestBody Claim claim) {
        return claimService.saveClaim(claim);
    }

    @GetMapping
    public List<Claim> getAllClaims() {
        return claimService.getAllClaims();
    }

    @GetMapping("/{id}")
    public Optional<Claim> getClaimById(@PathVariable Long id) {
        return claimService.getClaimById(id);
    }
}