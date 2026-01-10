package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BookRepository extends JpaRepository<Book, Long> {

    // 이미 등록된 ISBN13 인지 확인하기 위해 사용
    boolean existsByIsbn13(String isbn);
    Optional<Book> findByIsbn13(String isbn);

    // 파싱 시 중복 데이터를 미리 걸러내기 위해 사용
    List<Book> findAllByIsbn13In(Set<String> isbns);

    // 페이지를 조회할때마다 메서드를 계속 호출하지 않고
    // 한번만 조회하도록 메서드 수정

    @EntityGraph(attributePaths = {"bookAuthors", "bookAuthors.author"})
    Page<Book> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"bookAuthors", "bookAuthors.author"})
    Optional<Book> findById(Long id);

    List<Book> findTop5ByPublishedDateBetweenOrderByIdAsc(String start,String end);

    List<Book> findTop5ByOrderByIdDesc();
    List<Book> findTop5ByOrderByIdAsc();

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Book b SET " +
            "b.reviewCount = (SELECT COUNT(r) FROM Review r WHERE r.book.id = :bookId), " +
            "b.averageRating = COALESCE((SELECT AVG(r.rating) FROM Review r WHERE r.book.id = :bookId), 0.0) " +
            "WHERE b.id = :bookId")
    void updateBookReviewStats(@Param("bookId") Long bookId);
    List<Book> findTop200ByIdGreaterThanOrderByIdAsc(Long id);
    List<Book> findByIdIn(List<Long> ids); //카테고리-> 북리스트 후 정렬


    @Query(value = "SELECT bc FROM BookCategory bc " +
            "JOIN FETCH bc.book b " +
            "LEFT JOIN FETCH b.publisher p " +
            "WHERE bc.category.categoryId = :categoryId",
            countQuery = "SELECT count(bc) FROM BookCategory bc WHERE bc.category.categoryId = :categoryId")
        Page<BookCategory> findBooksByCategory(@Param("categoryId") int categoryId, Pageable pageable);


    @Query("SELECT b FROM Book b " +
            "WHERE b.id > :lastId " +
            "AND NOT EXISTS (SELECT bc FROM BookCategory bc WHERE bc.book = b) " +
            "ORDER BY b.id ASC")
    List<Book> findNextBatch(@Param("lastId") Long lastId, Pageable pageable);

    List<Book> findByOrderBySalesVolumeDesc(Pageable pageable);
}
