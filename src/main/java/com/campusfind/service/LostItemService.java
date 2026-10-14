package com.campusfind.service;

import com.campusfind.entity.LostItem;
import com.campusfind.repository.LostItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LostItemService {

    private final LostItemRepository lostItemRepository;

    public LostItemService(LostItemRepository lostItemRepository) {
        this.lostItemRepository = lostItemRepository;
    }

    public LostItem saveLostItem(LostItem lostItem) {
        return lostItemRepository.save(lostItem);
    }

    public List<LostItem> getAllLostItems() {
        return lostItemRepository.findAll();
    }

    public Optional<LostItem> getLostItemById(Long id) {
        return lostItemRepository.findById(id);
    }
}