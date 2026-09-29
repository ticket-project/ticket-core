package com.ticket.show.persistence;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.ticket.show.domain.Grade;
import com.ticket.show.domain.GradeRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class GradeRepositoryAdapter implements GradeRepository {
    private final SpringDataGradeJpaRepository jpaRepository;

    @Override
    public Map<Long, Grade> findGradeNames(final Collection<Long> gradeIds) {
        if (gradeIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findAllById(gradeIds).stream()
                .collect(Collectors.toMap(Grade::getId, gradeEntity -> gradeEntity));
    }
}
