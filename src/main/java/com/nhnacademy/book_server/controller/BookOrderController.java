package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.dto.request.BookOrderRequest;
import com.nhnacademy.book_server.dto.response.BookOrderResponse;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 주문할때 책이 주문 가능하도록 책의 아이디, 수량을 넘겨주는 컨트롤러
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/books")
public class BookOrderController {

    private final BookRepository bookRepository;

    @PostMapping("/{id}/order")
    public ResponseEntity<BookOrderResponse> bookOrder(@RequestBody BookOrderRequest bookOrderRequest,
                                                       @PathVariable("id") Long id){
        Book book=bookRepository.findById(id).orElseThrow(()-> new RuntimeException("책의 아이디가 존재하지 않습니다."));

        if (book.getStock() < bookOrderRequest.getQuantity()) {
            throw new RuntimeException("재고가 부족합니다. 남은 수량: " + book.getStock());
        }

        BookOrderResponse response=BookOrderResponse.builder()
                .id(book.getId())
                .title(book.getTitle())
                .price(book.getPrice())
                .build();

        return ResponseEntity.ok(response);
    }
}
