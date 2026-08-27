package com.ticket.core.infra.show;

import com.ticket.core.domain.show.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataCategoryJpaRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByIdAsc();
}
