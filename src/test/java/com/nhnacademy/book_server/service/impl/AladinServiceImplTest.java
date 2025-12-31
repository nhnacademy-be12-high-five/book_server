package com.nhnacademy.book_server.service.impl;


import com.nhnacademy.book_server.dto.response.AladinSearchResponse;
import com.nhnacademy.book_server.entity.AladinItem;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.service.MinioImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AladinServiceImplTest {

    @InjectMocks
    private AladinServiceImpl aladinService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private MinioImageService minioImageService;

    private final String TTB_KEY = "test-ttb-key";

    @BeforeEach
    void setUp() {
        // @Value("${aladin.ttb-key}") 필드에 값 주입
        ReflectionTestUtils.setField(aladinService, "ttbKey", TTB_KEY);
    }

    @Test
    @DisplayName("도서 검색 성공 - 결과가 있을 때")
    void searchBooks_Success() {
        // given
        String query = "Spring Boot";
        String queryType = "Keyword";
        String dummyImageUrl = "http://minio/image.jpg";

        // 알라딘 API 응답 Mock 생성
        AladinItem item = new AladinItem();
        item.setTitle("Spring Boot Guide");
        item.setIsbn13("9781234567890");
        item.setPriceStandard(20000);
        item.setPriceSales(18000); // 할인율 로직 검증용
        item.setLink("http://aladin/cover.jpg");
        item.setPubDate("2023-01-01");

        AladinSearchResponse response = new AladinSearchResponse();
        response.setItem(List.of(item));

        // RestTemplate Mocking
        given(restTemplate.getForObject(anyString(), eq(AladinSearchResponse.class), eq(TTB_KEY), eq(query), eq(queryType)))
                .willReturn(response);

        // MinioService Mocking
        given(minioImageService.uploadImageFromUrl(eq(item.getLink()), eq(item.getIsbn13())))
                .willReturn(dummyImageUrl);

        // when
        List<AladinItem> result = aladinService.searchBooks(query, queryType);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Spring Boot Guide");

        // 이미지 업로드가 호출되었는지 검증
        verify(minioImageService).uploadImageFromUrl(eq("http://aladin/cover.jpg"), eq("9781234567890"));
    }

    @Test
    @DisplayName("도서 검색 실패 - API 호출 중 예외 발생")
    void searchBooks_ApiError() {
        // given
        given(restTemplate.getForObject(anyString(), eq(AladinSearchResponse.class), any(), any(), any()))
                .willThrow(new RuntimeException("Connection Refused"));

        // when & then
        assertThatThrownBy(() -> aladinService.searchBooks("query", "Type"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("알라딘 API 오류");
    }

    @Test
    @DisplayName("Entity 변환 테스트 - 정상 변환")
    void convertToBookEntity_Success() {
        // given
        AladinItem item = new AladinItem();
        item.setTitle("Test Book");
        item.setIsbn13("1234567890123");
        item.setPriceStandard(10000);
        item.setPubDate("2024-01-01");
        item.setDescription("Description");
        item.setLink("http://original.image");

        String uploadedUrl = "http://minio.url/1234567890123.jpg";
        given(minioImageService.uploadImageFromUrl(item.getLink(), item.getIsbn13()))
                .willReturn(uploadedUrl);

        // when
        Book book = aladinService.convertToBookEntity(item);

        // then
        assertThat(book).isNotNull();
        assertThat(book.getTitle()).isEqualTo("Test Book");
        assertThat(book.getIsbn13()).isEqualTo("1234567890123");
        assertThat(book.getImage()).isEqualTo(uploadedUrl); // 업로드된 URL로 교체되었는지 확인
    }

    @Test
    @DisplayName("Entity 변환 테스트 - 필수 데이터(ISBN/제목) 누락 시 null 반환")
    void convertToBookEntity_MissingData() {
        // given
        AladinItem itemNoIsbn = new AladinItem();
        itemNoIsbn.setTitle("Title Only");

        AladinItem itemNoTitle = new AladinItem();
        itemNoTitle.setIsbn13("12345");

        // when
        Book result1 = aladinService.convertToBookEntity(itemNoIsbn);
        Book result2 = aladinService.convertToBookEntity(itemNoTitle);

        // then
        assertThat(result1).isNull();
        assertThat(result2).isNull();

        // 이미지 서비스가 호출되지 않아야 함
        verify(minioImageService, times(0)).uploadImageFromUrl(any(), any());
    }

    @Test
    @DisplayName("도서 상세 조회 (Lookup) - 성공")
    void lookupBook_Success() {
        // given
        String isbn = "9781234567890";
        AladinItem item = new AladinItem();
        item.setIsbn13(isbn);
        item.setTitle("Detail Book");

        AladinSearchResponse response = new AladinSearchResponse();
        response.setItem(List.of(item));

        given(restTemplate.getForObject(anyString(), eq(AladinSearchResponse.class), eq(TTB_KEY), eq(isbn)))
                .willReturn(response);

        // when
        AladinItem result = aladinService.lookupBook(isbn);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Detail Book");
    }

    @Test
    @DisplayName("도서 상세 조회 (LookupFromApi) - ISBN 정제 및 조회")
    void lookupBookFromApi_Success() {
        // given
        String dirtyIsbn = " 978-123-456 "; // 공백 및 하이픈 포함
        String cleanIsbn = "978123456";

        AladinItem item = new AladinItem();
        item.setIsbn13(cleanIsbn);

        AladinSearchResponse response = new AladinSearchResponse();
        response.setItem(List.of(item));

        given(restTemplate.getForObject(anyString(), eq(AladinSearchResponse.class), eq(TTB_KEY), eq(cleanIsbn)))
                .willReturn(response);

        // when
        AladinItem result = aladinService.lookupBookFromApi(dirtyIsbn);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getIsbn13()).isEqualTo(cleanIsbn);
    }

    @Test
    @DisplayName("리스트 조회 (Bestseller 등) - 성공")
    void getBookList_Success() {
        // given
        String queryType = "Bestseller";
        AladinItem item = new AladinItem();
        item.setTitle("Best Seller Book");

        AladinSearchResponse response = new AladinSearchResponse();
        response.setItem(List.of(item));

        given(restTemplate.getForObject(anyString(), eq(AladinSearchResponse.class), eq(TTB_KEY), eq(queryType)))
                .willReturn(response);

        // when
        List<AladinItem> result = aladinService.getBookList(queryType);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Best Seller Book");
    }

    @Test
    @DisplayName("리스트 조회 - API 오류 시 빈 리스트 반환")
    void getBookList_Fail_ReturnEmpty() {
        // given
        given(restTemplate.getForObject(anyString(), eq(AladinSearchResponse.class), any(), any()))
                .willThrow(new RuntimeException("API Error"));

        // when
        List<AladinItem> result = aladinService.getBookList("Bestseller");

        // then
        // getBookList는 예외를 catch해서 로그를 찍고 빈 리스트를 반환함
        assertThat(result).isEmpty();
    }
}