package com.ticket.catalog.infrastructure.grade;

import com.ticket.catalog.domain.grade.Grade;
import com.ticket.catalog.domain.grade.repository.GradeRepository;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(GradeRepositoryAdapter.class)
@SuppressWarnings("NonAsciiCharacters")
class GradeRepositoryAdapterTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private GradeRepository repository;

    @Test
    void code_오름차순으로_전체_Grade를_반환한다() throws Exception {
        persistGrade("VIP", "VIP석");
        persistGrade("A", "A석");
        persistGrade("R", "R석");
        flushAndClear();

        List<Grade> result = repository.findAllOrderByCodeAsc();

        assertThat(result).extracting(Grade::getCode)
                .containsExactly("A", "R", "VIP");
    }

    @Test
    void id로_단건_조회한다() throws Exception {
        Grade grade = persistGrade("VIP", "VIP석");
        flushAndClear();

        assertThat(repository.findById(grade.getId())).isPresent();
    }

    @Test
    void 없는_id면_빈_값을_반환한다() {
        assertThat(repository.findById(-1L)).isEmpty();
    }
}
