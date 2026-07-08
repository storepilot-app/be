package com.be.keyword;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SimilarProductRepeatedPhraseExtractor {
    private static final int MAX_PHRASE_TOKEN_COUNT = 3;

    private final ProductNameTokenExtractor productNameTokenExtractor;

    public SimilarProductRepeatedPhraseExtractor(ProductNameTokenExtractor productNameTokenExtractor) {
        this.productNameTokenExtractor = productNameTokenExtractor;
    }

    public Map<Integer, List<String>> extract(List<ProductSource> products) {
        if (products == null || products.isEmpty()) {
            return Map.of();
        }

        Map<String, List<ProductSource>> categoryGroups = products.stream()
                .filter(product -> product.category() != null && !product.category().isBlank())
                .collect(Collectors.groupingBy(
                        product -> normalize(product.category()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<Integer, List<String>> result = new HashMap<>();
        categoryGroups.values().forEach(group -> extractGroup(group, result));
        return Map.copyOf(result);
    }

    private void extractGroup(List<ProductSource> group, Map<Integer, List<String>> result) {
        Map<Integer, List<Phrase>> phrasesByProductId = new LinkedHashMap<>();
        Map<String, PhraseSupport> supportByPhrase = new HashMap<>();

        for (ProductSource product : group) {
            List<Phrase> phrases = createPhrases(productNameTokenExtractor.extract(product.productName()));
            phrasesByProductId.put(product.id(), phrases);

            String productIdentity = normalize(product.productName());
            for (Phrase phrase : phrases) {
                supportByPhrase
                        .computeIfAbsent(phrase.key(), key -> new PhraseSupport(phrase.text(), phrase.tokenCount()))
                        .productIdentities()
                        .add(productIdentity);
            }
        }

        int distinctProductCount = (int) group.stream()
                .map(product -> normalize(product.productName()))
                .distinct()
                .count();
        int minimumSupport = minimumSupport(distinctProductCount);

        phrasesByProductId.forEach((productId, phrases) -> {
            List<String> repeatedPhrases = phrases.stream()
                    .filter(phrase -> supportByPhrase.get(phrase.key()).count() >= minimumSupport)
                    .sorted(Comparator
                            .comparingInt((Phrase phrase) -> phrase.tokenCount()).reversed()
                            .thenComparing(
                                    phrase -> supportByPhrase.get(phrase.key()).count(),
                                    Comparator.reverseOrder()
                            ))
                    .map(Phrase::text)
                    .toList();
            result.put(productId, repeatedPhrases);
        });
    }

    private List<Phrase> createPhrases(List<String> tokens) {
        Map<String, Phrase> phrases = new LinkedHashMap<>();
        int maximumSize = Math.min(MAX_PHRASE_TOKEN_COUNT, tokens.size());

        for (int tokenCount = maximumSize; tokenCount >= 1; tokenCount--) {
            for (int start = 0; start + tokenCount <= tokens.size(); start++) {
                String phrase = String.join("", tokens.subList(start, start + tokenCount));
                phrases.putIfAbsent(normalize(phrase), new Phrase(phrase, tokenCount));
            }
        }
        return new ArrayList<>(phrases.values());
    }

    private int minimumSupport(int productCount) {
        if (productCount < 2) {
            return Integer.MAX_VALUE;
        }
        if (productCount < 5) {
            return 2;
        }
        double supportRate = productCount <= 10 ? 0.30 : 0.20;
        return Math.max(2, (int) Math.ceil(productCount * supportRate));
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    public record ProductSource(int id, String productName, String category) {
    }

    private record Phrase(String text, int tokenCount) {
        private String key() {
            return text.toLowerCase(Locale.ROOT);
        }
    }

    private record PhraseSupport(String text, int tokenCount, Set<String> productIdentities) {
        private PhraseSupport(String text, int tokenCount) {
            this(text, tokenCount, new LinkedHashSet<>());
        }

        private int count() {
            return productIdentities.size();
        }
    }
}
