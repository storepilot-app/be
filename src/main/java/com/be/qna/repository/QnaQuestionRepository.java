package com.be.qna.repository;

import com.be.qna.domain.QnaQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QnaQuestionRepository extends JpaRepository<QnaQuestion, Long> {
    List<QnaQuestion> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<QnaQuestion> findAllByOrderByCreatedAtDesc();

    Optional<QnaQuestion> findByIdAndUserId(Long id, Long userId);
}
