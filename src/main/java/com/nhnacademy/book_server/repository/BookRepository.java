package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BookRepository extends JpaRepository<Book, Long> {

    // 이미 등록된 ISBN13 인지 확인하기 위해 사용
    boolean existsByIsbn13(String isbn);

    Optional<Book> findByIsbn13(String isbn);

    // 파싱 시 중복 데이터를 미리 걸러내기 위해 사용
    List<Book> findAllByIsbn13In(Set<String> isbns);

    List<Book> findAllByIdIn(List<Long> bookIds);


}
