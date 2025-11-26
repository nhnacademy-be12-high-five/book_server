package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {
    Optional<SearchLog> findByKeyword(String keyword);

}
