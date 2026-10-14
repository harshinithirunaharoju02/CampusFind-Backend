package com.campusfind.controller;

import com.campusfind.entity.FoundItem;
import com.campusfind.service.FoundItemService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/found-items")
@CrossOrigin(origins = "http://localhost:5174")
public class FoundItemController {

    private final FoundItemService foundItemService;

    public FoundItemController(FoundItemService foundItemService) {
        this.foundItemService = foundItemService;
    }

    @PostMapping
    public FoundItem createFoundItem(@RequestBody FoundItem foundItem) {
        return foundItemService.saveFoundItem(foundItem);
    }

    @GetMapping
    public List<FoundItem> getAllFoundItems() {
        return foundItemService.getAllFoundItems();
    }

    @GetMapping("/{id}")
    public Optional<FoundItem> getFoundItemById(@PathVariable Long id) {
        return foundItemService.getFoundItemById(id);
    }
}