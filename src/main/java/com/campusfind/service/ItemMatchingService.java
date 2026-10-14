package com.campusfind.service;

import com.campusfind.entity.FoundItem;
import com.campusfind.entity.LostItem;
import com.campusfind.repository.FoundItemRepository;
import com.campusfind.repository.LostItemRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ItemMatchingService {

    private static final Logger log =
            LoggerFactory.getLogger(ItemMatchingService.class);

    // Temporary threshold for testing matching results.
    private static final double MIN_MATCH_SCORE = 25.0;

    // A different color reduces the score, but does not automatically
    // prevent an otherwise plausible item from matching.
    private static final double COLOR_MISMATCH_PENALTY = 15.0;

    private final LostItemRepository lostItemRepository;
    private final FoundItemRepository foundItemRepository;

    public ItemMatchingService(
            LostItemRepository lostItemRepository,
            FoundItemRepository foundItemRepository) {
        this.lostItemRepository = lostItemRepository;
        this.foundItemRepository = foundItemRepository;
    }

    public List<Map<String, Object>> findMatches(Long lostItemId) {

        Optional<LostItem> optionalLostItem =
                lostItemRepository.findById(lostItemId);

        if (optionalLostItem.isEmpty()) {
            log.warn("Lost item ID {} was not found", lostItemId);
            return new ArrayList<>();
        }

        LostItem lostItem = optionalLostItem.get();
        List<FoundItem> foundItems = foundItemRepository.findAll();
        List<Map<String, Object>> matches = new ArrayList<>();

        String lostType = itemType(
                lostItem.getItemName(), lostItem.getCategory());

        log.info(
                "Matching lost item ID {}: name='{}', type='{}', found reports={}",
                lostItemId, lostItem.getItemName(), lostType, foundItems.size());

        for (FoundItem foundItem : foundItems) {

            if (isClearlyLostDescription(foundItem.getDescription())) {
                log.debug("Skipping found item {}: description indicates lost item",
                        foundItem.getId());
                continue;
            }

            String foundType = itemType(
                    foundItem.getItemName(), foundItem.getCategory());

            if (!lostType.isEmpty()
                    && !foundType.isEmpty()
                    && !lostType.equals(foundType)) {
                log.debug("Skipping found item {}: incompatible type '{}'",
                        foundItem.getId(), foundType);
                continue;
            }

            if (lostType.isEmpty() != foundType.isEmpty()) {
                log.debug("Skipping found item {}: one item type is unknown",
                        foundItem.getId());
                continue;
            }

            if (hasConflictingCategories(
                    lostItem.getCategory(), foundItem.getCategory())) {
                log.debug("Skipping found item {}: conflicting categories",
                        foundItem.getId());
                continue;
            }

            double score = calculateScore(lostItem, foundItem);

            log.info(
                    "Candidate found item ID {} ('{}'): score={}",
                    foundItem.getId(), foundItem.getItemName(), score);

            if (score >= MIN_MATCH_SCORE) {

                Map<String, Object> match = new LinkedHashMap<>();

                match.put("lostItemId", lostItem.getId());
                match.put("lostItemName", lostItem.getItemName());
                match.put("lostItemDescription", lostItem.getDescription());
                match.put("lostItemCategory", lostItem.getCategory());
                match.put("lostItemColor", lostItem.getColor());
                match.put("lostItemLocation", lostItem.getLocation());

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
                        ((Number) a.get("matchScore")).doubleValue()));

        log.info("Returning {} matches for lost item ID {}",
                matches.size(), lostItemId);

        return matches;
    }

    private double calculateScore(
            LostItem lostItem, FoundItem foundItem) {

        double score = 0;

        double nameSimilarity = wordSimilarity(
                lostItem.getItemName(), foundItem.getItemName());

        double descriptionScore = descriptionSimilarity(
                lostItem.getDescription(),
                foundItem.getDescription(),
                lostItem.getLocation(),
                foundItem.getLocation());

        score += nameSimilarity * 35;

        if (sameItemType(
                lostItem.getItemName(),
                foundItem.getItemName(),
                lostItem.getCategory(),
                foundItem.getCategory())) {
            score += 20;
        }

        if (sameCategory(
                lostItem.getCategory(), foundItem.getCategory())) {
            score += 10;
        }

        Set<String> lostColors = colorWords(lostItem.getColor());
        Set<String> foundColors = colorWords(foundItem.getColor());

        if (!lostColors.isEmpty() && !foundColors.isEmpty()) {
            if (sameColor(lostItem.getColor(), foundItem.getColor())) {
                score += 10;
            } else {
                score -= COLOR_MISMATCH_PENALTY;
            }
        }

        if (reportedColorConflict(
                lostItem.getColor(), lostItem.getDescription())) {
            score -= 20;
        }

        if (reportedColorConflict(
                foundItem.getColor(), foundItem.getDescription())) {
            score -= 20;
        }

        LocationComparison locations =
                compareReportedLocations(lostItem, foundItem);

        if (locations.same) {
            score += 10;
        } else if (locations.conflict) {
            score -= 15;
        }

        score += descriptionScore * 15;

        score = Math.max(0, Math.min(score, 100));

        return Math.round(score * 100.0) / 100.0;
    }

    private String getMatchReason(
            LostItem lostItem, FoundItem foundItem) {

        List<String> reasons = new ArrayList<>();

        if (wordSimilarity(
                lostItem.getItemName(), foundItem.getItemName()) > 0) {
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
                lostItem.getCategory(), foundItem.getCategory())) {
            reasons.add("Categories match");
        }

        Set<String> lostColors = colorWords(lostItem.getColor());
        Set<String> foundColors = colorWords(foundItem.getColor());

        if (!lostColors.isEmpty() && !foundColors.isEmpty()) {
            if (sameColor(lostItem.getColor(), foundItem.getColor())) {
                reasons.add("Colors match");
            } else {
                reasons.add("Reported colors differ");
            }
        }

        if (reportedColorConflict(
                lostItem.getColor(), lostItem.getDescription())
                || reportedColorConflict(
                foundItem.getColor(), foundItem.getDescription())) {
            reasons.add("Description conflicts with reported color");
        }

        LocationComparison locations =
                compareReportedLocations(lostItem, foundItem);

        if (locations.conflict) {
            reasons.add("Reported block locations conflict");
        } else if (locations.same) {
            reasons.add("Reported locations match");
        } else {
            reasons.add("Location could not be confirmed");
        }

        if (descriptionSimilarity(
                lostItem.getDescription(),
                foundItem.getDescription(),
                lostItem.getLocation(),
                foundItem.getLocation()) > 0) {
            reasons.add("Descriptions share similar details");
        }

        return String.join(", ", reasons);
    }

    private boolean isClearlyLostDescription(String description) {

        if (description == null || description.isBlank()) {
            return false;
        }

        String text = normalize(description);

        return Pattern.compile(
                "\\b(?:i|we)\\s+(?:have\\s+|had\\s+)?(?:lost|misplaced)\\b")
                .matcher(text).find()
                || Pattern.compile("\\bmy\\s+lost\\s+")
                .matcher(text).find()
                || Pattern.compile("\\bi\\s+lost\\b")
                .matcher(text).find();
    }

    private boolean reportedColorConflict(
            String colorField, String description) {

        Set<String> fieldColors = colorWords(colorField);
        Set<String> descriptionColors = colorWords(description);

        if (fieldColors.isEmpty() || descriptionColors.isEmpty()) {
            return false;
        }

        Set<String> common = new HashSet<>(fieldColors);
        common.retainAll(descriptionColors);

        return common.isEmpty();
    }

    private String itemType(String name, String category) {

        String type = detectType(normalize(name));

        if (!type.isEmpty()) {
            return type;
        }

        return detectType(normalize(category));
    }

    private String detectType(String text) {

        if (text == null || text.isBlank()) {
            return "";
        }

        if (containsAny(text,
                "calculator", "calculators",
                "calculater", "calculaters",
                "calc", "caluculator", "caluculators",
                "calsiis", "calisi")) {
            return "calculator";
        }

        if (containsAny(text,
                "water bottle", "bottle", "flask",
                "thermos", "sipper")) {
            return "bottle";
        }

        if (containsAny(text,
                "backpack", "backpacks", "bckpack",
                "rucksack", "schoolbag", "school bag",
                "handbag", "bag", "luggage")) {
            return "bag";
        }

        if (containsAny(text,
                "record book", "recordbook", "notebook",
                "notepad", "exercise book", "copybook",
                "journal")) {
            return "notebook";
        }

        if (containsAny(text,
                "smartphone", "mobile phone",
                "cellphone", "cell phone", "phone", "mobile")) {
            return "phone";
        }

        if (containsAny(text,
                "wallet", "wllet", "purse",
                "card holder", "cardholder")) {
            return "wallet";
        }

        if (containsAny(text, "laptop", "notebook computer")) {
            return "laptop";
        }

        if (containsAny(text,
                "headphones", "headphone", "earphones",
                "earphone", "earbuds", "airpods")) {
            return "audio_device";
        }

        if (containsAny(text,
                "identity card", "id card", "college id",
                "student id", "identity badge")) {
            return "id_card";
        }

        if (containsAny(text,
                "spectacles", "spectacle", "glasses",
                "eyeglasses", "spects")) {
            return "eyewear";
        }

        return "";
    }

    private boolean sameItemType(
            String firstName, String secondName,
            String firstCategory, String secondCategory) {

        String firstType = itemType(firstName, firstCategory);
        String secondType = itemType(secondName, secondCategory);

        return !firstType.isEmpty() && firstType.equals(secondType);
    }

    private boolean sameCategory(String first, String second) {

        if (first == null || second == null
                || first.isBlank() || second.isBlank()) {
            return false;
        }

        String firstCategory = normalize(first);
        String secondCategory = normalize(second);

        String firstType = detectType(firstCategory);
        String secondType = detectType(secondCategory);

        if (!firstType.isEmpty() || !secondType.isEmpty()) {
            return !firstType.isEmpty()
                    && firstType.equals(secondType);
        }

        return firstCategory.equals(secondCategory);
    }

    private boolean hasConflictingCategories(
            String first, String second) {

        if (first == null || second == null
                || first.isBlank() || second.isBlank()) {
            return false;
        }

        String firstType = detectType(normalize(first));
        String secondType = detectType(normalize(second));

        return !firstType.isEmpty()
                && !secondType.isEmpty()
                && !firstType.equals(secondType);
    }

    private boolean sameColor(String first, String second) {

        Set<String> firstColors = colorWords(first);
        Set<String> secondColors = colorWords(second);

        if (firstColors.isEmpty() || secondColors.isEmpty()) {
            return false;
        }

        Set<String> common = new HashSet<>(firstColors);
        common.retainAll(secondColors);

        return !common.isEmpty();
    }

    private Set<String> colorWords(String text) {

        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }

        Map<String, String> synonyms = Map.ofEntries(
                Map.entry("grey", "gray"),
                Map.entry("navy", "blue"),
                Map.entry("maroon", "red"),
                Map.entry("burgundy", "red"),
                Map.entry("violet", "purple"),
                Map.entry("golden", "gold"),
                Map.entry("multicolour", "multicolor"),
                Map.entry("multicoloured", "multicolor"),
                Map.entry("multicolored", "multicolor")
        );

        Set<String> knownColors = Set.of(
                "black", "white", "red", "blue", "green",
                "yellow", "orange", "pink", "purple", "gray",
                "brown", "gold", "silver", "beige", "cream",
                "multicolor"
        );

        Set<String> colors = new HashSet<>();

        for (String word : normalize(text).split("\\s+")) {
            String canonical = synonyms.getOrDefault(word, word);

            if (knownColors.contains(canonical)) {
                colors.add(canonical);
            }
        }

        return colors;
    }

    private static class LocationComparison {
        private final boolean same;
        private final boolean conflict;

        private LocationComparison(boolean same, boolean conflict) {
            this.same = same;
            this.conflict = conflict;
        }
    }

    private LocationComparison compareReportedLocations(
            LostItem lostItem, FoundItem foundItem) {

        String lostLocationBlock = extractBlock(lostItem.getLocation());
        String foundLocationBlock = extractBlock(foundItem.getLocation());

        String lostDescriptionBlock =
                extractBlock(lostItem.getDescription());
        String foundDescriptionBlock =
                extractBlock(foundItem.getDescription());

        boolean lostContradicts =
                !lostLocationBlock.isEmpty()
                && !lostDescriptionBlock.isEmpty()
                && !lostLocationBlock.equals(lostDescriptionBlock);

        boolean foundContradicts =
                !foundLocationBlock.isEmpty()
                && !foundDescriptionBlock.isEmpty()
                && !foundLocationBlock.equals(foundDescriptionBlock);

        Set<String> lostBlocks = new HashSet<>();
        Set<String> foundBlocks = new HashSet<>();

        if (!lostLocationBlock.isEmpty()) {
            lostBlocks.add(lostLocationBlock);
        }
        if (!lostDescriptionBlock.isEmpty()) {
            lostBlocks.add(lostDescriptionBlock);
        }
        if (!foundLocationBlock.isEmpty()) {
            foundBlocks.add(foundLocationBlock);
        }
        if (!foundDescriptionBlock.isEmpty()) {
            foundBlocks.add(foundDescriptionBlock);
        }

        Set<String> commonBlocks = new HashSet<>(lostBlocks);
        commonBlocks.retainAll(foundBlocks);

        boolean conflictingBlocks =
                !lostBlocks.isEmpty()
                && !foundBlocks.isEmpty()
                && !lostBlocks.equals(foundBlocks);

        if (conflictingBlocks) {
            return new LocationComparison(false, true);
        }

        if (lostContradicts || foundContradicts) {
            return new LocationComparison(false, false);
        }

        if (!commonBlocks.isEmpty()) {
            return new LocationComparison(true, false);
        }

        if (lostBlocks.isEmpty() && foundBlocks.isEmpty()) {
            if (sameLocation(
                    lostItem.getLocation(), foundItem.getLocation())) {
                return new LocationComparison(true, false);
            }

            if (hasConflictingLocations(
                    lostItem.getLocation(), foundItem.getLocation())) {
                return new LocationComparison(false, true);
            }
        }

        return new LocationComparison(false, false);
    }

    private boolean sameLocation(String first, String second) {

        if (first == null || second == null
                || first.isBlank() || second.isBlank()) {
            return false;
        }

        String firstBlock = extractBlock(first);
        String secondBlock = extractBlock(second);

        if (!firstBlock.isEmpty() && !secondBlock.isEmpty()) {
            return firstBlock.equals(secondBlock);
        }

        Set<String> firstWords = locationWords(first);
        Set<String> secondWords = locationWords(second);

        Set<String> common = new HashSet<>(firstWords);
        common.retainAll(secondWords);

        if (!common.isEmpty()) {
            return true;
        }

        for (String firstWord : firstWords) {
            for (String secondWord : secondWords) {
                if (!firstWord.equals(secondWord)
                        && hasSimilarSpelling(firstWord, secondWord)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasConflictingLocations(
            String first, String second) {

        String firstBlock = extractBlock(first);
        String secondBlock = extractBlock(second);

        return !firstBlock.isEmpty()
                && !secondBlock.isEmpty()
                && !firstBlock.equals(secondBlock);
    }

    private String extractBlock(String text) {

        if (text == null || text.isBlank()) {
            return "";
        }

        Matcher matcher = Pattern.compile(
                "\\b(?:block\\s+([a-z0-9]+)|([a-z0-9]+)\\s+block)\\b")
                .matcher(normalize(text));

        if (matcher.find()) {
            return matcher.group(1) != null
                    ? matcher.group(1)
                    : matcher.group(2);
        }

        return "";
    }

    private Set<String> locationWords(String location) {

        if (location == null || location.isBlank()) {
            return Collections.emptySet();
        }

        Set<String> ignored = Set.of(
                "near", "at", "in", "the", "outside",
                "inside", "block", "building", "room",
                "area", "around"
        );

        return Arrays.stream(normalize(location).split("\\s+"))
                .map(this::canonicalWord)
                .filter(word -> !ignored.contains(word))
                .filter(word -> !word.isBlank())
                .collect(Collectors.toSet());
    }

    private double descriptionSimilarity(
            String firstDescription, String secondDescription,
            String firstLocation, String secondLocation) {

        Set<String> firstWords = words(firstDescription);
        Set<String> secondWords = words(secondDescription);

        firstWords.removeAll(locationWords(firstLocation));
        secondWords.removeAll(locationWords(secondLocation));

        Set<String> locationTerms = Set.of(
                "block", "building", "room", "area",
                "near", "outside", "inside", "around"
        );

        firstWords.removeAll(locationTerms);
        secondWords.removeAll(locationTerms);

        String firstBlock = extractBlock(firstDescription);
        String secondBlock = extractBlock(secondDescription);

        if (!firstBlock.isEmpty()) {
            firstWords.remove(firstBlock);
        }
        if (!secondBlock.isEmpty()) {
            secondWords.remove(secondBlock);
        }

        if (firstWords.isEmpty() || secondWords.isEmpty()) {
            return 0;
        }

        return wordSimilarityFromSets(firstWords, secondWords);
    }

    private double wordSimilarity(String first, String second) {
        return wordSimilarityFromSets(words(first), words(second));
    }

    private double wordSimilarityFromSets(
            Set<String> firstWords, Set<String> secondWords) {

        if (firstWords.isEmpty() || secondWords.isEmpty()) {
            return 0;
        }

        Set<String> matchedFirst = new HashSet<>();
        Set<String> matchedSecond = new HashSet<>();

        for (String word : firstWords) {
            if (secondWords.contains(word)) {
                matchedFirst.add(word);
                matchedSecond.add(word);
            }
        }

        for (String firstWord : firstWords) {
            if (matchedFirst.contains(firstWord)) {
                continue;
            }

            for (String secondWord : secondWords) {
                if (matchedSecond.contains(secondWord)) {
                    continue;
                }

                if (hasSimilarSpelling(firstWord, secondWord)) {
                    matchedFirst.add(firstWord);
                    matchedSecond.add(secondWord);
                    break;
                }
            }
        }

        int matchedCount = matchedFirst.size();
        int totalUniqueWords =
                firstWords.size() + secondWords.size() - matchedCount;

        if (totalUniqueWords == 0) {
            return 0;
        }

        return (double) matchedCount / totalUniqueWords;
    }

    private Set<String> words(String text) {

        if (text == null || text.isBlank()) {
            return new HashSet<>();
        }

        Set<String> ignored = Set.of(
                "the", "and", "with", "for", "was",
                "are", "inside", "from", "this", "that",
                "you", "your", "have", "has", "near",
                "found", "lost", "item", "belonging",
                "located", "there", "here", "nearby"
        );

        return Arrays.stream(normalize(text).split("\\s+"))
                .map(this::canonicalWord)
                .filter(word -> word.length() > 2)
                .filter(word -> !ignored.contains(word))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String canonicalWord(String word) {

        return switch (word) {
            case "flasks", "thermoses", "sippers",
                    "bottles" -> "bottle";

            case "backpacks", "rucksacks", "schoolbag",
                    "schoolbags", "handbags", "bags",
                    "bckpack" -> "backpack";

            case "notepads", "notebooks", "journals",
                    "copybook", "copybooks" -> "notebook";

            case "mobiles", "smartphones", "cellphone",
                    "cellphones", "phones" -> "phone";

            case "purses", "wallets", "cardholder",
                    "cardholders", "wllet" -> "wallet";

            case "earphones", "earbuds", "airpods",
                    "headphones" -> "headphone";

            case "calculators", "calculater", "calculaters",
                    "calc", "caluculator", "caluculators",
                    "calsiis", "calisi" -> "calculator";

            case "grey" -> "gray";
            case "records" -> "record";

            default -> word;
        };
    }

    private boolean hasSimilarSpelling(String first, String second) {

        if (first.length() < 4 || second.length() < 4) {
            return false;
        }

        if (first.charAt(0) != second.charAt(0)) {
            return false;
        }

        int maxDistance =
                Math.max(first.length(), second.length()) <= 5 ? 1 : 2;

        return editDistance(first, second) <= maxDistance;
    }

    private int editDistance(String first, String second) {

        int[][] dp =
                new int[first.length() + 1][second.length() + 1];

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
                        dp[i - 1][j - 1] + cost);
            }
        }

        return dp[first.length()][second.length()];
    }

    private boolean containsAny(String text, String... terms) {

        Set<String> textWords = new HashSet<>(
                Arrays.asList(normalize(text).split("\\s+")));

        for (String term : terms) {
            String normalizedTerm = normalize(term);

            if (textWords.containsAll(
                    Arrays.asList(normalizedTerm.split("\\s+")))) {
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