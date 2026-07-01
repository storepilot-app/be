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
public class ProductNameTokenExtractor {
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[\\s_/|,(){}\\[\\]（）]+");
    private static final Pattern QUANTITY_TOKEN_PATTERN = Pattern.compile(
            "^\\d+(?:\\.\\d+)?(?:(?i:mg|kg|g|ml|l|cm|m|set|ea|p)|개|개입|매|세트|팩|박스|입|봉|정|캡슐)$"
    );
    private static final Pattern PROMOTION_QUANTITY_PATTERN = Pattern.compile("^\\d+\\+\\d+$");
    private static final Pattern PURE_NUMBER_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+)?$");
    private static final Pattern EDGE_PUNCTUATION_PATTERN = Pattern.compile("^[+.-]+|[+.-]+$");
    private static final Pattern INVALID_CHARACTER_PATTERN = Pattern.compile("[^\\p{IsHangul}A-Za-z0-9+.-]");
    private static final Set<String> SALES_STOP_WORDS = Set.of(
            "무료배송",
            "당일배송",
            "오늘출발",
            "해외배송",
            "국내배송",
            "사은품",
            "증정",
            "할인",
            "특가",
            "이벤트",
            "최저가",
            "인기",
            "추천",
            "신상품",
            "새상품"
    );

    public List<String> extract(String productName) {
        if (productName == null || productName.isBlank()) {
            return List.of();
        }

        String normalized = Normalizer.normalize(productName, Normalizer.Form.NFKC);
        String[] rawTokens = SEPARATOR_PATTERN.matcher(normalized).replaceAll(" ").trim().split(" +");
        List<String> tokens = new ArrayList<>();
        Set<String> deduplicationKeys = new HashSet<>();

        for (String rawToken : rawTokens) {
            String token = clean(rawToken);
            if (!isUsable(token)) {
                continue;
            }
            String deduplicationKey = token.toLowerCase(Locale.ROOT);
            if (deduplicationKeys.add(deduplicationKey)) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    private String clean(String value) {
        String cleaned = INVALID_CHARACTER_PATTERN.matcher(value).replaceAll("");
        return EDGE_PUNCTUATION_PATTERN.matcher(cleaned).replaceAll("").trim();
    }

    private boolean isUsable(String token) {
        if (token.length() < 2) {
            return false;
        }
        if (PURE_NUMBER_PATTERN.matcher(token).matches()) {
            return false;
        }
        if (QUANTITY_TOKEN_PATTERN.matcher(token).matches()) {
            return false;
        }
        if (PROMOTION_QUANTITY_PATTERN.matcher(token).matches()) {
            return false;
        }
        return !SALES_STOP_WORDS.contains(token.toLowerCase(Locale.ROOT));
    }
}
