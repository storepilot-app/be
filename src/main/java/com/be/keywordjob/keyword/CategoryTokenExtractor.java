package com.be.keywordjob.keyword;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CategoryTokenExtractor {
    private static final int MAX_LEVEL_COUNT = 3;
    private static final Pattern LEVEL_SEPARATOR_PATTERN = Pattern.compile("\\s*>\\s*");
    private static final Pattern ALIAS_SEPARATOR_PATTERN = Pattern.compile("[/|,·]+");
    private static final Pattern INVALID_CHARACTER_PATTERN = Pattern.compile("[^\\p{IsHangul}A-Za-z0-9+]");
    private static final Set<String> GENERIC_CATEGORIES = Set.of("기타", "전체", "일반", "미분류");

    public List<String> extract(String categoryPath) {
        if (categoryPath == null || categoryPath.isBlank()) {
            return List.of();
        }

        String normalized = Normalizer.normalize(categoryPath, Normalizer.Form.NFKC);
        String[] levels = LEVEL_SEPARATOR_PATTERN.split(normalized);
        List<String> tokens = new ArrayList<>();
        Set<String> deduplicationKeys = new HashSet<>();
        int firstLevelIndex = Math.max(0, levels.length - MAX_LEVEL_COUNT);

        for (int levelIndex = levels.length - 1; levelIndex >= firstLevelIndex; levelIndex--) {
            for (String alias : ALIAS_SEPARATOR_PATTERN.split(levels[levelIndex])) {
                String token = clean(alias);
                if (!isUsable(token)) {
                    continue;
                }
                String deduplicationKey = token.toLowerCase(Locale.ROOT);
                if (deduplicationKeys.add(deduplicationKey)) {
                    tokens.add(token);
                }
            }
        }
        return List.copyOf(tokens);
    }

    private String clean(String value) {
        return INVALID_CHARACTER_PATTERN.matcher(value).replaceAll("").trim();
    }

    private boolean isUsable(String token) {
        return !token.isBlank() && !GENERIC_CATEGORIES.contains(token.toLowerCase(Locale.ROOT));
    }
}
