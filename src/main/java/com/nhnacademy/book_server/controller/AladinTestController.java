package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.entity.AladinItem;
import com.nhnacademy.book_server.service.impl.AladinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/test/aladin")
public class AladinTestController {

    private final AladinService aladinService;

    /**
     * ISBN13로 상세조회(lookup)
     * 예) GET /api/test/aladin/lookup?isbn13=9788936434120
     */
    @GetMapping("/lookup")
    public Object lookup(@RequestParam String isbn13) {
        return aladinService.lookupBook(isbn13);
    }

    /**
     * 리스트 조회(ItemList)
     * 예) GET /api/test/aladin/list?queryType=Bestseller
     */
    @GetMapping("/list")
    public ResponseEntity<List<AladinItem>> list(@RequestParam String queryType) {
        return ResponseEntity.ok(aladinService.getBookList(queryType));
    }

    /**
     * 검색(ItemSearch)
     * 예) GET /api/test/aladin/search?query=자바&queryType=Title
     */
    @GetMapping("/search")
    public ResponseEntity<List<AladinItem>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "Title") String queryType
    ) {
        return ResponseEntity.ok(aladinService.searchBooks(query, queryType));
    }
}
