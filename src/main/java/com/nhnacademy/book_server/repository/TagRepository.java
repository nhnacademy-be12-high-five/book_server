package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.dto.response.TagResponse;
import com.nhnacademy.book_server.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.stream.LongStream;

public interface TagRepository extends JpaRepository<Tag,Long> {

    boolean existsByName(String name);
}
