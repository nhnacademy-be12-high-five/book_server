package com.nhnacademy.book_server.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "team5-order-server")
public interface OrderFeignClient {

    @PostMapping("/books/bulk")
    List<Boolean> getBooksBulk(@RequestBody List<Long> bookIds);

    @GetMapping("check-purchase")
    Boolean hasPurchasedBook(@RequestParam("memberId") Long memberId,
                             @RequestParam("bookId") Long bookId);
}