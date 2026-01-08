package com.nhnacademy.book_server.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.CategoryResponse;
import com.nhnacademy.book_server.dto.SearchResult;
import com.nhnacademy.book_server.dto.response.TagResponse;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Review;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.review.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService implements RagSearchable {

    private static final String INDEX = "emb-high-five"; // book_index.json과 일치하는 인덱스명
    private static final int PAGE_SIZE = 100;

    private static final String FIELD_PUBLISHED_DATE = "publishedDate";

    private final ElasticsearchClient client;
    private final EmbeddingClientService embeddingClientService; // Ollama 구현체 주입됨
    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;

    @Override
    public SearchResult<BookResponse> searchByRag(String keyword, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return new SearchResult<>(List.of(), 0L);
        }

        try {
            // 1. 임베딩 (1024차원)
            List<Float> queryVector = embeddingClientService.embed(keyword);

            if (queryVector == null || queryVector.isEmpty()) {
                return new SearchResult<>(List.of(), 0L);
            }

            int topK = size;
            int numCandidates = topK * 3; // 후보군 여유 있게 설정

            // 2. KNN 검색
            @SuppressWarnings("unchecked")
            SearchResponse<Map<String, Object>> response = (SearchResponse<Map<String, Object>>) (Object) client.search(s -> s
                            .index(INDEX)
                            .knn(knn -> knn
                                    .field("vector") // [중요] book_index.json의 필드명 "vector" 사용
                                    .queryVector(queryVector)
                                    .k(topK)
                                    .numCandidates(numCandidates)
                            )
                            .from(page * size)
                            .size(size),
                    Map.class
            );

            long totalHits = response.hits().total() != null
                    ? response.hits().total().value()
                    : response.hits().hits().size();

            List<BookResponse> books = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::toBookResponse)
                    .toList();

            return new SearchResult<>(books, totalHits);

        } catch (Exception exception) {
            log.error("RAG 검색 중 예외 발생 keyword={}", keyword, exception);
            return new SearchResult<>(List.of(), 0L);
        }
    }

    private BookResponse toBookResponse(Map<String, Object> source) {
        if (source == null) return null;

        Long bookId = null;
        if (source.get("id") instanceof Number n) bookId = n.longValue();
        else if (source.get("bookId") instanceof Number n) bookId = n.longValue();

        String title = (String) source.get("title");
        String author = (String) source.get("author");
        String isbn = (String) source.get("isbn");

        Integer price = null;
        if (source.get("price") instanceof Number n) price = n.intValue();

        String image = (String) source.get("image");
        String content = (String) source.get("content");
        String publisher = (String) source.get("publisher");

        String publishedDate = null;
        if (source.get(FIELD_PUBLISHED_DATE) != null) publishedDate = source.get(FIELD_PUBLISHED_DATE).toString();

        Double avgRating = null;
        if (source.get("avgRating") instanceof Number n) avgRating = n.doubleValue();

        Long reviewCount = 0L;
        if (source.get("reviewCount") instanceof Number n) reviewCount = n.longValue();

        String aiSummary = (String) source.get("aiSummary");

        return new BookResponse(
                bookId, title, author, isbn, price, image,
                Collections.<CategoryResponse>emptyList(), // 카테고리 리스트
                Collections.<TagResponse>emptyList(),      // 태그 리스트
                content, publisher, publishedDate, avgRating, reviewCount, aiSummary, null,
                null, null
        );
    }

    @Async
    @Override
    @Transactional(readOnly = true)
    public void reindexBooks() {
        log.info("RAG reindex 시작 - {} 전체 재색인", INDEX);
        try {
            int pageNumber = 0;
            long totalIndexed = 0;
            Page<Book> bookPage;

            do {
                bookPage = bookRepository.findAll(PageRequest.of(pageNumber, PAGE_SIZE));
                if (bookPage.isEmpty()) {
                    break;
                }

                List<Book> books = bookPage.getContent();
                processBatch(books); // 배치 처리 로직 분리

                totalIndexed += books.size();
                log.info("RAG reindex 진행: {}권 완료", totalIndexed);
                pageNumber++;

            } while (bookPage.hasNext());
        } catch (Exception e) {
            log.error("RAG reindex 전체 실패", e);
        }
    }

    // [Helper Method 1] 배치 단위 처리 (리뷰 조회 및 Bulk 요청)
    private void processBatch(List<Book> books) throws IOException {
        List<Long> bookIds = books.stream().map(Book::getId).toList();
        List<Review> allReviews = reviewRepository.findByBookIdIn(bookIds);
        Map<Long, List<Review>> reviewMap = allReviews.stream()
                .collect(Collectors.groupingBy(r -> r.getBook().getId()));

        List<BulkOperation> operations = new ArrayList<>();

        for (Book book : books) {
            BulkOperation op = createIndexOperation(book, reviewMap);
            if (op != null) {
                operations.add(op);
            }
        }

        if (!operations.isEmpty()) {
            client.bulk(b -> b.index(INDEX).operations(operations));
        }
    }

    private BulkOperation createIndexOperation(Book book, Map<Long, List<Review>> reviewMap) {
        try {
            BookResponse bookResponse = BookResponse.from(book, null, reviewMap.getOrDefault(book.getId(), List.of()));
            String embeddingText = buildEmbeddingText(bookResponse);
            List<Float> embeddingVector = embeddingClientService.embed(embeddingText);

            if (!isValidEmbedding(embeddingVector, book.getId())) {
                return null;
            }

            Map<String, Object> document = new HashMap<>();
            document.put("bookId", bookResponse.bookId());
            document.put("title", bookResponse.title());
            document.put("author", bookResponse.author());
            document.put("isbn", bookResponse.isbn());
            document.put("price", bookResponse.price());
            document.put("image", bookResponse.image());
            document.put("content", bookResponse.content());
            document.put("publisher", bookResponse.publisher());
            document.put(FIELD_PUBLISHED_DATE, bookResponse.publishedDate()); // [수정] 상수 사용
            document.put("avgRating", bookResponse.avgRating());
            document.put("reviewCount", bookResponse.reviewCount());
            document.put("vector", embeddingVector);
            document.put("aiSummary", buildSimpleSummary(bookResponse));

            return BulkOperation.of(o -> o
                    .index(i -> i.index(INDEX).id(String.valueOf(book.getId())).document(document))
            );
        } catch (Exception e) {
            log.error("RAG reindex 개별 실패 id={}", book.getId());
            return null;
        }
    }

    private boolean isValidEmbedding(List<Float> embeddingVector, Long bookId) {
        if (embeddingVector == null || embeddingVector.isEmpty()) {
            log.warn("RAG reindex: 임베딩 생성 실패, 도서 건너뜀 bookId={}", bookId);
            return false;
        }
        if (embeddingVector.size() != 1024) {
            log.warn("RAG reindex: 임베딩 차원 불일치, 도서 건너뜀 bookId={} expected=1024 actual={}",
                    bookId, embeddingVector.size());
            return false;
        }
        return true;
    }

    private String buildEmbeddingText(BookResponse book) {
        return (book.title() != null ? book.title() + ". " : "") +
                (book.author() != null ? "저자: " + book.author() + ". " : "") +
                (book.publisher() != null ? "출판사: " + book.publisher() + ". " : "") +
                (book.content() != null ? "내용: " + book.content() : "");
    }

    private String buildSimpleSummary(BookResponse book) {
        String content = book.content() != null ? book.content() : "";
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }
}