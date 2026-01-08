package com.nhnacademy.book_server.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.CategoryResponse;
import com.nhnacademy.book_server.dto.SearchResult;
import com.nhnacademy.book_server.dto.response.TagResponse;
import com.nhnacademy.book_server.entity.SearchFieldType;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticService {

    private static final String INDEX = "high-five";

    private static final String FIELD_REVIEW_COUNT = "reviewCount";
    private static final String FIELD_PRICE = "price";
    private static final String FIELD_PUBLISHED_DATE = "publishedDate";
    private static final String FIELD_AVG_RATING = "avgRating";

    private final ElasticsearchClient client;

    public SearchResult<BookResponse> search(String keyword, BookSortType sort, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return new SearchResult<>(Collections.emptyList(), 0L);
        }

        int from = page * size;

        try {
            SearchResponse<Map> response = client.search(s -> {
                s.index(INDEX)
                        .from(from)
                        .size(size)
                        // [핵심 1] 인기순/신간순 개수 불일치 해결
                        // ES가 문서를 10,000개까지만 세지 않고 끝까지 세도록 강제합니다.
                        .trackTotalHits(t -> t.enabled(true));

                // 1. 기본 검색 쿼리 생성 ("지리산" 정확도 문제 해결 로직 포함)
                Query baseQuery = buildBaseQuery(keyword);

                // 2. 정렬 로직 적용 (baseQuery를 감싸거나 정렬 추가)
                applySortLogic(s, sort, baseQuery);

                return s;
            }, Map.class);

            // 3. 결과 변환
            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;
            List<BookResponse> books = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::mapToBookResponse)
                    .toList();

            return new SearchResult<>(books, totalHits);

        } catch (IOException e) {
            log.error("Elasticsearch 검색 오류: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVER_ERROR);
        }
    }

    // [핵심 2] "지리산" 검색 시 정확도 높은 책을 위로 올리는 로직
    private Query buildBaseQuery(String keyword) {
        // 기존 필드 가중치
        List<String> searchFields = List.of(
                "title^" + SearchFieldType.TITLE.getWeight(),
                "author^" + SearchFieldType.AUTHOR.getWeight(),
                "isbn^" + SearchFieldType.ISBN.getWeight(),
                "publisher^" + SearchFieldType.PUBLISHER.getWeight(),
                "content^" + SearchFieldType.CONTENT.getWeight(),
                "aiSummary^45",
                "categories.categoryName^" + SearchFieldType.TAG.getWeight()
        );

        // 기본 MultiMatch 쿼리
        Query multiMatch = Query.of(q -> q.multiMatch(m -> m
                .query(keyword)
                .fields(searchFields)
                .operator(Operator.And)
        ));

        // Bool 쿼리로 감싸서 "정확히 일치하면 점수 뻥튀기(Boost)" 적용
        return Query.of(q -> q.bool(b -> b
                .must(multiMatch) // 일단 검색어는 포함되어야 함
                .should(s -> s.match(m -> m
                        .field("title.enum") // 매핑에 있는 keyword 타입 필드 사용
                        .query(keyword)
                        .boost(2000.0f)      // [Kick] 제목이 정확히 일치하면 점수 +2000점
                ))
                .should(s -> s.matchPhrase(mp -> mp
                        .field("title")
                        .query(keyword)
                        .boost(1000.0f)      // [Kick] 제목에 단어가 순서대로 붙어있으면 점수 +1000점
                ))
        ));
    }

    private void applySortLogic(co.elastic.clients.elasticsearch.core.SearchRequest.Builder s,
                                BookSortType sort,
                                Query baseQuery) {

        // POPULAR (인기도)
        if (sort == null || sort == BookSortType.POPULAR) {
            s.query(q -> q.functionScore(fs -> fs
                    .query(baseQuery)
                    .functions(f -> f.scriptScore(ss -> ss.script(sc -> sc
                            .lang("painless")
                            .source(
                                    "double searchScore = (doc['searchCount'].size() > 0) ? Math.log1p(doc['searchCount'].value) : 0;" +
                                            "double viewScore = (doc['viewCount'].size() > 0) ? Math.log1p(doc['viewCount'].value) : 0;" +

                                            "return (_score * 10) + (searchScore * 2) + viewScore;"
                            )
                    )))
                    .boostMode(FunctionBoostMode.Replace)
            ));
            s.sort(so -> so.score(sc -> sc.order(SortOrder.Desc)));
        }
        // RATING (평점순 - 리뷰 100개 이상만) -> *이것만 결과 개수가 적게 나옵니다 (정상)*
        else if (sort == BookSortType.RATING) {
            Query reviewFilter = Query.of(q -> q.range(r -> r
                    .number(n -> n.field(FIELD_REVIEW_COUNT).gte(100.0))
            ));

            s.query(q -> q.bool(b -> b
                    .must(baseQuery)
                    .filter(reviewFilter)
            ));
            s.sort(so -> so.field(f -> f.field(FIELD_AVG_RATING).order(SortOrder.Desc)));
        }
        // 기타 정렬 (신간, 가격, 리뷰순)
        else {
            s.query(baseQuery); // 인기순과 동일한 baseQuery 사용 -> 결과 개수 동일 보장

            switch (sort) {
                case LOW_PRICE -> s.sort(so -> so.field(f -> f.field(FIELD_PRICE).order(SortOrder.Asc)));
                case HIGH_PRICE -> s.sort(so -> so.field(f -> f.field(FIELD_PRICE).order(SortOrder.Desc)));
                case REVIEW -> s.sort(so -> so.field(f -> f.field(FIELD_REVIEW_COUNT).order(SortOrder.Desc)));
                case NEW -> s.sort(so -> so.field(f -> f.field(FIELD_PUBLISHED_DATE).order(SortOrder.Desc)));
                default -> log.warn("Unsupported sort type for ElasticSearch: {}", sort);
            }
        }
    }

    // ... (mapToBookResponse, saveAll 등 기존 코드 유지) ...
    // mapToBookResponse, saveAll, increaseReviewCount 등은 보내주신 코드 그대로 사용하시면 됩니다.
    @SuppressWarnings("unchecked")
    private BookResponse mapToBookResponse(Map<String, Object> source) {
        Long bookId = parseLong(source.getOrDefault("bookId", source.get("id")));
        String title = (String) source.get("title");
        String author = (String) source.get("author");
        String isbn = (String) source.getOrDefault("isbn13", source.get("isbn"));
        Integer price = parseInt(source.get(FIELD_PRICE));
        String image = (String) source.getOrDefault("imageUrl", source.get("image"));
        String content = (String) source.get("content");
        String publisher = (String) source.get("publisher");
        String publishedDate = source.get(FIELD_PUBLISHED_DATE) != null ? source.get(FIELD_PUBLISHED_DATE).toString() : null;
        Double avgRating = parseDouble(source.get(FIELD_AVG_RATING));
        Long reviewCount = parseLong(source.get(FIELD_REVIEW_COUNT));
        String aiSummary = (String) source.get("aiSummary");

        List<CategoryResponse> categoryList = new ArrayList<>();
        Object categoriesObj = source.get("categories");
        if (categoriesObj instanceof List<?>) {
            List<Map<String, Object>> catMaps = (List<Map<String, Object>>) categoriesObj;
            for (Map<String, Object> cm : catMaps) {
                Long cId = parseLong(cm.get("categoryId"));
                String cName = (String) cm.get("categoryName");
                categoryList.add(new CategoryResponse(parseInt(cId), cName));
            }
        }
        List<TagResponse> tagList = Collections.emptyList();

        return new BookResponse(
                bookId, title, author, isbn, price, image,
                categoryList, tagList,
                content, publisher, publishedDate, avgRating, reviewCount, aiSummary, null
                ,null, null
        );
    }

    private Long parseLong(Object obj) {
        if (obj instanceof Number number) return number.longValue();
        if (obj instanceof String str) try { return Long.parseLong(str); } catch (NumberFormatException e) {log.debug("Failed to parse Long from string: {}", str);}
        return 0L;
    }

    private Integer parseInt(Object obj) {
        if (obj instanceof Number number) return number.intValue();
        if (obj instanceof String str) try { return Integer.parseInt(str); } catch (NumberFormatException e) {log.debug("Failed to parse Integer from string: {}", str);}
        return 0;
    }

    private Double parseDouble(Object obj) {
        if (obj instanceof Number number) return number.doubleValue();
        if (obj instanceof String str) try { return Double.parseDouble(str); } catch (NumberFormatException e) {log.debug("Failed to parse Double from string: {}", str);}
        return 0.0;
    }

    // ... saveAll, reviewCount 메서드 유지 ...
    public void saveAll(List<BookResponse> books) {
        if (books == null || books.isEmpty()) return;
        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (BookResponse book : books) {
                if (book == null || book.bookId() == null) continue;
                bulkBuilder.operations(op -> op.index(idx -> idx.index(INDEX).id(book.bookId().toString()).document(book)));
            }
            BulkResponse response = client.bulk(bulkBuilder.build());
            if (response.errors()) {
                log.error("Elasticsearch Bulk Indexing failed with errors.");
                throw new BusinessException(ErrorCode.EXTERNAL_SERVER_ERROR);
            }
        } catch (IOException e) {
            log.error("Elasticsearch Bulk Indexing IO Error: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVER_ERROR);
        }
    }

    public void increaseReviewCount(Long bookId) {
        updateReviewCount(bookId,
                "if (ctx._source.reviewCount == null) { ctx._source.reviewCount = 1; } else { ctx._source.reviewCount += 1; }");
    }

    public void decreaseReviewCount(Long bookId) {
        updateReviewCount(bookId,
                "if (ctx._source.reviewCount == null) { ctx._source.reviewCount = 0; } else { ctx._source.reviewCount = Math.max(0, ctx._source.reviewCount - 1); }");
    }

    private void updateReviewCount(Long bookId, String scriptSource) {
        try {
            client.update(u -> u.index(INDEX).id(bookId.toString())
                    .script(sc -> sc.lang("painless").source(scriptSource)), Void.class);
        } catch (Exception e) {
            log.error("Review count update failed", e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVER_ERROR);
        }
    }
}