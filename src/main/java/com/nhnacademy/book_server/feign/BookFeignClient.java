package com.nhnacademy.book_server.feign;

import com.nhnacademy.book_server.entity.Book;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "book-service")
public interface BookFeignClient {

    @GetMapping("/api/books/bulk-lookup")
    List<Book> getBooksBulk(@RequestBody List<Long> bookIds);
}

