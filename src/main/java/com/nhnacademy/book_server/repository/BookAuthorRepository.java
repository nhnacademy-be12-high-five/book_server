package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.BookAuthor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookAuthorRepository extends JpaRepository<BookAuthor,BookAuthor.Pk> {
    // 특정 책에 연관된 모든 저자 매핑 정보를 가져옴 (필요 시 사용)
    List<BookAuthor> findAllByBook_Id(Long bookId);
}
