package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface PublisherRepository extends JpaRepository<Publisher,String> {
    // 인수가 이름(String)의 Set이므로 Name으로 조회하도록 수정해야 합니다.
    List<Publisher> findAllByNameIn(Set<String> allPublisherNames);
}
