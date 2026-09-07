package com.ticket.show.infrastructure.grade;

import com.ticket.show.domain.grade.Grade;
import com.ticket.show.domain.grade.repository.GradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link GradeRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class GradeRepositoryAdapter implements GradeRepository {

    private final SpringDataGradeJpaRepository jpaRepository;

    @Override
    public Optional<Grade> findById(final Long gradeId) {
        return jpaRepository.findById(gradeId);
    }

    @Override
    public List<Grade> findAllOrderByCodeAsc() {
        return jpaRepository.findAllByOrderByCodeAsc();
    }
}
