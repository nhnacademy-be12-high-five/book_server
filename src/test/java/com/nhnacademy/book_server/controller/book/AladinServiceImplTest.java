package com.nhnacademy.book_server.controller.book;

import com.nhnacademy.book_server.dto.response.AladinSearchResponse;
import com.nhnacademy.book_server.entity.AladinItem;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.service.MinioImageService;
import com.nhnacademy.book_server.service.impl.AladinServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class) // Mockito 프레임워크 사용
class AladinServiceImplTest {

    @InjectMocks
    private AladinServiceImpl aladinService; // 테스트 대상

    @Mock
    private RestTemplate restTemplate; // 가짜 RestTemplate

    @Mock
    private BookRepository bookRepository;

    @Mock
    private MinioImageService minioImageService;

    @BeforeEach
    void setup() {
        // @Value("${aladin.ttb-key}") 값을 주입하기 위해 ReflectionTestUtils 사용
        ReflectionTestUtils.setField(aladinService, "ttbKey", "test-ttb-key");
    }

    @Test
    @DisplayName("도서 검색 테스트 - 성공")
    void searchBooks_Success() {
        // given
        String query = "Spring";
        String queryType = "Title";

        // 알라딘 API가 반환할 가짜 응답 데이터 생성
        AladinItem item = new AladinItem();
        item.setTitle("Spring Boot");
        item.setIsbn13("1234567890123");
        item.setPriceStandard(20000);
        item.setPriceSales(18000);
        item.setLink("http://image.url");

        AladinSearchResponse mockResponse = new AladinSearchResponse();
        mockResponse.setItem(List.of(item));

        // RestTemplate이 호출되면 mockResponse를 반환하도록 설정
        given(restTemplate.getForObject(anyString(), eq(AladinSearchResponse.class), any(), any(), any()))
                .willReturn(mockResponse);

        // 이미지 업로드 모킹 (convertToBookEntity 내부 호출용)
        given(minioImageService.uploadImageFromUrl(anyString(), anyString()))
                .willReturn("http://minio.url/image.jpg");

        // when
        List<AladinItem> result = aladinService.searchBooks(query, queryType);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Spring Boot");
    }

    @Test
    @DisplayName("상세 조회 테스트 (lookupBook)")
    void lookupBook_Success() {
        // given
        String isbn = "9791163035105";

        AladinItem item = new AladinItem();
        item.setTitle("Test Book");
        item.setIsbn13(isbn);

        AladinSearchResponse mockResponse = new AladinSearchResponse();
        mockResponse.setItem(List.of(item));

        given(restTemplate.getForObject(anyString(), eq(AladinSearchResponse.class), any(), any()))
                .willReturn(mockResponse);

        // when
        AladinItem result = aladinService.lookupBook(isbn);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getIsbn13()).isEqualTo(isbn);
    }

    @Test
    @DisplayName("Entity 변환 테스트")
    void convertToBookEntity_Test() {
        // given
        AladinItem item = new AladinItem();
        item.setTitle("Junit Test");
        item.setIsbn13("11111");
        item.setLink("http://origin.url");
        item.setPriceStandard(10000);
        item.setPubDate("2024-01-01");

        given(minioImageService.uploadImageFromUrl(anyString(), anyString()))
                .willReturn("http://minio/uploaded.jpg");

        // when
        Book book = aladinService.convertToBookEntity(item);

        // then
        assertThat(book).isNotNull();
        assertThat(book.getTitle()).isEqualTo("Junit Test");
        assertThat(book.getImage()).isEqualTo("http://minio/uploaded.jpg");
    }
}