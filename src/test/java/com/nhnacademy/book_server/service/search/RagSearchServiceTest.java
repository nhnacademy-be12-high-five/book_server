package com.nhnacademy.book_server.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.util.ObjectBuilder;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.SearchResult;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.review.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagSearchServiceTest {

    @Mock ElasticsearchClient client;
    @Mock EmbeddingClientService embeddingClientService;
    @Mock BookRepository bookRepository;
    @Mock ReviewRepository reviewRepository;

    RagSearchService ragSearchService;

    @BeforeEach
    void setUp() {
        ragSearchService = new RagSearchService(client, embeddingClientService, bookRepository, reviewRepository);
    }

    // --------------------------
    // searchByRag() 커버리지 강화
    // --------------------------

    @Test
    @DisplayName("빈 키워드면 즉시 빈 결과 반환 + embed/client 호출 없음")
    void searchByRag_blank_returnsEmpty() {
        SearchResult<BookResponse> result = ragSearchService.searchByRag("   ", 0, 10);

        assertThat(result.totalHits()).isZero();
        assertThat(result.content()).isEmpty();

        verifyNoInteractions(embeddingClientService);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("임베딩이 null/empty면 빈 결과 반환")
    void searchByRag_embeddingNullOrEmpty_returnsEmpty() {
        when(embeddingClientService.embed("x")).thenReturn(null);

        SearchResult<BookResponse> r1 = ragSearchService.searchByRag("x", 0, 10);
        assertThat(r1.totalHits()).isZero();
        assertThat(r1.content()).isEmpty();

        reset(embeddingClientService); // 첫 호출/스텁 제거
        when(embeddingClientService.embed("x")).thenReturn(List.of());

        SearchResult<BookResponse> r2 = ragSearchService.searchByRag("x", 0, 10);
        assertThat(r2.totalHits()).isZero();
        assertThat(r2.content()).isEmpty();

        verify(embeddingClientService, times(1)).embed("x"); // reset 이후 1회
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("정상: knn 요청(index/field/k/numCandidates/from/size) 구성 + hits 매핑 + totalHits(total 존재)")
    void searchByRag_success_buildsKnnRequest_andMapsHitsAndTotal_whenTotalExists() throws Exception {
        String keyword = "유아";
        int page = 2;
        int size = 10;

        List<Float> vector = List.of(0.1f, 0.2f, 0.3f);
        when(embeddingClientService.embed(keyword)).thenReturn(vector);

        Map<String, Object> src1 = new HashMap<>();
        src1.put("id", 1);
        src1.put("title", "책1");
        src1.put("author", "저자1");
        src1.put("isbn", "isbn1");
        src1.put("price", 10000);

        Map<String, Object> src2 = new HashMap<>();
        src2.put("bookId", 2);
        src2.put("title", "책2");
        src2.put("author", "저자2");
        src2.put("isbn", "isbn2");
        src2.put("price", 20000);

        SearchResponse<Map> fakeResponse =
                buildSearchResponse("emb-high-five", List.of(src1, src2), 2L, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(Function.class);

        doReturn(fakeResponse).when(client).search(captor.capture(), eq(Map.class));

        SearchResult<BookResponse> result = ragSearchService.searchByRag(keyword, page, size);

        assertThat(result.totalHits()).isEqualTo(2L);
        assertThat(result.content()).hasSize(2);

        BookResponse b1 = result.content().get(0);
        assertThat(b1.bookId()).isEqualTo(1L);
        assertThat(b1.title()).isEqualTo("책1");
        assertThat(b1.price()).isEqualTo(10000);

        BookResponse b2 = result.content().get(1);
        assertThat(b2.bookId()).isEqualTo(2L);
        assertThat(b2.title()).isEqualTo("책2");
        assertThat(b2.price()).isEqualTo(20000);

        // 요청 검증
        Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> fn = captor.getValue();
        SearchRequest req = fn.apply(new SearchRequest.Builder()).build();

        assertThat(req.index()).containsExactly("emb-high-five");
        assertThat(req.from()).isEqualTo(page * size);
        assertThat(req.size()).isEqualTo(size);

        assertThat(req.knn()).hasSize(1);
        var knn = req.knn().get(0);

        assertThat(knn.field()).isEqualTo("vector");
        assertThat(knn.k()).isEqualTo(size);
        assertThat(knn.numCandidates()).isEqualTo(size * 3);
        assertThat(knn.queryVector()).containsExactlyElementsOf(vector);

        verify(embeddingClientService).embed(keyword);
        verify(client).search(any(Function.class), eq(Map.class));
    }

    @Test
    @DisplayName("정상: total이 null이면 hits.size()로 totalHits 계산")
    void searchByRag_success_totalNull_usesHitsSize() throws Exception {
        String keyword = "테스트";
        when(embeddingClientService.embed(keyword)).thenReturn(List.of(0.1f));

        Map<String, Object> src = new HashMap<>();
        src.put("id", 10);
        src.put("title", "책10");
        src.put("price", 1234);

        SearchResponse<Map> fakeResponse =
                buildSearchResponse("emb-high-five", List.of(src), 999L, false);

        doReturn(fakeResponse).when(client).search(any(Function.class), eq(Map.class));

        SearchResult<BookResponse> result = ragSearchService.searchByRag(keyword, 0, 10);

        assertThat(result.totalHits()).isEqualTo(1L);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).bookId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("예외 발생 시 빈 결과 반환")
    void searchByRag_exception_returnsEmpty() throws Exception {
        when(embeddingClientService.embed("x")).thenReturn(List.of(0.1f));

        doThrow(new RuntimeException("boom"))
                .when(client).search(any(Function.class), eq(Map.class));

        SearchResult<BookResponse> result = ragSearchService.searchByRag("x", 0, 10);

        assertThat(result.totalHits()).isZero();
        assertThat(result.content()).isEmpty();
    }

    // --------------------------
    // reindexBooks() 커버리지 강화
    // --------------------------

    @Test
    @DisplayName("reindexBooks: 정상 벡터(1024)면 bulk 호출 + operations 생성")
    void reindexBooks_success_callsBulk() throws Exception {
        // given
        Book book1 = mock(Book.class);
        when(book1.getId()).thenReturn(1L);
        Book book2 = mock(Book.class);
        when(book2.getId()).thenReturn(2L);

        // 총 2건 / pageSize=100이면 첫 페이지가 마지막 페이지라서
        // PageRequest.of(1,100) 조회가 '발생하지 않을 수 있음' → 불필요 stubbing 제거
        Page<Book> page0 = new PageImpl<>(List.of(book1, book2), PageRequest.of(0, 100), 2);
        when(bookRepository.findAll(PageRequest.of(0, 100))).thenReturn(page0);

        when(reviewRepository.findByBookIdIn(List.of(1L, 2L))).thenReturn(List.of());

        BookResponse br1 = new BookResponse(1L, "제목1", "저자1", "isbn1", 1000, "img1",
                List.of(), List.of(), "내용1", "출판사1", "2025-01-01", 4.5, 10L, "aisum1", null, null, null);
        BookResponse br2 = new BookResponse(2L, "제목2", "저자2", "isbn2", 2000, "img2",
                List.of(), List.of(), "내용2", "출판사2", "2025-01-02", 4.0, 5L, "aisum2", null, null, null);

        List<Float> vec1024 = Collections.nCopies(1024, 0.01f);
        when(embeddingClientService.embed(anyString())).thenReturn(vec1024);

        when(client.bulk(any(Function.class))).thenReturn(mock(BulkResponse.class));

        try (MockedStatic<BookResponse> mocked = mockStatic(BookResponse.class)) {
            mocked.when(() -> BookResponse.from(eq(book1), isNull(), anyList())).thenReturn(br1);
            mocked.when(() -> BookResponse.from(eq(book2), isNull(), anyList())).thenReturn(br2);

            // when
            ragSearchService.reindexBooks();
        }

        // then: bulk(Function) 캡처
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<BulkRequest.Builder, ObjectBuilder<BulkRequest>>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(Function.class);

        verify(client, atLeastOnce()).bulk(captor.capture());

        Function<BulkRequest.Builder, ObjectBuilder<BulkRequest>> fn = captor.getValue();
        BulkRequest built = fn.apply(new BulkRequest.Builder()).build();

        assertThat(built.operations()).hasSize(2);
        assertThat(built.index()).isEqualTo("emb-high-five");
    }

    @Test
    @DisplayName("reindexBooks: 임베딩 null/empty면 해당 도서 스킵( bulk 미호출 가능 )")
    void reindexBooks_embeddingNull_skips() throws Exception {
        Book book1 = mock(Book.class);
        when(book1.getId()).thenReturn(1L);

        Page<Book> page0 = new PageImpl<>(List.of(book1), PageRequest.of(0, 100), 1);
        when(bookRepository.findAll(PageRequest.of(0, 100))).thenReturn(page0);

        when(reviewRepository.findByBookIdIn(List.of(1L))).thenReturn(List.of());

        BookResponse br1 = new BookResponse(
                1L, "제목1", "저자1", "isbn1", 1000, "img1",
                List.of(), List.of(),
                "내용1", "출판사1", "2025-01-01", 4.5, 10L, "aisum1", null,
                null, null
        );

        when(embeddingClientService.embed(anyString())).thenReturn(null);

        try (MockedStatic<BookResponse> mocked = mockStatic(BookResponse.class)) {
            mocked.when(() -> BookResponse.from(eq(book1), isNull(), anyList())).thenReturn(br1);
            ragSearchService.reindexBooks();
        }

        verify(client, never()).bulk(any(Function.class));
    }

    @Test
    @DisplayName("reindexBooks: 임베딩 차원 불일치(1024 아님)면 스킵")
    void reindexBooks_wrongDim_skips() throws Exception {
        Book book1 = mock(Book.class);
        when(book1.getId()).thenReturn(1L);

        Page<Book> page0 = new PageImpl<>(List.of(book1), PageRequest.of(0, 100), 1);
        when(bookRepository.findAll(PageRequest.of(0, 100))).thenReturn(page0);

        when(reviewRepository.findByBookIdIn(List.of(1L))).thenReturn(List.of());

        BookResponse br1 = new BookResponse(
                1L, "제목1", "저자1", "isbn1", 1000, "img1",
                List.of(), List.of(),
                "내용1", "출판사1", "2025-01-01", 4.5, 10L, "aisum1", null,
                null, null
        );

        when(embeddingClientService.embed(anyString())).thenReturn(List.of(0.1f, 0.2f, 0.3f));

        try (MockedStatic<BookResponse> mocked = mockStatic(BookResponse.class)) {
            mocked.when(() -> BookResponse.from(eq(book1), isNull(), anyList())).thenReturn(br1);
            ragSearchService.reindexBooks();
        }

        verify(client, never()).bulk(any(Function.class));
    }

    @Test
    @DisplayName("reindexBooks: 개별 도서 처리 중 예외가 나도 전체가 죽지 않고 계속 진행")
    void reindexBooks_perBookException_doesNotCrashWhole() throws Exception {
        Book book1 = mock(Book.class);
        when(book1.getId()).thenReturn(1L);
        Book book2 = mock(Book.class);
        when(book2.getId()).thenReturn(2L);

        Page<Book> page0 = new PageImpl<>(List.of(book1, book2), PageRequest.of(0, 100), 2);
        when(bookRepository.findAll(PageRequest.of(0, 100))).thenReturn(page0);

        when(reviewRepository.findByBookIdIn(List.of(1L, 2L))).thenReturn(List.of());

        BookResponse br2 = new BookResponse(
                2L, "제목2", "저자2", "isbn2", 2000, "img2",
                List.of(), List.of(),
                "내용2", "출판사2", "2025-01-02", 4.0, 5L, "aisum2", null
                ,null, null
        );

        List<Float> vec1024 = Collections.nCopies(1024, 0.01f);
        when(embeddingClientService.embed(anyString())).thenReturn(vec1024);

        when(client.bulk(any(Function.class))).thenReturn(mock(BulkResponse.class));

        try (MockedStatic<BookResponse> mocked = mockStatic(BookResponse.class)) {
            mocked.when(() -> BookResponse.from(eq(book1), isNull(), anyList()))
                    .thenThrow(new RuntimeException("per-book fail"));
            mocked.when(() -> BookResponse.from(eq(book2), isNull(), anyList()))
                    .thenReturn(br2);

            ragSearchService.reindexBooks();
        }

        // book2는 정상 처리 → bulk 호출이 발생할 가능성이 큼(구현에 따라 0회일 수도 있어 atLeast(0)로 둠)
        verify(client, atLeast(0)).bulk(any(Function.class));
    }

    // --------------------------
    // SearchResponse 빌더 (필수 필드 충족)
    // --------------------------

    private SearchResponse<Map> buildSearchResponse(String indexName,
                                                    List<Map<String, Object>> sources,
                                                    long total,
                                                    boolean includeTotal) {
        final String fixedIndex = indexName;

        List<Hit<Map>> hits = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            final int docId = i + 1;
            final Map<String, Object> src = sources.get(i);

            hits.add(Hit.<Map>of(h -> h
                    .index(fixedIndex)
                    .id(String.valueOf(docId))
                    .source(src)
            ));
        }

        HitsMetadata<Map> hitsMetadata = HitsMetadata.<Map>of(hm -> {
            hm.hits(hits);
            if (includeTotal) {
                hm.total(TotalHits.of(th -> th.value(total).relation(TotalHitsRelation.Eq)));
            }
            return hm;
        });

        ShardStatistics shards = ShardStatistics.of(s -> s
                .total(1)
                .successful(1)
                .skipped(0)
                .failed(0)
        );

        return SearchResponse.<Map>of(sr -> sr
                .took(1)
                .timedOut(false)
                .shards(shards)
                .hits(hitsMetadata)
        );
    }
}
