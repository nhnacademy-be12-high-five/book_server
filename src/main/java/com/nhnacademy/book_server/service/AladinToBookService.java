//package com.nhnacademy.book_server.service;
//
//package com.nhnacademy.book_server.service;
//
//import com.nhnacademy.book_server.dto.aladin.AladinResponse;
//import com.nhnacademy.book_server.dto.aladin.BookItem;
//import com.nhnacademy.book_server.entity.Book;
//import com.nhnacademy.book_server.parser.ParsingDto;
//import com.nhnacademy.book_server.response.AladinSearchResponse;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//@Service
//@Slf4j
//@RequiredArgsConstructor
//public class AladinToBookService {
//
//    private final AladinService aladinService; // RestTemplate 서비스로 변경
//    private final BookService bookService; // 기존 BookService
//
//    @Transactional
//    public Book registerBookByIsbn(String isbn) {
//
//        // 1. RestTemplate 서비스 호출 (동기적)
//        AladinSearchResponse aladinResponse = (AladinSearchResponse) aladinService.searchBooks(isbn, "ISBN");
//
//        if (aladinResponse.getItem() == null || aladinResponse.getItem().isEmpty()) {
//            throw new RuntimeException("ISBN에 해당하는 책 정보를 찾을 수 없습니다: " + isbn);
//        }
//
//        // 2. 검색 결과 처리 및 DTO 변환
//        Book bookItem = aladinResponse.getItem().get(0);
//        ParsingDto dto = convertToParsingDto(bookItem);
//
//        // 3. 기존 BookService의 createBook 메서드 호출하여 DB에 저장
//        // 이 과정 전체가 @Transactional로 묶여 안전하게 처리됩니다.
//        Book savedBook = bookService.createBook(dto);
//
//        log.info("알라딘 API를 통해 도서 등록 완료: ISBN={}", savedBook.getIsbn());
//        return savedBook;
//    }
//
//    // BookItem POJO를 기존 BookService가 사용하는 ParsingDto로 변환하는 메서드
//    private ParsingDto convertToParsingDto(Book bookItem) {
//
//    }
//}