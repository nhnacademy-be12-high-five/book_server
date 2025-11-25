package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface authorRepository extends JpaRepository<Author,Long> {

    List<Author> findAllById(Long id);
    List<Author> findAllByNameIn(Set<String> names);
}
