package com.campusfind.controller;

import com.campusfind.entity.LostItem;
import com.campusfind.service.LostItemService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/lost-items")
@CrossOrigin(origins = "http://localhost:5174")
public class LostItemController {

    private final LostItemService lostItemService;

    public LostItemController(LostItemService lostItemService) {
        this.lostItemService = lostItemService;
    }

    @PostMapping
    public LostItem createLostItem(@RequestBody LostItem lostItem) {
        return lostItemService.saveLostItem(lostItem);
    }

    @GetMapping
    public List<LostItem> getAllLostItems() {
        return lostItemService.getAllLostItems();
    }

    @GetMapping("/{id}")
    public Optional<LostItem> getLostItemById(@PathVariable Long id) {
        return lostItemService.getLostItemById(id);
    }
}