package com.ticket.show.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ticket.show.domain.Grade;
import com.ticket.show.domain.GradeRepository;

import lombok.RequiredArgsConstructor;

/** {@link GradeRepository}의 JPA 구현이다. */
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
