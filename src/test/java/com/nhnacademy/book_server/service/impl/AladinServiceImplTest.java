package com.nhnacademy.book_server.service.impl;

import com.nhnacademy.book_server.dto.response.AladinSearchResponse;
import com.nhnacademy.book_server.entity.AladinItem;
import com.nhnacademy.book_server.service.MinioImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AladinServiceImplTest {

    @InjectMocks
    private AladinServiceImpl aladinService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private MinioImageService minioImageService;

    @BeforeEach
    void setUp() {
        // API Key 등 프로퍼티 값 주입 (ReflectionTestUtils 사용)
        // 실제 코드의 @Value("${aladin.api.key}") 변수명에 맞춰 수정 필요
        ReflectionTestUtils.setField(aladinService, "ttbKey", "test-ttb-key"); // TTB 키가 있다면
    }

    @Test
    @DisplayName("search: 알라딘 API 호출 성공 및 리스트 반환 테스트")
    void search_Success() {
        String query = "Java";
        String queryType = "Title";

        // Mock Response 설정
        AladinSearchResponse mockResponse = new AladinSearchResponse();
        mockResponse.setTotalResults(1);

        AladinItem item = new AladinItem();
        item.setTitle("Effective Java");
        item.setAuthor("Joshua Bloch");
        item.setIsbn13("9788966263058");
        item.setPubDate("2018-11-01");
        item.setPriceStandard(36000);
        item.setPriceSales(32400);
        item.setLink("http://test.com");
        item.setDescription("Description");
        item.setCategoryId(1);
        item.setCategoryName("IT");
        item.setCategoryNamePath("IT>Java");

        mockResponse.setItem(List.of(item));

        given(restTemplate.getForObject(
                anyString(),
                eq(AladinSearchResponse.class),
                any(), any(), any()
        )).willReturn(mockResponse);

        given(minioImageService.uploadImageFromUrl(any(), any()))
                .willReturn("http://minio-server/uploaded_image.jpg");

        // When
        List<AladinItem> result = aladinService.searchBooks(query, queryType);

        // Then
        assertThat(result)
                .isNotNull()
                .hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Effective Java");

        // 호출 검증도 getForObject로 변경
        verify(restTemplate).getForObject(
                anyString(),
                eq(AladinSearchResponse.class),
                any(), any(), any()
        );
    }

    @Test
    @DisplayName("search: 검색 결과가 없을 때 빈 리스트 반환 확인")
    void search_EmptyResult() {
        // Given
        AladinSearchResponse emptyResponse = new AladinSearchResponse();
        emptyResponse.setTotalResults(0);
        emptyResponse.setItem(Collections.emptyList());

        given(restTemplate.getForObject(
                anyString(),
                eq(AladinSearchResponse.class),
                any(), any(), any()
        )).willReturn(emptyResponse);

        // When
        List<AladinItem> result = aladinService.searchBooks("UnknownBook", "Title");

        // Then
        assertThat(result)
                .isNotNull()
                .isEmpty();

        // 호출 검증
        verify(restTemplate).getForObject(
                anyString(),
                eq(AladinSearchResponse.class),
                any(), any(), any()
        );
    }

    @Test
    @DisplayName("search: 외부 API 오류(네트워크 등) 발생 시 예외 처리")
    void search_ApiError() {
        // Given
        given(restTemplate.getForObject(
                anyString(),
                eq(AladinSearchResponse.class),
                any(), any(), any()
        )).willThrow(new RestClientException("Connection refused"));

        // When & Then
        assertThatThrownBy(() -> aladinService.searchBooks("ErrorCase", "Title"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("알라딘 API 오류")
                .hasMessageContaining("Connection refused");
    }
}