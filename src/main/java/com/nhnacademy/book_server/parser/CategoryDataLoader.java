//package com.nhnacademy.book_server.parser;
//
//import com.nhnacademy.book_server.service.category.CategoryBackfillService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class CategoryDataLoader implements CommandLineRunner {
//
//    private final CategoryBackfillService categoryBackfillService;
//
//    @Override
//    public void run(String... args) {
//        log.info("========== CATEGORY BACKFILL START ==========");
//        categoryBackfillService.backfillAllBooks();
//        log.info("========== CATEGORY BACKFILL END ==========");
//    }
//}
