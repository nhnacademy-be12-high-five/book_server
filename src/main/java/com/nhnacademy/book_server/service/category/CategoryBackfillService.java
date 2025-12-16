//package com.nhnacademy.book_server.service.category;
//
//import com.nhnacademy.book_server.dto.response.AladinItemLookUpResponse;
//import com.nhnacademy.book_server.entity.AladinItem;
//import com.nhnacademy.book_server.entity.Book;
//import com.nhnacademy.book_server.repository.BookRepository;
//import com.nhnacademy.book_server.service.category.CategoryMappingService;
//import com.nhnacademy.book_server.service.impl.AladinService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class CategoryBackfillService {
//
//    private final BookRepository bookRepository;
//    private final AladinService aladinService;                 // lookupBook 사용 :contentReference[oaicite:1]{index=1}
//    private final CategoryMappingService categoryMappingService;
//
//    private static final int BATCH_SIZE = 200; // 쿼터/속도 고려(처음엔 200 추천)
//
//    public void backfillAllBooks() {
//        long lastId = 0L;
//        long processed = 0L;
//        long mapped = 0L;
//        long failed = 0L;
//
//        while (true) {
//            List<Book> books = bookRepository.findTop200ByIdGreaterThanOrderByIdAsc(lastId);
//            if (books.isEmpty()) break;
//
//            for (Book book : books) {
//                lastId = book.getId();
//                processed++;
//
//                try {
//                    AladinItem item = aladinService.lookupBookFromApi(book.getIsbn13());
//
//                    log.info("LOOKUP OK isbn={}, catId=[{}], catName=[{}]",
//                            book.getIsbn13(),
//                            item == null ? "null" : String.valueOf(item.getCategoryId()),
//                            item == null ? "null" : item.getCategoryName());
//
//                    if (item == null || item.getCategoryId() == null || item.getCategoryName() == null) {
//                        log.warn("SKIP mapping isbn={} (no category)", book.getIsbn13());
//                        continue;
//                    }
//
//                    categoryMappingService.upsertCategoryAndMap(book, item.getCategoryId(), item.getCategoryName());
//                    mapped++;
//
//                } catch (Exception e) {
//                    failed++;
//                }
//            }
//
//            log.info("BACKFILL progress lastId={}, processed={}, mapped={}, failed={}",
//                    lastId, processed, mapped, failed);
//        }
//
//        log.info("BACKFILL DONE processed={}, mapped={}, failed={}", processed, mapped, failed);
//    }
//
//
//    @Transactional
//    protected void mapOne(Book book, Integer categoryId, String categoryName) {
//        categoryMappingService.upsertCategoryAndMap(book, categoryId, categoryName);
//    }
//}
