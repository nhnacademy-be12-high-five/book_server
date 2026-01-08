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
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
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

    private final String bookBucket = "book-bucket";
    private final String reviewBucket = "review-bucket";
    private final String defaultImgUrl = "http://minio/default.png";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(minioImageService, "bookBucketName", bookBucket);
        ReflectionTestUtils.setField(minioImageService, "reviewBucketName", reviewBucket);
        ReflectionTestUtils.setField(minioImageService, "defaultImageUrl", defaultImgUrl);
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
        assertThat(resultUrl)
                .contains("hi-five-bucket-review")
                .endsWith("test.jpg"); // UUID가 붙지만 endsWith로 확인 가능 (코드상 UUID_파일명)

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo(reviewBucket);
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
        assertThat(result).isEqualTo(defaultImgUrl);
    }

    @Test
    @DisplayName("URL 업로드 실패: 잘못된 프로토콜 (ftp) -> 보안 위협으로 차단 -> 기본 이미지 반환")
    void uploadImageFromUrl_InvalidProtocol() {
        String invalidUrl = "ftp://example.com/image.jpg";
        String result = minioImageService.uploadImageFromUrl(invalidUrl, "1234567890");

        // validateImageUrl에서 예외 발생 -> catch 블록에서 defaultImageUrl 반환
        assertThat(result).isEqualTo(defaultImgUrl);
    }

    @Test
    @DisplayName("URL 업로드 실패: 내부 IP (localhost) 접근 시도 -> 차단 -> 기본 이미지 반환")
    void uploadImageFromUrl_LocalhostBlocked() {
        String localUrl = "http://localhost:8080/image.jpg";
        String result = minioImageService.uploadImageFromUrl(localUrl, "1234567890");

        assertThat(result).isEqualTo(defaultImgUrl);
    }

    @Test
    @DisplayName("URL 업로드 실패: 잘못된 URL 형식 (MalformedURLException) -> 기본 이미지 반환")
    void uploadImageFromUrl_MalformedUrl() {
        String badUrl = "ht tp://broken-url";
        String result = minioImageService.uploadImageFromUrl(badUrl, "1234567890");

        assertThat(result).isEqualTo(defaultImgUrl);
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

        assertThat(captor.getValue().bucket()).isEqualTo(bookBucket);
        assertThat(captor.getValue().key()).isEqualTo("book123.jpg");
    }

    @Test
    @DisplayName("책 이미지 삭제: 기본 이미지는 삭제하지 않음")
    void deleteBookImage_SkipDefault() {
        minioImageService.deleteBookImage(defaultImgUrl);
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

    // --- 5. Bucket Clear & Delete All Objects Tests ---

    @Test
    @DisplayName("버킷 비우기: 객체가 하나도 없을 때 (즉시 종료)")
    void clearBookImageBucket_Empty() {
        // given
        // 내용물이 없는 응답 설정
        ListObjectsV2Response emptyResponse = ListObjectsV2Response.builder()
                .contents(List.of())
                .isTruncated(false)
                .nextContinuationToken(null)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(emptyResponse);

        // when
        minioImageService.clearBookImageBucket();

        // then
        // 목록 조회는 했으나, 삭제 요청은 하지 않아야 함
        verify(s3Client, times(1)).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    @DisplayName("버킷 비우기: 객체가 존재할 때 삭제 요청 전송")
    void clearReviewImageBucket_Success() {
        // given
        S3Object obj1 = S3Object.builder().key("img1.jpg").build();
        S3Object obj2 = S3Object.builder().key("img2.jpg").build();

        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(obj1, obj2)
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(response);

        // when
        minioImageService.clearReviewImageBucket();

        // then
        // 1. 목록 조회 호출 확인 (리뷰 버킷)
        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(listCaptor.capture());
        assertThat(listCaptor.getValue().bucket()).isEqualTo(reviewBucket);

        // 2. 삭제 요청 호출 확인
        ArgumentCaptor<DeleteObjectsRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(deleteCaptor.capture());

        // 삭제하려는 객체 목록이 맞는지 확인
        assertThat(deleteCaptor.getValue().bucket()).isEqualTo(reviewBucket);
        assertThat(deleteCaptor.getValue().delete().objects()).hasSize(2);
    }

    @Test
    @DisplayName("버킷 비우기: 페이지네이션 (객체가 많아서 두 번에 나눠 삭제)")
    void deleteAllObjectsInBucket_Pagination() {
        // given
        // 첫 번째 페이지: 객체 있음, 다음 토큰 존재
        S3Object obj1 = S3Object.builder().key("page1_obj.jpg").build();
        ListObjectsV2Response firstResponse = ListObjectsV2Response.builder()
                .contents(obj1)
                .isTruncated(true)
                .nextContinuationToken("token_for_page_2")
                .build();

        // 두 번째 페이지: 객체 있음, 더 이상 토큰 없음
        S3Object obj2 = S3Object.builder().key("page2_obj.jpg").build();
        ListObjectsV2Response secondResponse = ListObjectsV2Response.builder()
                .contents(obj2)
                .isTruncated(false)
                .nextContinuationToken(null)
                .build();

        // 순서대로 리턴하도록 설정
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(firstResponse)
                .thenReturn(secondResponse);

        // when
        minioImageService.deleteAllObjectsInBucket(bookBucket);

        // then
        // listObjectsV2가 총 2번 호출되어야 함
        verify(s3Client, times(2)).listObjectsV2(any(ListObjectsV2Request.class));

        // deleteObjects도 총 2번 호출되어야 함 (페이지마다 삭제)
        verify(s3Client, times(2)).deleteObjects(any(DeleteObjectsRequest.class));
    }
    @Test
    @DisplayName("URL 업로드 성공: 정상 흐름 (Content-Type으로 확장자 인식)")
    void uploadImageFromUrl_Success() throws IOException {
        // given
        String validUrl = "https://example.com/image"; // 확장자가 없는 URL
        String isbn = "97911";
        byte[] mockImageBytes = new byte[]{1, 2, 3};

        // Spy 객체 생성
        MinioImageService spyService = spy(minioImageService);

        // Mock Connection 설정
        HttpURLConnection mockConnection = mock(HttpURLConnection.class);
        when(mockConnection.getResponseCode()).thenReturn(200);
        when(mockConnection.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(mockImageBytes));

        // [중요] Content-Type 헤더가 "image/png"라고 가정
        when(mockConnection.getContentType()).thenReturn("image/png");

        // getConnection 호출 시 Mock 반환 (URL 객체는 무엇이든 상관없음)
        doReturn(mockConnection).when(spyService).getConnection(any(URL.class));

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);

        // when
        String result = spyService.uploadImageFromUrl(validUrl, isbn);

        // then
        // 1. Content-Type("image/png")을 통해 확장자가 .png로 잘 붙었는지 확인
        assertThat(result).endsWith(".png");

        // 2. S3 업로드 요청 검증
        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));

        assertThat(putCaptor.getValue().key()).isEqualTo(isbn + ".png");
        assertThat(putCaptor.getValue().contentType()).isEqualTo("image/png");
    }
}