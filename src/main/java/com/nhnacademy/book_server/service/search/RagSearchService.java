package com.nhnacademy.book_server.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.SearchResult;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Review;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService implements RagSearchable {

    //  RAG용 임베딩 인덱스 이름
    // 필요시 "high-five-embedding" 등으로 변경
    private static final String INDEX = "em-high-five";

    //  DB에서 읽어올 도서 페이지 크기 (일반 리인덱스와 맞추고 싶으면 1000 사용)
    private static final int PAGE_SIZE = 1000;

    private final ElasticsearchClient client;
    private final EmbeddingClientService embeddingClientService;

    //  RAG 재색인을 위해 직접 Book / Review를 주입받아 사용
    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;

    // =========================
    // 1. RAG 검색 (기존 그대로)
    // =========================
    @Override
    public SearchResult<BookResponse> searchByRag(String keyword, int page, int size) {
        // 1. 키워드 검증
        if (keyword == null || keyword.isBlank()) {
            log.warn("RAG 검색: 빈 키워드입니다.");
            return new SearchResult<>(List.of(), 0L);
        }

        try {
            // 2. 쿼리 벡터 생성 (임베딩)
            List<Float> queryVector = embeddingClientService.embed(keyword);

            if (queryVector == null || queryVector.isEmpty()) {
                log.warn("RAG 검색: 임베딩 결과가 비어 있습니다. keyword={}", keyword);
                return new SearchResult<>(List.of(), 0L);
            }

            int topK = size;
            int numCandidates = topK * 3;

            // 3. KNN 검색 실행
            SearchResponse<Map> response = client.search(
                    s -> s.index(INDEX)
                            .knn(knn -> knn
                                    .field("embedding")
                                    .queryVector(queryVector)
                                    .k(topK)
                                    .numCandidates(numCandidates)
                            )
                            .from(page * size)
                            .size(size),
                    Map.class
            );

            // 4. totalHits 계산
            long totalHits;
            if (response.hits().total() != null) {
                totalHits = response.hits().total().value();
            } else {
                totalHits = response.hits().hits().size();
            }

            // 5. hit → BookResponse 매핑
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

    /**
     * ES _source -> BookResponse 매핑
     */
    private BookResponse toBookResponse(Map<String, Object> source) {
        if (source == null) {
            return null;
        }

        Long bookId = null;
        if (source.get("id") != null) {
            bookId = ((Number) source.get("id")).longValue();
        } else if (source.get("bookId") != null) {
            bookId = ((Number) source.get("bookId")).longValue();
        }

        String title = (String) source.get("title");
        String author = (String) source.get("author");
        String isbn = (String) source.get("isbn");

        Integer price = null;
        if (source.get("price") != null) {
            price = ((Number) source.get("price")).intValue();
        }

        String image = (String) source.get("image");

        Integer categoryId = null;
        if (source.get("categoryId") != null) {
            categoryId = ((Number) source.get("categoryId")).intValue();
        }

        String content = (String) source.get("content");
        String publisher = (String) source.get("publisher");

        String publishedDate = null;
        if (source.get("publishedDate") != null) {
            publishedDate = source.get("publishedDate").toString();
        }

        Double avgRating = null;
        if (source.get("avgRating") != null) {
            avgRating = ((Number) source.get("avgRating")).doubleValue();
        }

        Long reviewCount = 0L;
        if (source.get("reviewCount") != null) {
            reviewCount = ((Number) source.get("reviewCount")).longValue();
        }

        String aiSummary = (String) source.get("aiSummary");

        return new BookResponse(
                bookId,
                title,
                author,
                isbn,
                price,
                image,
                categoryId,
                content,
                publisher,
                publishedDate,
                avgRating,
                reviewCount,
                aiSummary,
                null
        );
    }

    // =========================
    // 2. RAG 재색인 (개선 버전)
    // =========================
    @Override
    @Transactional(readOnly = true)
    public void reindexBooks() {
        log.info("RAG reindex 시작 - {} 전체 재색인", INDEX);

        try {
            int pageNumber = 0;
            long totalIndexed = 0;

            while (true) {
                PageRequest pageRequest = PageRequest.of(pageNumber, PAGE_SIZE);
                Page<Book> bookPage = bookRepository.findAll(pageRequest);
                List<Book> books = bookPage.getContent();

                if (books.isEmpty()) {
                    log.info("RAG reindex: 더 이상 인덱싱할 도서가 없습니다. 종료.");
                    break;
                }

                // 1) 현재 페이지 도서 ID 목록
                List<Long> bookIds = books.stream()
                        .map(Book::getId)
                        .toList();

                // 2) 해당 도서들의 리뷰를 한 번에 조회
                List<Review> allReviews = reviewRepository.findByBookIdIn(bookIds);

                // 3) bookId -> 리뷰 목록 매핑
                Map<Long, List<Review>> reviewMap = allReviews.stream()
                        .collect(Collectors.groupingBy(review -> review.getBook().getId()));

                // 4) BulkOperation 리스트
                List<co.elastic.clients.elasticsearch.core.bulk.BulkOperation> operations =
                        new ArrayList<>();

                for (Book book : books) {
                    try {
                        List<Review> reviewsForBook =
                                reviewMap.getOrDefault(book.getId(), List.of());

                        // Book + Review → BookResponse (카테고리는 null)
                        BookResponse bookResponse =
                                BookResponse.from(book, null, reviewsForBook);

                        // 4-1. 임베딩용 텍스트 구성
                        String embeddingText = buildEmbeddingText(bookResponse);

                        // 4-2. 임베딩 생성
                        List<Float> embeddingVector =
                                embeddingClientService.embed(embeddingText);

                        if (embeddingVector == null || embeddingVector.isEmpty()) {
                            log.warn("RAG reindex: 임베딩 생성 실패, 도서 건너뜀 bookId={}", book.getId());
                            continue;
                        }

                        // 필요 시 차원 체크 (예: 768, 1024 등)
                        if (embeddingVector.size() != 768) {
                            log.warn("RAG reindex: 임베딩 차원 불일치, 도서 건너뜀 bookId={} expected=768 actual={}",
                                    book.getId(), embeddingVector.size());
                            continue;
                        }

                        // 4-3. ES에 저장할 문서 구성
                        Map<String, Object> document = new HashMap<>();
                        document.put("bookId", bookResponse.bookId());
                        document.put("title", bookResponse.title());
                        document.put("author", bookResponse.author());
                        document.put("isbn", bookResponse.isbn());
                        document.put("price", bookResponse.price());
                        document.put("image", bookResponse.image());
                        document.put("categoryId", bookResponse.categoryId());
                        document.put("content", bookResponse.content());
                        document.put("publisher", bookResponse.publisher());
                        document.put("publishedDate", bookResponse.publishedDate());
                        document.put("avgRating", bookResponse.avgRating());
                        document.put("reviewCount", bookResponse.reviewCount());
                        document.put("embedding", embeddingVector);

                        String aiSummary = buildSimpleSummary(bookResponse);
                        document.put("aiSummary", aiSummary);

                        // 4-4. BulkOperation 생성
                        co.elastic.clients.elasticsearch.core.bulk.BulkOperation op =
                                co.elastic.clients.elasticsearch.core.bulk.BulkOperation.of(o -> o
                                        .index(i -> i
                                                .index(INDEX)
                                                .id(String.valueOf(bookResponse.bookId()))
                                                .document(document)
                                        )
                                );
                        operations.add(op);

                    } catch (Exception e) {
                        log.error("RAG reindex: 개별 도서 인덱싱 실패 bookId={}", book.getId(), e);
                    }
                }

                // 5) 현재 페이지 bulk 인덱싱 실행
                if (!operations.isEmpty()) {
                    client.bulk(b -> b
                            .index(INDEX)
                            .operations(operations)
                    );
                }

                totalIndexed += books.size();
                log.info("RAG reindex 진행 상황: page={} ({}권 처리 누적 {}권)",
                        pageNumber, books.size(), totalIndexed);

                if (!bookPage.hasNext()) {
                    break;
                }
                pageNumber++;
            }

            log.info("RAG reindex 완료 - {} 인덱싱 종료 (총 {}권)", INDEX, totalIndexed);

        } catch (Exception exception) {
            log.error("RAG reindex 중 예외 발생", exception);
        }
    }

    /**
     * 임베딩용 텍스트 구성
     */
    private String buildEmbeddingText(BookResponse book) {
        StringBuilder builder = new StringBuilder();

        if (book.title() != null) {
            builder.append(book.title()).append(". ");
        }
        if (book.author() != null) {
            builder.append("저자: ").append(book.author()).append(". ");
        }
        if (book.publisher() != null) {
            builder.append("출판사: ").append(book.publisher()).append(". ");
        }
        if (book.content() != null) {
            builder.append("내용: ").append(book.content());
        }

        return builder.toString();
    }

    /**
     * 프론트에 보여줄 간단 요약용 문자열
     */
    private String buildSimpleSummary(BookResponse book) {
        String title = book.title() != null ? book.title() : "";
        String content = book.content() != null ? book.content() : "";

        String trimmedContent = content.length() > 220
                ? content.substring(0, 220) + "..."
                : content;

        if (title.isBlank() && trimmedContent.isBlank()) {
            return null;
        }

        return "『" + title + "』 " + trimmedContent;
    }
}
