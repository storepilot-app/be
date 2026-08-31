package com.be.categorymatcher.service;

import com.be.categorymatcher.client.CategoryMatcherAiClient;
import com.be.categorymatcher.dto.CategoryMatchPredictResponse;
import com.be.categorymatcher.dto.CategoryMatchPrediction;
import com.be.categorymatcher.dto.CategoryMatchProductRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CategoryPredictionBatchProcessor {
    private final CategoryMatcherAiClient categoryMatcherAiClient;

    @Value("${storepilot.category.batch-size:300}")
    private int batchSize;

    Map<Integer, CategoryMatchPrediction> predict(
            Long versionId,
            List<CategoryMatchProductRequest> products,
            IntConsumer processedCountConsumer
    ) {
        Map<Integer, CategoryMatchPrediction> predictions = new HashMap<>();
        int totalCount = products.size();
        int safeBatchSize = Math.max(1, batchSize);
        int batchNumber = 0;
        long allBatchesStartedAt = System.nanoTime();

        for (int start = 0; start < totalCount; start += safeBatchSize) {
            batchNumber++;
            int end = Math.min(start + safeBatchSize, totalCount);
            long batchStartedAt = System.nanoTime();
            predictions.putAll(predictBatch(versionId, products.subList(start, end)));
            log.info(
                    "category_batch_timing batch={} batchSize={} processed={} total={} elapsedMs={}",
                    batchNumber,
                    end - start,
                    end,
                    totalCount,
                    elapsedMillis(batchStartedAt)
            );
            processedCountConsumer.accept(end);
        }
        log.info(
                "category_all_batches_timing batches={} products={} elapsedMs={}",
                batchNumber,
                totalCount,
                elapsedMillis(allBatchesStartedAt)
        );
        return predictions;
    }

    private Map<Integer, CategoryMatchPrediction> predictBatch(
            Long versionId,
            List<CategoryMatchProductRequest> products
    ) {
        Optional<CategoryMatchPredictResponse> response = categoryMatcherAiClient.predict(versionId, products);
        if (response.isEmpty()) {
            return Collections.emptyMap();
        }

        List<CategoryMatchPrediction> predictions = response.get().results();
        if (predictions == null || predictions.isEmpty()) {
            return Collections.emptyMap();
        }

        return predictions.stream()
                .collect(Collectors.toMap(
                        CategoryMatchPrediction::rowId,
                        Function.identity(),
                        (first, second) -> first,
                        HashMap::new
                ));
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
