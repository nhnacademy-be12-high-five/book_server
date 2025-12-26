package com.nhnacademy.book_server.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode; // [수정1] 이름 변경됨
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ElasticService {

    private static final String INDEX = "high-five";
    private final ElasticsearchClient client;

    public SearchResult<BookResponse> search(String keyword, BookSortType sort, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return new SearchResult<>(List.of(), 0L);
        }

        int from = page * size;

        // 1. 필드별 가중치 설정
        String[] fields = {
                "title^" + SearchFieldType.TITLE.getWeight(),
                "author^" + SearchFieldType.AUTHOR.getWeight(),
                "tags^" + SearchFieldType.TAG.getWeight(),
                "isbn^" + SearchFieldType.ISBN.getWeight(),
                "publisher^" + SearchFieldType.PUBLISHER.getWeight(),
                "content^" + SearchFieldType.CONTENT.getWeight(),
                "reviews^" + SearchFieldType.REVIEWCONTENT.getWeight()
        };

        try {
            SearchResponse<Map> response = client.search(s -> {
                s.index(INDEX)
                        .from(from)
                        .size(size);

                // 2. 기본 검색 쿼리 (가중치 적용)
                Query multiMatch = Query.of(q -> q.multiMatch(m -> m
                        .query(keyword)
                        .fields(List.of(fields))
                        .operator(Operator.And)
                ));

                // 3. 정렬 및 필터링 로직
                if (sort == BookSortType.POPULAR || sort == null) {
                    // [인기도 정렬]
                    s.query(q -> q.functionScore(fs -> fs
                            .query(multiMatch)
                            .functions(f -> f.scriptScore(ss -> ss.script(sc -> sc
                                    .source(
                                            "_score * 10 " +
                                                    "+ Math.log1p(doc.containsKey('searchCount') ? doc['searchCount'].value : 0) * 2 " +
                                                    "+ Math.log1p(doc.containsKey('viewCount') ? doc['viewCount'].value : 0)"
                                    )
                            )))
                            .boostMode(FunctionBoostMode.Sum) // [수정1] Enum 이름 변경
                    ));

                } else if (sort == BookSortType.RATING) {
                    // [평점순 정렬] (리뷰 100개 이상만)

                    // [수정2] RangeQuery 사용법 변경 (8.15.0+): .number()로 감싸야 함
                    Query filterQuery = Query.of(q -> q.range(r -> r
                            .number(n -> n
                                    .field("reviewCount")
                                    .gte(100.0) // JsonData 없이 double 사용 가능
                            )
                    ));

                    s.query(q -> q.bool(b -> b
                            .must(multiMatch)
                            .filter(filterQuery)
                    ));

                    s.sort(so -> so.field(f -> f.field("avgRating").order(SortOrder.Desc)));

                } else {
                    // [그 외 정렬]
                    s.query(multiMatch);

                    switch (sort) {
                        case LOW_PRICE -> s.sort(so -> so.field(f -> f.field("price").order(SortOrder.Asc)));
                        case HIGH_PRICE -> s.sort(so -> so.field(f -> f.field("price").order(SortOrder.Desc)));
                        case REVIEW -> s.sort(so -> so.field(f -> f.field("reviewCount").order(SortOrder.Desc)));
                        case NEW -> s.sort(so -> so.field(f -> f.field("publishedDate").order(SortOrder.Desc)));
                    }
                }

                return s;
            }, Map.class);

            // 4. 결과 매핑
            long totalHits = response.hits().total() != null ? response.hits().total().value() : response.hits().hits().size();
            List<BookResponse> books = response.hits().hits().stream()
                    .map(Hit::source)
                    .map(this::toBookResponse)
                    .toList();

            return new SearchResult<>(books, totalHits);

        } catch (Exception e) {
            throw new RuntimeException("ES 검색 실패: " + e.getMessage(), e);
        }
    }

    private BookResponse toBookResponse(Map<String, Object> source) {
        if (source == null) return null;

        Long bookId = null;
        if (source.get("id") instanceof Number nId) {
            bookId = nId.longValue();
        } else if (source.get("bookId") instanceof Number nBookId) {
            bookId = nBookId.longValue();
        }

        String title = (String) source.get("title");
        String author = (String) source.get("author");
        String isbn = (String) source.get("isbn");

        Integer price = null;
        Object priceObj = source.get("price");
        if (priceObj instanceof Number nPrice) {
            price = nPrice.intValue();
        }

        String image = (String) source.get("image");
        List<CategoryResponse> categoryList = Collections.emptyList();
        String content = (String) source.get("content");
        String publisher = (String) source.get("publisher");

        String publishedDate = null;
        if (source.get("publishedDate") != null) {
            publishedDate = source.get("publishedDate").toString();
        }

        Double avgRating = null;
        Object avgObj = source.get("avgRating");
        if (avgObj instanceof Number nAvg) {
            avgRating = nAvg.doubleValue();
        }

        Long reviewCount = 0L;
        Object revObj = source.get("reviewCount");
        if (revObj instanceof Number nRev) {
            reviewCount = nRev.longValue();
        }

        String aiSummary = (String) source.get("aiSummary");
        List<TagResponse> tagList = Collections.emptyList();

        return new BookResponse(
                bookId, title, author, isbn, price, image, categoryList, tagList,
                content, publisher, publishedDate, avgRating, reviewCount, aiSummary, null
        );
    }

    public void saveAll(List<BookResponse> books) {
        if (books == null || books.isEmpty()) return;

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (BookResponse book : books) {
                if (book == null || book.bookId() == null) continue;
                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(INDEX)
                                .id(book.bookId().toString())
                                .document(book)
                        )
                );
            }
            BulkResponse response = client.bulk(bulkBuilder.build());
            if (response.errors()) {
                response.items().forEach(item -> {
                    if (item.error() != null) {
                        System.err.println("ES bulk 인덱싱 실패 - id=" + item.id() + " reason=" + item.error().reason());
                    }
                });
                throw new RuntimeException("ES bulk 인덱싱 중 일부 문서 실패 발생");
            }
        } catch (IOException e) {
            throw new RuntimeException("ES bulk 인덱싱 실패", e);
        }
    }
}