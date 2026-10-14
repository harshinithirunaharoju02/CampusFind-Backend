package com.campusfind.service;

import com.campusfind.entity.FoundItem;
import com.campusfind.entity.LostItem;
import com.campusfind.repository.FoundItemRepository;
import com.campusfind.repository.LostItemRepository;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ImageStorageService {

    private final LostItemRepository lostItemRepository;
    private final FoundItemRepository foundItemRepository;

    public ImageStorageService(
            LostItemRepository lostItemRepository,
            FoundItemRepository foundItemRepository) {
        this.lostItemRepository = lostItemRepository;
        this.foundItemRepository = foundItemRepository;
    }

    // ================= IMAGE UPLOAD =================

    public String storeImage(MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please select an image.");
        }

        String contentType = file.getContentType();

        if (contentType == null
                || !(contentType.equals("image/jpeg")
                || contentType.equals("image/png")
                || contentType.equals("image/webp"))) {
            throw new IllegalArgumentException(
                    "Only JPG, PNG, and WEBP images are allowed.");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "Image size must not exceed 5 MB.");
        }

        Path uploadDirectory = Paths.get(
                "uploads", "item-images")
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(uploadDirectory);

        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException(
                    "Unsupported image type.");
        };

        String filename = UUID.randomUUID() + extension;

        Path destination = uploadDirectory
                .resolve(filename)
                .normalize();

        if (!destination.startsWith(uploadDirectory)) {
            throw new IOException(
                    "Invalid image destination.");
        }

        Files.copy(file.getInputStream(), destination);

        return "/uploads/item-images/" + filename;
    }

    // ================= FIND MATCHES =================

    public List<Map<String, Object>> findMatches(Long lostItemId) {

        Optional<LostItem> optionalLostItem =
                lostItemRepository.findById(lostItemId);

        if (optionalLostItem.isEmpty()) {
            return new ArrayList<>();
        }

        LostItem lostItem = optionalLostItem.get();

        List<Map<String, Object>> matches = new ArrayList<>();

        for (FoundItem foundItem : foundItemRepository.findAll()) {

            String lostType = itemType(
                    lostItem.getItemName(),
                    lostItem.getCategory());

            String foundType = itemType(
                    foundItem.getItemName(),
                    foundItem.getCategory());

            if (!lostType.isEmpty()
                    && !foundType.isEmpty()
                    && !lostType.equals(foundType)) {
                continue;
            }

            if (lostType.isEmpty() != foundType.isEmpty()) {
                continue;
            }

            double score = calculateScore(lostItem, foundItem);

            if (score >= 25) {

                Map<String, Object> match =
                        new LinkedHashMap<>();

                match.put("lostItemId", lostItem.getId());
                match.put("lostItemName", lostItem.getItemName());
                match.put("foundItemId", foundItem.getId());
                match.put("foundItemName", foundItem.getItemName());
                match.put("imagePath", foundItem.getImagePath());
                match.put("description", foundItem.getDescription());
                match.put("location", foundItem.getLocation());
                match.put("category", foundItem.getCategory());
                match.put("color", foundItem.getColor());
                match.put("matchScore", score);
                match.put("matchReason",
                        getMatchReason(lostItem, foundItem));

                matches.add(match);
            }
        }

        matches.sort((a, b) ->
                Double.compare(
                        ((Number) b.get("matchScore")).doubleValue(),
                        ((Number) a.get("matchScore")).doubleValue()
                ));

        return matches;
    }

    // ================= CALCULATE MATCH SCORE =================

    private double calculateScore(
            LostItem lostItem, FoundItem foundItem) {

        double score = 0;

        double nameSimilarity = wordSimilarity(
                lostItem.getItemName(),
                foundItem.getItemName());

        double descriptionSimilarity = wordSimilarity(
                lostItem.getDescription(),
                foundItem.getDescription());

        score += nameSimilarity * 40;

        if (sameItemType(
                lostItem.getItemName(),
                foundItem.getItemName(),
                lostItem.getCategory(),
                foundItem.getCategory())) {
            score += 20;
        }

        if (sameCategory(
                lostItem.getCategory(),
                foundItem.getCategory())) {
            score += 10;
        }

        if (sameColor(
                lostItem.getColor(),
                foundItem.getColor())) {
            score += 10;
        }

        if (sameLocation(
                lostItem.getLocation(),
                foundItem.getLocation())) {
            score += 10;
        }

        score += descriptionSimilarity * 10;

        return Math.round(Math.min(score, 100) * 100.0) / 100.0;
    }

    // ================= MATCH REASONS =================

    private String getMatchReason(
            LostItem lostItem, FoundItem foundItem) {

        List<String> reasons = new ArrayList<>();

        if (wordSimilarity(
                lostItem.getItemName(),
                foundItem.getItemName()) > 0) {
            reasons.add("Item names share similar words");
        }

        if (sameItemType(
                lostItem.getItemName(),
                foundItem.getItemName(),
                lostItem.getCategory(),
                foundItem.getCategory())) {
            reasons.add("Item types are compatible");
        }

        if (sameCategory(
                lostItem.getCategory(),
                foundItem.getCategory())) {
            reasons.add("Categories match");
        }

        if (sameColor(
                lostItem.getColor(),
                foundItem.getColor())) {
            reasons.add("Colors are similar");
        }

        if (sameLocation(
                lostItem.getLocation(),
                foundItem.getLocation())) {
            reasons.add("Locations are similar");
        }

        if (wordSimilarity(
                lostItem.getDescription(),
                foundItem.getDescription()) > 0) {
            reasons.add("Descriptions share similar words");
        }

        return reasons.isEmpty()
                ? "Some item details are similar"
                : String.join(", ", reasons);
    }

    // ================= IDENTIFY ITEM TYPE =================

    private String itemType(String name, String category) {

        String text = normalize(name) + " " + normalize(category);

        if (containsAny(text, "calculator", "calculators", "calc",
                "caluculator", "caluculators", "calisi", "calsiis")) {
            return "calculator";
        }

        if (containsAny(text, "water bottle", "bottle", "flask",
                "thermos", "sipper")) {
            return "bottle";
        }

        if (containsAny(text, "backpack", "bakpack", "rucksack",
                "schoolbag", "school bag", "handbag", "bag", "luggage")) {
            return "bag";
        }

        if (containsAny(text, "record book", "recordbook",
                "notebook", "notepad", "exercise book",
                "copybook", "journal", "register")) {
            return "book";
        }

        if (containsAny(text, "smartphone", "mobile phone",
                "cellphone", "cell phone", "phone", "mobile")) {
            return "phone";
        }

        if (containsAny(text, "wallet", "wllet", "purse",
                "card holder", "cardholder")) {
            return "wallet";
        }

        if (containsAny(text, "laptop", "notebook computer")) {
            return "laptop";
        }

        if (containsAny(text, "headphones", "headphone",
                "earphones", "earphone", "earbuds", "airpods")) {
            return "audio_device";
        }

        if (containsAny(text, "identity card", "id card",
                "college id", "student id", "identity badge")) {
            return "id_card";
        }

        return "";
    }

    private boolean sameItemType(
            String firstName, String secondName,
            String firstCategory, String secondCategory) {

        String firstType = itemType(firstName, firstCategory);
        String secondType = itemType(secondName, secondCategory);

        return !firstType.isEmpty()
                && firstType.equals(secondType);
    }

    // ================= CATEGORY =================

    private boolean sameCategory(String first, String second) {

        if (first == null || second == null
                || first.isBlank() || second.isBlank()) {
            return false;
        }

        String firstType = itemType("", first);
        String secondType = itemType("", second);

        if (!firstType.isEmpty()
                && firstType.equals(secondType)) {
            return true;
        }

        return normalize(first).equals(normalize(second));
    }

    // ================= COLOR =================

    private boolean sameColor(String first, String second) {

        Set<String> firstColors = colorWords(first);
        Set<String> secondColors = colorWords(second);

        if (firstColors.isEmpty() || secondColors.isEmpty()) {
            return false;
        }

        firstColors.retainAll(secondColors);

        return !firstColors.isEmpty();
    }

    private Set<String> colorWords(String text) {

        Set<String> knownColors = Set.of(
                "black", "white", "red", "blue", "green",
                "yellow", "orange", "pink", "purple", "violet",
                "brown", "gray", "grey", "silver", "gold",
                "beige", "cream", "maroon", "navy"
        );

        Set<String> result = new HashSet<>();

        for (String word : normalize(text).split("\\s+")) {
            if (knownColors.contains(word)) {
                result.add(word.equals("grey") ? "gray" : word);
            }
        }

        return result;
    }

    // ================= LOCATION =================

    private boolean sameLocation(String first, String second) {

        Set<String> firstWords = locationWords(first);
        Set<String> secondWords = locationWords(second);

        if (firstWords.isEmpty() || secondWords.isEmpty()) {
            return false;
        }

        Set<String> common = new HashSet<>(firstWords);
        common.retainAll(secondWords);

        return !common.isEmpty();
    }

    private Set<String> locationWords(String text) {

        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }

        return Arrays.stream(normalize(text).split("\\s+"))
                .filter(word -> !Set.of(
                        "near", "at", "in", "the", "of", "college"
                ).contains(word))
                .collect(Collectors.toSet());
    }

    // ================= WORD SIMILARITY =================

    private double wordSimilarity(String first, String second) {

        Set<String> firstWords = words(first);
        Set<String> secondWords = words(second);

        if (firstWords.isEmpty() || secondWords.isEmpty()) {
            return 0;
        }

        Set<String> common = new HashSet<>(firstWords);
        common.retainAll(secondWords);

        Set<String> allWords = new HashSet<>(firstWords);
        allWords.addAll(secondWords);

        double similarity =
                (double) common.size() / allWords.size();

        if (similarity == 0
                && hasSimilarSpelling(firstWords, secondWords)) {
            return 0.5;
        }

        return similarity;
    }

    private boolean hasSimilarSpelling(
            Set<String> firstWords, Set<String> secondWords) {

        for (String first : firstWords) {
            for (String second : secondWords) {
                if (first.length() >= 4 && second.length() >= 4
                        && editDistance(first, second) == 1) {
                    return true;
                }
            }
        }

        return false;
    }

    private int editDistance(String first, String second) {

        int[][] dp = new int[first.length() + 1][second.length() + 1];

        for (int i = 0; i <= first.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= second.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= first.length(); i++) {
            for (int j = 1; j <= second.length(); j++) {

                int cost = first.charAt(i - 1)
                        == second.charAt(j - 1) ? 0 : 1;

                dp[i][j] = Math.min(
                        Math.min(
                                dp[i - 1][j] + 1,
                                dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[first.length()][second.length()];
    }

    // ================= NORMALIZE WORDS =================

    private Set<String> words(String text) {

        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }

        return Arrays.stream(normalize(text).split("\\s+"))
                .map(this::canonicalWord)
                .filter(word -> word.length() > 2)
                .filter(word -> !Set.of(
                        "the", "and", "with", "for", "was",
                        "are", "inside", "from", "this", "that",
                        "you", "your", "have", "has", "near",
                        "found", "lost", "item", "belonging",
                        "located", "there", "here"
                ).contains(word))
                .collect(Collectors.toSet());
    }

    private String canonicalWord(String word) {

        return switch (word) {
            case "flasks", "thermos", "thermoses", "sippers" ->
                    "bottle";

            case "backpacks", "bakpack", "bakpacks", "rucksacks",
                    "schoolbag", "schoolbags", "handbags", "bags" ->
                    "backpack";

            case "recordbook", "register", "recordbooks" ->
                    "book";

            case "notepad", "notepads", "notebooks", "journals",
                    "copybook", "copybooks" ->
                    "notebook";

            case "mobiles", "smartphones", "cellphone",
                    "cellphones", "phones" ->
                    "phone";

            case "purse", "purses", "wllet", "wallets",
                    "cardholder", "cardholders" ->
                    "wallet";

            case "earphones", "earbuds", "airpods", "headphones" ->
                    "headphone";

            case "calculators", "calc", "caluculator",
                    "caluculators", "calisi", "calsiis" ->
                    "calculator";

            case "grey" -> "gray";

            default -> word;
        };
    }

    // ================= TEXT HELPERS =================

    private boolean containsAny(String text, String... terms) {

        Set<String> textWords =
                new HashSet<>(Arrays.asList(text.split("\\s+")));

        for (String term : terms) {
            Set<String> termWords = new HashSet<>(
                    Arrays.asList(normalize(term).split("\\s+")));

            if (textWords.containsAll(termWords)) {
                return true;
            }
        }

        return false;
    }

    private String normalize(String text) {

        if (text == null || text.isBlank()) {
            return "";
        }

        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}