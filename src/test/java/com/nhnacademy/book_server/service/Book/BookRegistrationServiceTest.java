package com.nhnacademy.book_server.service.Book;

import com.nhnacademy.book_server.dto.BookInfoDto;
import com.nhnacademy.book_server.dto.KakaoBookSearchResponse;
import com.nhnacademy.book_server.dto.response.GoogleBookResponse;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.service.BookRegistrationService;
import com.nhnacademy.book_server.service.MinioImageService;
import com.nhnacademy.book_server.service.search.GeminiTextClientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookRegistrationServiceTest {

    @InjectMocks
    private BookRegistrationService bookService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private GeminiTextClientService geminiService;

    @Mock
    private MinioImageService minioImageService;

    @Test
    @DisplayName("ISBN 유효성 검사 실패")
    void getBookInfoWithAi_InvalidIsbn() {
        assertThatThrownBy(() -> bookService.getBookInfoWithAi("123")) // 짧은 ISBN
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 ISBN");
    }

    @Test
    @DisplayName("Kakao 검색 성공 시 정상 반환")
    void getBookInfoWithAi_KakaoSuccess() {
        // given
        String isbn = "9788912345678";
        String apiKey = "dummy_key";
        ReflectionTestUtils.setField(bookService, "kakaoApiKey", apiKey);

        // Kakao Mock Response 설정
        KakaoBookSearchResponse.Document doc = new KakaoBookSearchResponse.Document();
        doc.setTitle("테스트 책");
        doc.setContents("원본 설명");
        doc.setAuthors(List.of("테스트 저자"));
        doc.setDatetime("2023-01-01T00:00:00.000+09:00");
        doc.setPublisher("테스트 출판사");
        doc.setPrice(10000);

        KakaoBookSearchResponse response = new KakaoBookSearchResponse();
        response.setDocuments(List.of(doc));

        given(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(KakaoBookSearchResponse.class)))
                .willReturn(ResponseEntity.ok(response));

        // Image & Gemini Mock
        given(minioImageService.uploadImageFromUrl(anyString(), eq(isbn)))
                .willReturn("http://minio/image.jpg");
        given(geminiService.generateAnswer(anyString()))
                .willReturn("<h3>AI 추천</h3><p>이 책은...</p>");

        // when
        BookInfoDto result = bookService.getBookInfoWithAi(isbn);

        // then
        assertThat(result.getTitle()).isEqualTo("테스트 책");
        assertThat(result.getDescription()).contains("AI 추천"); // Gemini 결과 반영 확인
        assertThat(result.getImage()).isEqualTo("http://minio/image.jpg");

        verify(restTemplate).exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(KakaoBookSearchResponse.class));
    }

    @Test
    @DisplayName("Kakao 실패 시 Google 검색 시도 및 성공")
    void getBookInfoWithAi_KakaoFail_GoogleSuccess() {
        // given
        String isbn = "9788912345678";
        ReflectionTestUtils.setField(bookService, "kakaoApiKey", "dummy");

        // Kakao 결과 없음 (Exception 발생 혹은 빈 결과)
        given(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(KakaoBookSearchResponse.class)))
                .willThrow(new RuntimeException("Kakao Error"));

        // Google Mock Response 설정
        GoogleBookResponse.VolumeInfo info = new GoogleBookResponse.VolumeInfo();
        info.setTitle("구글 책");
        info.setDescription("구글 설명");
        info.setAuthors(List.of("구글 저자"));
        info.setPublishedDate("2023");

        GoogleBookResponse.Item item = new GoogleBookResponse.Item();
        item.setVolumeInfo(info);

        GoogleBookResponse googleResponse = new GoogleBookResponse();
        googleResponse.setItems(List.of(item));

        given(restTemplate.getForObject(any(URI.class), eq(GoogleBookResponse.class)))
                .willReturn(googleResponse);

        // Image & Gemini Mock
        given(minioImageService.uploadImageFromUrl(anyString(), eq(isbn))).willReturn("img");
        given(geminiService.generateAnswer(anyString())).willReturn("AI 설명");

        // when
        BookInfoDto result = bookService.getBookInfoWithAi(isbn);

        // then
        assertThat(result.getTitle()).isEqualTo("구글 책");
        verify(restTemplate).getForObject(any(URI.class), eq(GoogleBookResponse.class)); // 구글 호출 확인
    }

    @Test
    @DisplayName("Kakao와 Google 모두 실패 시 예외 발생")
    void getBookInfoWithAi_AllFail() {
        // given
        String isbn = "9788912345678";
        ReflectionTestUtils.setField(bookService, "kakaoApiKey", "dummy");

        // Kakao 실패
        given(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(KakaoBookSearchResponse.class)))
                .willReturn(ResponseEntity.ok(new KakaoBookSearchResponse())); // 빈 결과

        // Google 실패
        given(restTemplate.getForObject(any(URI.class), eq(GoogleBookResponse.class)))
                .willThrow(new RuntimeException("Google Error"));

        // when & then
        assertThatThrownBy(() -> bookService.getBookInfoWithAi(isbn))
                .isInstanceOf(BusinessException.class);
    }
}