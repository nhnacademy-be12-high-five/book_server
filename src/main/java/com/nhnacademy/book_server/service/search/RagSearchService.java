package com.nhnacademy.book_server.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.SearchResult;
import com.nhnacademy.book_server.service.read.BookReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService implements RagSearchable {

    private static final String INDEX = "book_embedding_index";

    private final ElasticsearchClient client;
    private final EmbeddingClientService embeddingClientService;
    private final BookReadService bookReadService;

    @Override
    public SearchResult<BookResponse> searchByRag(String keyword, int page, int size) {
        // 1. 키워드 검증
        if (keyword == null || keyword.isBlank()) {
            log.warn("RAG 검색: 빈 키워드입니다.");
            return new SearchResult<>(List.of(), 0L);
        }

        try {
            // 2. 쿼리 벡터 생성 (Gemini 임베딩)
            List<Float> queryVector = embeddingClientService.embed(keyword);

            if (queryVector == null || queryVector.isEmpty()) {
                log.warn("RAG 검색: 임베딩 결과가 비어 있습니다. keyword={}", keyword);
                return new SearchResult<>(List.of(), 0L);
            }

            int topK = size;              // 우선 페이지 size만큼
            int numCandidates = topK * 3; // 후보는 넉넉하게

            // 3. KNN 검색 실행
            SearchResponse<Map> response = client.search(
                    s -> s.index(INDEX)
                            .knn(knn -> knn
                                    .field("embedding")          // ES dense_vector 필드명
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

            // TODO: 필요 시 hit.score()를 활용한 유사도 threshold 적용 가능

            // 5. hit → BookResponse 변환
            List<BookResponse> books = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::toBookResponse)
                    .toList();

            return new SearchResult<>(books, totalHits);

        } catch (Exception exception) {
            log.error("RAG 검색 중 예외 발생 keyword={}", keyword, exception);
            // 500으로 올리지 말고, 일단 빈 결과 반환
            return new SearchResult<>(List.of(), 0L);
        }
    }

    /**
     * ES _source -> BookResponse 매핑
     * (book_embedding_index 에 저장해 둔 필드 기준)
     */
    private BookResponse toBookResponse(Map<String, Object> source) {
        if (source == null) {
            return null;
        }

        Long id = null;
        if (source.get("id") != null) {
            id = ((Number) source.get("id")).longValue();
        } else if (source.get("bookId") != null) {
            id = ((Number) source.get("bookId")).longValue();
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

        // 🔹 ES에 저장된 AI 요약(없으면 null)
        String aiSummary = (String) source.get("aiSummary");

        return new BookResponse(
                id,
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
                aiSummary
        );
    }

    /**
     * 전체 도서를 읽어서 book_embedding_index 에
     * 임베딩 + 메타데이터 + 간단 요약(aiSummary)까지 인덱싱
     */
    @Override
    @Transactional(readOnly = true)
    public void reindexBooks() {
        log.info("RAG reindex 시작 - {} 전체 재색인", INDEX);

        try {
            // 2. 전체 도서 목록 조회
            List<BookResponse> books = bookReadService.findAllBooks();
            if (books == null || books.isEmpty()) {
                log.warn("RAG reindex: 인덱싱할 도서가 없습니다.");
                return;
            }

            log.info("RAG reindex: 총 {}권 도서 임베딩 및 인덱싱 시작", books.size());

            // 3. 각 도서별로 임베딩 생성 + 인덱싱
            for (BookResponse book : books) {
                try {
                    // 3-1. 임베딩 생성에 사용할 텍스트 구성
                    String embeddingText = buildEmbeddingText(book);

                    // 3-2. 임베딩 벡터 생성
                    List<Float> embeddingVector = embeddingClientService.embed(embeddingText);

                    if (embeddingVector == null || embeddingVector.isEmpty()) {
                        log.warn("RAG reindex: 임베딩 생성 실패, 도서 건너뜀 bookId={}", book.id());
                        continue;
                    }

                    if (embeddingVector.size() != 768) { // 필요 시 설정값으로 변경 가능
                        log.warn("RAG reindex: 임베딩 차원 불일치, 도서 건너뜀 bookId={} expected=768 actual={}",
                                book.id(), embeddingVector.size());
                        continue;
                    }

                    // 3-3. ES에 저장할 문서 구성
                    Map<String, Object> document = new HashMap<>();
                    document.put("bookId", book.id());
                    document.put("title", book.title());
                    document.put("author", book.author());
                    document.put("isbn", book.isbn());
                    document.put("price", book.price());
                    document.put("image", book.image());
                    document.put("categoryId", book.categoryId());
                    document.put("content", book.content());
                    document.put("publisher", book.publisher());
                    document.put("publishedDate", book.publishedDate());
                    document.put("avgRating", book.avgRating());
                    document.put("reviewCount", book.reviewCount());
                    document.put("embedding", embeddingVector);

                    // 🔹 간단한 요약을 미리 만들어 ES에 같이 저장 (프론트에서 book.aiSummary 로 사용)
                    String aiSummary = buildSimpleSummary(book);
                    document.put("aiSummary", aiSummary);

                    // 3-4. ES 인덱스에 도큐먼트 저장
                    client.index(i -> i
                            .index(INDEX)
                            .id(String.valueOf(book.id()))
                            .document(document)
                    );

                } catch (Exception exception) {
                    log.error("RAG reindex: 개별 도서 인덱싱 실패 bookId={}", book.id(), exception);
                }
            }

            log.info("RAG reindex 완료 - {} 인덱싱 종료", INDEX);

        } catch (Exception exception) {
            log.error("RAG reindex 중 예외 발생", exception);
        }
    }

    /**
     * 임베딩용 텍스트 구성
     * (제목 + 저자 + 출판사 + 내용)
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
     * (나중에 LLM으로 대체 가능)
     */
    private String buildSimpleSummary(BookResponse book) {
        // 제목 + 내용 앞부분만 잘라서 요약 형식으로 생성
        String title = book.title() != null ? book.title() : "";
        String content = book.content() != null ? book.content() : "";

        // 너무 길면 잘라주기
        String trimmedContent = content.length() > 220
                ? content.substring(0, 220) + "..."
                : content;

        if (title.isBlank() && trimmedContent.isBlank()) {
            return null;
        }

        return "『" + title + "』 " + trimmedContent;
    }

}
