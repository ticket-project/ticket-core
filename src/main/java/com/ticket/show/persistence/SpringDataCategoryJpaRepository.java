package com.ticket.show.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.show.domain.Category;

interface SpringDataCategoryJpaRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderByIdAsc();
}
