package com.nhnacademy.book_server.repository.review;

import com.nhnacademy.book_server.entity.BookReviewAi;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BookReviewAiRepository extends JpaRepository<BookReviewAi, Long> {
    Optional<BookReviewAi> findByBook_Id(Long bookId);
}
