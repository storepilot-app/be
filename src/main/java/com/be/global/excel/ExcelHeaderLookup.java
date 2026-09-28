package com.be.global.excel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

public final class ExcelHeaderLookup {
    private final List<HeaderEntry> entries;
    private final Map<String, List<Integer>> exactIndexes;
    private final Map<String, List<Integer>> whitespaceIgnoredIndexes;

    private ExcelHeaderLookup(
            List<HeaderEntry> entries,
            Map<String, List<Integer>> exactIndexes,
            Map<String, List<Integer>> whitespaceIgnoredIndexes
    ) {
        this.entries = entries;
        this.exactIndexes = exactIndexes;
        this.whitespaceIgnoredIndexes = whitespaceIgnoredIndexes;
    }

    public static ExcelHeaderLookup from(Row headerRow) {
        List<HeaderEntry> entries = new ArrayList<>();
        Map<String, List<Integer>> exactIndexes = new HashMap<>();
        Map<String, List<Integer>> whitespaceIgnoredIndexes = new HashMap<>();
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell).trim();
            entries.add(new HeaderEntry(header, cell.getColumnIndex()));
            exactIndexes.computeIfAbsent(header, ignored -> new ArrayList<>()).add(cell.getColumnIndex());
            whitespaceIgnoredIndexes.computeIfAbsent(removeWhitespace(header), ignored -> new ArrayList<>())
                    .add(cell.getColumnIndex());
        }
        return new ExcelHeaderLookup(entries, exactIndexes, whitespaceIgnoredIndexes);
    }

    public int findFirst(String header) {
        List<Integer> indexes = findAll(header);
        return indexes.isEmpty() ? -1 : indexes.getFirst();
    }

    public List<Integer> findAll(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return List.copyOf(exactIndexes.getOrDefault(header, List.of()));
    }

    public int findFirstIgnoringWhitespace(String header) {
        List<Integer> indexes = findAllIgnoringWhitespace(header);
        return indexes.isEmpty() ? -1 : indexes.getFirst();
    }

    public List<Integer> findAllIgnoringWhitespace(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return List.copyOf(whitespaceIgnoredIndexes.getOrDefault(removeWhitespace(header), List.of()));
    }

    public int findFirstNormalized(Collection<String> acceptedHeaders) {
        Set<String> normalizedHeaders = new HashSet<>();
        for (String header : acceptedHeaders) {
            normalizedHeaders.add(normalize(header));
        }
        return entries.stream()
                .filter(entry -> normalizedHeaders.contains(normalize(entry.value())))
                .mapToInt(HeaderEntry::columnIndex)
                .findFirst()
                .orElse(-1);
    }

    public static String normalize(String value) {
        return value == null ? "" : removeWhitespace(value).toLowerCase(Locale.ROOT);
    }

    private static String removeWhitespace(String value) {
        return value.replaceAll("\\s+", "");
    }

    private record HeaderEntry(String value, int columnIndex) {
    }
}
