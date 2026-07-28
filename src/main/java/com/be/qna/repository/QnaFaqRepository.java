package com.be.qna.repository;

import com.be.qna.domain.QnaFaq;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QnaFaqRepository extends JpaRepository<QnaFaq, Long> {
    List<QnaFaq> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<QnaFaq> findAllByOrderBySortOrderAscIdAsc();
}
