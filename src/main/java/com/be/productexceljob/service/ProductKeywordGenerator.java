package com.be.productexceljob.service;

import com.be.keyword.CategoryTokenExtractor;
import com.be.keyword.KeywordCandidateRanker;
import com.be.keyword.KeywordCandidateRanker.ScoredKeyword;
import com.be.keyword.KeywordCombinationTemplate;
import com.be.keyword.KeywordSynonymDictionary;
import com.be.keyword.KeywordSynonymDictionary.SynonymExpansion;
import com.be.keyword.ProductNameTokenExtractor;
import com.be.keyword.SimilarProductRepeatedPhraseExtractor;
import com.be.keyword.SimilarProductRepeatedPhraseExtractor.ProductSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductKeywordGenerator {
    private final CategoryTokenExtractor categoryTokenExtractor;
    private final KeywordCandidateRanker keywordCandidateRanker;
    private final KeywordCombinationTemplate keywordCombinationTemplate;
    private final KeywordSynonymDictionary keywordSynonymDictionary;
    private final ProductNameTokenExtractor productNameTokenExtractor;
    private final SimilarProductRepeatedPhraseExtractor similarProductRepeatedPhraseExtractor;

    public Map<Integer, List<GeneratedKeyword>> generate(
            List<ProductKeywordSource> products,
            int keywordCount
    ) {
        if (products == null || products.isEmpty()) {
            return Map.of();
        }

        Map<Integer, List<String>> repeatedPhrases = similarProductRepeatedPhraseExtractor.extract(
                products.stream()
                        .map(product -> new ProductSource(
                                product.rowId(),
                                product.productName(),
                                product.category()
                        ))
                        .toList()
        );
        Map<Integer, List<GeneratedKeyword>> keywordsByRow = new LinkedHashMap<>();
        for (ProductKeywordSource product : products) {
            keywordsByRow.put(
                    product.rowId(),
                    generateKeywords(
                            product.productName(),
                            product.category(),
                            repeatedPhrases.getOrDefault(product.rowId(), List.of()),
                            keywordCount
                    )
            );
        }
        return Map.copyOf(keywordsByRow);
    }

    private List<GeneratedKeyword> generateKeywords(
            String productName,
            String category,
            List<String> repeatedPhrases,
            int keywordCount
    ) {
        List<String> productTokens = productNameTokenExtractor.extract(productName);
        List<String> categoryTokens = categoryTokenExtractor.extract(category);
        List<String> synonymSources = new ArrayList<>();
        synonymSources.addAll(productTokens);
        synonymSources.addAll(categoryTokens);
        synonymSources.addAll(repeatedPhrases);
        List<SynonymExpansion> synonymExpansions = keywordSynonymDictionary.findExpansions(synonymSources);
        List<String> synonyms = synonymExpansions.stream()
                .map(SynonymExpansion::keyword)
                .toList();

        List<String> candidates = keywordCombinationTemplate.generate(
                productTokens,
                categoryTokens,
                repeatedPhrases,
                synonyms
        );
        return keywordCandidateRanker.rank(
                        candidates,
                        productTokens,
                        categoryTokens,
                        repeatedPhrases,
                        synonyms
                ).stream()
                .limit(keywordCount)
                .map(score -> new GeneratedKeyword(
                        score,
                        resolveKeywordReasons(
                                score.keyword(),
                                productTokens,
                                categoryTokens,
                                repeatedPhrases,
                                synonymExpansions
                        )
                ))
                .toList();
    }

    private List<String> resolveKeywordReasons(
            String keyword,
            List<String> productTokens,
            List<String> categoryTokens,
            List<String> repeatedPhrases,
            List<SynonymExpansion> synonymExpansions
    ) {
        Set<String> reasons = new LinkedHashSet<>();
        if (containsKeyword(categoryTokens, keyword)) {
            reasons.add("카테고리 핵심어");
        }
        if (containsKeyword(repeatedPhrases, keyword)) {
            reasons.add("유사상품 반복 표현");
        }
        synonymExpansions.stream()
                .filter(expansion -> sameKeyword(expansion.keyword(), keyword))
                .map(expansion -> "동의어 치환: " + expansion.sourceTerm() + " → " + expansion.keyword())
                .forEach(reasons::add);
        if (containsKeyword(productTokens, keyword)) {
            reasons.add("상품명 토큰");
        }
        if (isProductTokenCombination(keyword, productTokens)) {
            reasons.add("상품명 토큰 조합");
        }
        if (isProductCategoryCombination(keyword, productTokens, categoryTokens)) {
            reasons.add("상품명 + 카테고리 조합");
        }
        if (reasons.isEmpty()) {
            reasons.add("조합 템플릿");
        }
        return List.copyOf(reasons);
    }

    private boolean containsKeyword(List<String> values, String keyword) {
        return values.stream().anyMatch(value -> sameKeyword(value, keyword));
    }

    private boolean isProductTokenCombination(String keyword, List<String> productTokens) {
        if (productTokens.size() >= 2 && sameKeyword(String.join("", productTokens), keyword)) {
            return true;
        }
        for (int index = 0; index + 1 < productTokens.size(); index++) {
            if (sameKeyword(productTokens.get(index) + productTokens.get(index + 1), keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProductCategoryCombination(
            String keyword,
            List<String> productTokens,
            List<String> categoryTokens
    ) {
        if (categoryTokens.isEmpty()) {
            return false;
        }
        String primaryCategory = categoryTokens.getFirst();
        for (int index = 0; index < productTokens.size(); index++) {
            if (sameKeyword(combineWithCategory(List.of(productTokens.get(index)), primaryCategory), keyword)) {
                return true;
            }
            if (index + 1 < productTokens.size()
                    && sameKeyword(
                    combineWithCategory(productTokens.subList(index, index + 2), primaryCategory),
                    keyword
            )) {
                return true;
            }
        }
        return false;
    }

    private String combineWithCategory(List<String> tokens, String category) {
        StringBuilder prefix = new StringBuilder();
        String normalizedCategory = normalizeKeyword(category);
        for (String token : tokens) {
            String normalizedToken = normalizeKeyword(token);
            if (!normalizedCategory.contains(normalizedToken) && !normalizedToken.contains(normalizedCategory)) {
                prefix.append(token);
            }
        }
        return prefix.isEmpty() ? category : prefix + category;
    }

    private boolean sameKeyword(String first, String second) {
        return normalizeKeyword(first).equals(normalizeKeyword(second));
    }

    private String normalizeKeyword(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    public record ProductKeywordSource(
            int rowId,
            String productName,
            String category
    ) {
    }

    public record GeneratedKeyword(
            ScoredKeyword score,
            List<String> reasons
    ) {
        public GeneratedKeyword {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }
}
