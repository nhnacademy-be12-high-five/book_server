package com.nhnacademy.book_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MinioImageServiceTest {

    @InjectMocks
    private MinioImageService minioImageService;

    @Mock
    private S3Client s3Client;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(minioImageService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(minioImageService, "minioUrl", "http://localhost:9000");
        ReflectionTestUtils.setField(minioImageService, "defaultImageUrl", "default.jpg");
    }

    @Test
    @DisplayName("이미지 업로드 성공")
    void uploadImage_Success() throws IOException {
        // given
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        given(file.getOriginalFilename()).willReturn("test.jpg");
        given(file.getContentType()).willReturn("image/jpeg");
        given(file.getInputStream()).willReturn(new ByteArrayInputStream("data".getBytes()));
        given(file.getSize()).willReturn(4L);

        // when
        String result = minioImageService.uploadImage(file);

        // then
        assertThat(result).startsWith("http://localhost:9000/test-bucket/");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("이미지 업로드 실패 - 잘못된 확장자")
    void uploadImage_Fail_Extension() {
        // given
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        given(file.getOriginalFilename()).willReturn("test.exe");
        given(file.getContentType()).willReturn("image/jpeg");

        // when & then
        assertThatThrownBy(() -> minioImageService.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이미지 업로드 실패 - 잘못된 ContentType")
    void uploadImage_Fail_ContentType() {
        // given
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        given(file.getOriginalFilename()).willReturn("test.jpg");
        given(file.getContentType()).willReturn("application/pdf");

        // when & then
        assertThatThrownBy(() -> minioImageService.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이미지 삭제 성공")
    void deleteImages() {
        // given
        List<String> urls = List.of(
                "http://localhost:9000/test-bucket/file1.jpg",
                "http://localhost:9000/test-bucket/folder/file2.jpg"
        );

        // when
        minioImageService.deleteImages(urls);

        // then
        verify(s3Client).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    @DisplayName("URL 업로드 - URL 없음 (기본 이미지 반환)")
    void uploadImageFromUrl_Empty() {
        String result = minioImageService.uploadImageFromUrl(null, "isbn");
        assertThat(result).isEqualTo("default.jpg");
    }
}