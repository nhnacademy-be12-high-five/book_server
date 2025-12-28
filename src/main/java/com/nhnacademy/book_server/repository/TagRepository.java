package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag,Long> {

    boolean existsByName(String name);
}
