package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.book;
import com.nhnacademy.book_server.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional

public class bookService {

    private final BookRepository bookRepository;

    public book createBook(book book){
        return bookRepository.save(book);
    }

    // 모든 책 조회
    public List<book> findAllBooks(){
        return bookRepository.findAll();
    }

    // 책 한권 조회
    public Optional<book> findBookById(int id) {
        return bookRepository.findById(id);
    }

    // 책 업데이트
    public book updateBook(int id){
       book existingBook=bookRepository.findById(id).orElseThrow(()->new RuntimeException("아이디가 존재하지 않습니다."));

       return bookRepository.save(existingBook);
    }

    // 책 삭제
    public void deleteBook(int id){
        book deletebook=bookRepository.findById(id).orElseThrow(()->new RuntimeException("삭제할 아이디가 없습니다."));

        bookRepository.deleteById(id);
    }

//    public boolean isAdmin(Long userId) {
//
//        if (userId )
//    }
}
