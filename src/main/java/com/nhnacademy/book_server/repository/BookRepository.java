package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.parser.ParsingDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BookRepository extends JpaRepository<Book, Long> {

    // 이미 등록된 ISBN13 인지 확인하기 위해 사용
    boolean existsByIsbn13(String isbn);

    Optional<Book> findByIsbn13(String isbn);

    // 파싱 시 중복 데이터를 미리 걸러내기 위해 사용
    List<Book> findAllByIsbn13In(Set<String> isbns);

<<<<<<< Updated upstream
    List<Book> findAllByIdIn(List<Long> bookIds);

    Optional<Book> findAllByIsbn13(String isbn13);


    //검색 키워드를 제목이나 설명에 포함하는 도서 검색 (페이지네이션)
    Page<Book> findByTitle(
            String titleKeyword,
            String descriptionKeyword,
            Pageable pageable
    );
=======
>>>>>>> Stashed changes
}
