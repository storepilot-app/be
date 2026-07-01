package com.be.keywordjob.keyword;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class KeywordSynonymDictionary {
    private static final String DEFAULT_DICTIONARY_PATH = "/keyword-synonyms.txt";

    private final Map<String, List<String>> synonymsByTerm;

    public KeywordSynonymDictionary() {
        this.synonymsByTerm = loadDefaultDictionary();
    }

    KeywordSynonymDictionary(List<String> dictionaryLines) {
        this.synonymsByTerm = parse(dictionaryLines);
    }

    public List<String> findSynonyms(List<String> sourceTerms) {
        if (sourceTerms == null || sourceTerms.isEmpty()) {
            return List.of();
        }

        Map<String, String> candidates = new LinkedHashMap<>();
        for (String sourceTerm : sourceTerms) {
            if (sourceTerm == null || sourceTerm.isBlank()) {
                continue;
            }
            String normalizedSource = normalize(sourceTerm);
            synonymsByTerm.forEach((dictionaryTerm, synonyms) -> {
                if (!normalizedSource.contains(dictionaryTerm)) {
                    return;
                }
                for (String synonym : synonyms) {
                    String candidate = replaceIgnoreCase(sourceTerm, dictionaryTerm, synonym);
                    if (!normalize(candidate).equals(normalizedSource)) {
                        candidates.putIfAbsent(normalize(candidate), candidate);
                    }
                }
            });
        }
        return List.copyOf(candidates.values());
    }

    private Map<String, List<String>> loadDefaultDictionary() {
        try (InputStream inputStream = KeywordSynonymDictionary.class.getResourceAsStream(DEFAULT_DICTIONARY_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("Keyword synonym dictionary was not found: " + DEFAULT_DICTIONARY_PATH);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            )) {
                return parse(reader.lines().toList());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read keyword synonym dictionary.", e);
        }
    }

    private Map<String, List<String>> parse(List<String> lines) {
        Map<String, List<String>> dictionary = new LinkedHashMap<>();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }

            List<String> group = new ArrayList<>();
            for (String term : trimmed.split("\\|")) {
                String cleaned = term.trim();
                if (!cleaned.isBlank() && group.stream().noneMatch(cleaned::equalsIgnoreCase)) {
                    group.add(cleaned);
                }
            }

            for (String term : group) {
                List<String> synonyms = group.stream()
                        .filter(candidate -> !candidate.equalsIgnoreCase(term))
                        .toList();
                if (!synonyms.isEmpty()) {
                    dictionary.put(normalize(term), synonyms);
                }
            }
        }
        return Map.copyOf(dictionary);
    }

    private String replaceIgnoreCase(String source, String target, String replacement) {
        String normalizedSource = source.toLowerCase(Locale.ROOT);
        int index = normalizedSource.indexOf(target);
        if (index < 0) {
            return source;
        }
        return source.substring(0, index) + replacement + source.substring(index + target.length());
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
