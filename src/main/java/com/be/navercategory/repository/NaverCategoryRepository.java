package com.be.navercategory.repository;

import com.be.navercategory.domain.NaverCategory;
import com.be.navercategory.domain.NaverCategoryVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Repository;

@Repository
public class NaverCategoryRepository {
    private final AtomicReference<NaverCategoryVersion> activeVersion = new AtomicReference<>();
    private final AtomicReference<List<NaverCategory>> activeCategories = new AtomicReference<>(List.of());

    public void replaceActive(NaverCategoryVersion version, List<NaverCategory> categories) {
        activeVersion.set(version);
        activeCategories.set(List.copyOf(categories));
    }

    public Optional<NaverCategoryVersion> findActiveVersion() {
        return Optional.ofNullable(activeVersion.get());
    }

    public List<NaverCategory> findActiveCategories() {
        return new ArrayList<>(activeCategories.get());
    }
}
