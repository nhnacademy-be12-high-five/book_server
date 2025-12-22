package com.nhnacademy.book_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioImageServiceTest {

    @InjectMocks
    private MinioImageService minioImageService;

    @Mock
    private S3Client s3Client;

    private final String BOOK_BUCKET = "book-bucket";
    private final String REVIEW_BUCKET = "review-bucket";
    private final String DEFAULT_IMG_URL = "http://minio/default.png";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(minioImageService, "bookBucketName", BOOK_BUCKET);
        ReflectionTestUtils.setField(minioImageService, "reviewBucketName", REVIEW_BUCKET);
        ReflectionTestUtils.setField(minioImageService, "defaultImageUrl", DEFAULT_IMG_URL);
    }

    // --- 1. uploadImage (MultipartFile) Tests ---

    @Test
    @DisplayName("파일 업로드 성공: 정상적인 이미지 파일")
    void uploadImage_Success() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "content".getBytes()
        );

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);

        // when
        String resultUrl = minioImageService.uploadImage(file);

        // then
        assertThat(resultUrl).contains("hi-five-bucket-review");
        assertThat(resultUrl).endsWith("test.jpg"); // UUID가 붙지만 endsWith로 확인 가능 (코드상 UUID_파일명)

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo(REVIEW_BUCKET);
    }

    @Test
    @DisplayName("파일 업로드 실패: 이미지가 아님 (ContentType 불일치)")
    void uploadImage_Fail_NotImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "content".getBytes()
        );

        assertThatThrownBy(() -> minioImageService.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미지 파일만 업로드 가능");
    }

    @Test
    @DisplayName("파일 업로드 실패: 지원하지 않는 확장자")
    void uploadImage_Fail_InvalidExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.exe", "image/jpeg", "content".getBytes()
        );

        assertThatThrownBy(() -> minioImageService.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 이미지 형식입니다");
    }

    @Test
    @DisplayName("파일 업로드 실패: InputStream 읽기 오류 (IOException -> RuntimeException)")
    void uploadImage_Fail_IOException() throws IOException {
        // given
        MockMultipartFile file = spy(new MockMultipartFile(
                "file", "test.png", "image/png", "content".getBytes()
        ));
        // getInputStream 호출 시 IOException 발생 유도
        doThrow(new IOException("IO Error")).when(file).getInputStream();

        // when & then
        assertThatThrownBy(() -> minioImageService.uploadImage(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("이미지 업로드 실패");
    }

    // --- 2. uploadImageFromUrl (URL String) Tests ---

    @Test
    @DisplayName("URL 업로드 실패: 빈 URL 입력 시 기본 이미지 반환")
    void uploadImageFromUrl_EmptyUrl() {
        String result = minioImageService.uploadImageFromUrl("", "1234567890");
        assertThat(result).isEqualTo(DEFAULT_IMG_URL);
    }

    @Test
    @DisplayName("URL 업로드 실패: 잘못된 프로토콜 (ftp) -> 보안 위협으로 차단 -> 기본 이미지 반환")
    void uploadImageFromUrl_InvalidProtocol() {
        String invalidUrl = "ftp://example.com/image.jpg";
        String result = minioImageService.uploadImageFromUrl(invalidUrl, "1234567890");

        // validateImageUrl에서 예외 발생 -> catch 블록에서 defaultImageUrl 반환
        assertThat(result).isEqualTo(DEFAULT_IMG_URL);
    }

    @Test
    @DisplayName("URL 업로드 실패: 내부 IP (localhost) 접근 시도 -> 차단 -> 기본 이미지 반환")
    void uploadImageFromUrl_LocalhostBlocked() {
        String localUrl = "http://localhost:8080/image.jpg";
        String result = minioImageService.uploadImageFromUrl(localUrl, "1234567890");

        assertThat(result).isEqualTo(DEFAULT_IMG_URL);
    }

    @Test
    @DisplayName("URL 업로드 실패: 잘못된 URL 형식 (MalformedURLException) -> 기본 이미지 반환")
    void uploadImageFromUrl_MalformedUrl() {
        String badUrl = "ht tp://broken-url";
        String result = minioImageService.uploadImageFromUrl(badUrl, "1234567890");

        assertThat(result).isEqualTo(DEFAULT_IMG_URL);
    }

    // Note: uploadImageFromUrl의 "성공 케이스"는 실제 외부 네트워크 연결(HttpURLConnection)이 필요하므로,
    // 순수 단위 테스트에서는 Mocking이 매우 어렵습니다. (PowerMock 등을 쓰거나 구조 리팩토링 필요)
    // 하지만 위 테스트들로 '검증 로직'과 '예외 처리' 분기는 커버됩니다.

    // --- 3. deleteReviewImages Tests ---

    @Test
    @DisplayName("리뷰 이미지 삭제 성공: 여러 개의 URL")
    void deleteReviewImages_Success() {
        List<String> urls = List.of(
                "https://nhnbook.shop/hi-five-bucket-review/img1.jpg",
                "https://nhnbook.shop/hi-five-bucket-review/img2.jpg"
        );

        minioImageService.deleteReviewImages(urls);

        // deleteObject가 URL 개수만큼 호출되었는지 확인
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("리뷰 이미지 삭제: 리스트가 null이거나 비어있으면 무시")
    void deleteReviewImages_Empty() {
        minioImageService.deleteReviewImages(null);
        minioImageService.deleteReviewImages(List.of());

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("리뷰 이미지 삭제: URL 디코딩 및 키 추출 확인")
    void deleteReviewImages_KeyExtraction() {
        // URL 인코딩된 파일명 (한글 등)
        String encodedUrl = "https://nhnbook.shop/hi-five-bucket-review/%ED%85%8C%EC%8A%A4%ED%8A%B8.jpg"; // "테스트.jpg"

        minioImageService.deleteReviewImages(List.of(encodedUrl));

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());

        assertThat(captor.getValue().key()).isEqualTo("테스트.jpg");
    }

    @Test
    @DisplayName("리뷰 이미지 삭제: S3 예외 발생 시 로그 남기고 중단되지 않음")
    void deleteReviewImages_ExceptionSafe() {
        List<String> urls = List.of("http://url1.com/a.jpg", "http://url2.com/b.jpg");

        // 첫 번째 삭제 시 예외 발생
        doThrow(S3Exception.builder().message("S3 Error").build())
                .when(s3Client).deleteObject(argThat((DeleteObjectRequest r) -> r.key().contains("a.jpg")));

        // 실행 (예외가 던져지지 않아야 함)
        minioImageService.deleteReviewImages(urls);

        // 두 번째 삭제도 시도했는지 확인 (호출 횟수 2회)
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    // --- 4. deleteBookImage Tests ---

    @Test
    @DisplayName("책 이미지 삭제 성공")
    void deleteBookImage_Success() {
        String url = "https://nhnbook.shop/hi-five-bucket/book123.jpg";

        minioImageService.deleteBookImage(url);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());

        assertThat(captor.getValue().bucket()).isEqualTo(BOOK_BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("book123.jpg");
    }

    @Test
    @DisplayName("책 이미지 삭제: 기본 이미지는 삭제하지 않음")
    void deleteBookImage_SkipDefault() {
        minioImageService.deleteBookImage(DEFAULT_IMG_URL);
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("책 이미지 삭제: S3 예외 발생 시 안전하게 처리")
    void deleteBookImage_Exception() {
        String url = "http://valid-url.com/img.jpg";
        doThrow(S3Exception.builder().message("Fail").build()).when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        // 예외가 밖으로 던져지지 않는지 확인
        minioImageService.deleteBookImage(url);

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}