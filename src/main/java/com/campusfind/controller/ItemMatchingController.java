package com.campusfind.controller;

import com.campusfind.service.ItemMatchingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/matches")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174"
})
public class ItemMatchingController {

    private final ItemMatchingService itemMatchingService;

    public ItemMatchingController(
            ItemMatchingService itemMatchingService) {
        this.itemMatchingService = itemMatchingService;
    }

    @GetMapping("/lost/{lostItemId}")
    public List<Map<String, Object>> findMatches(
            @PathVariable Long lostItemId) {
        return itemMatchingService.findMatches(lostItemId);
    }
}