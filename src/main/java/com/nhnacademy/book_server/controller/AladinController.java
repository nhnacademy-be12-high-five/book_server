package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.entity.AladinItem;
import com.nhnacademy.book_server.service.impl.AladinServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/aladin")
@RequiredArgsConstructor
public class AladinController {

    private final AladinServiceImpl aladinService;

    // 전체 조회
    @GetMapping("/search")
    public List<AladinItem> search(@RequestParam String query, @RequestParam String queryType) {
        return aladinService.searchBooks(query, queryType);
    }

    //상세 세부 조회
    @GetMapping("/lookup")
    public AladinItem lookup(@RequestParam String isbn13){
        return aladinService.lookupBook(isbn13);
    }

    // 베스트 셀러 신간 조회
    @GetMapping("/list")
    public List<AladinItem> getList(@RequestParam(defaultValue = "Bestseller") String queryType) {
        // QueryType: Bestseller, ItemNewAll, ItemNewSpecial 등
        return aladinService.getBookList(queryType);
    }
}
