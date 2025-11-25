package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookUpdateRequest;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.repository.BookRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@Transactional
public class BookService {
    private final BookRepository bookRepository;

    private Book book;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

        @Transactional
        public Book createBook(ParsingDto dto){
            return bookRepository.save(book);
        }

        // 모든 책 조회
        @Transactional(readOnly = true)
        public List<Book> findAllBooks(String userId){
            return bookRepository.findAll();
        }

        // 책 한권 조회
        @Transactional(readOnly = true)
        public Optional<Book> findBookById(Long id, String userId) {
            return bookRepository.findById(id);
        }

        // 책 업데이트
        @Transactional // 💡 트랜잭션 적용
        public Book updateBook(Long id, BookUpdateRequest request, String userId){
            Book existingBook=bookRepository.findById(id).orElseThrow(()->new RuntimeException("아이디가 존재하지 않습니다."));

            return bookRepository.save(existingBook);
        }

        // 책 삭제
        public void deleteBook(Long id,String userId){
            Book deletebook=bookRepository.findById(id).orElseThrow(()->new RuntimeException("삭제할 아이디가 없습니다."));

            bookRepository.deleteById(id);
        }
    }

