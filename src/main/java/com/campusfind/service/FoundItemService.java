package com.campusfind.service;

import com.campusfind.entity.FoundItem;
import com.campusfind.repository.FoundItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FoundItemService {

    private final FoundItemRepository foundItemRepository;

    public FoundItemService(FoundItemRepository foundItemRepository) {
        this.foundItemRepository = foundItemRepository;
    }

    public FoundItem saveFoundItem(FoundItem foundItem) {
        return foundItemRepository.save(foundItem);
    }

    public List<FoundItem> getAllFoundItems() {
        return foundItemRepository.findAll();
    }

    public Optional<FoundItem> getFoundItemById(Long id) {
        return foundItemRepository.findById(id);
    }
}