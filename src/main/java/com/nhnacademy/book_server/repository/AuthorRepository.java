package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AuthorRepository extends JpaRepository<Author,Long> {

    Optional<Author> findByName(String name);

    List<Author> findAllByNameIn(Set<String> names);
}
