package com.nhnacademy.book_server.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "order-service")
public interface OrderFeignClient {

    @PostMapping("/books/bulk")
    List<Boolean> getBooksBulk(@RequestBody List<Long> bookIds);
}