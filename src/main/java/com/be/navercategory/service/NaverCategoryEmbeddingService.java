package com.be.navercategory.service;

import com.be.navercategory.client.NaverCategoryEmbeddingAiClient;
import com.be.navercategory.domain.NaverCategory;
import com.be.navercategory.repository.NaverCategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NaverCategoryEmbeddingService {
    private final NaverCategoryEmbeddingAiClient naverCategoryEmbeddingAiClient;
    private final NaverCategoryRepository naverCategoryRepository;

    public void rebuildEmbeddings(Long versionId) {
        List<NaverCategory> categories = naverCategoryRepository.findByVersionId(versionId);
        rebuildEmbeddings(versionId, categories);
    }

    public void rebuildEmbeddings(Long versionId, List<NaverCategory> categories) {
        if (!categories.isEmpty()) {
            naverCategoryEmbeddingAiClient.rebuild(versionId, categories);
        }
    }
}
