package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioImageService {

    private final S3Client s3Client;

    @Value("${minio.book-bucket-name}")
    private String bookBucketName;

    @Value("${minio.review-bucket-name}")
    private String reviewBucketName;

    @Value("${minio.default-image-url}")
    private String defaultImageUrl;

    private static final String PROXY_BASE_URL_BOOK = "https://nhnbook.shop/hi-five-bucket";
    private static final String PROXY_BASE_URL_REVIEW = "https://nhnbook.shop/hi-five-bucket-review";
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp");

    public void clearBookImageBucket() {
        deleteAllObjectsInBucket(bookBucketName);
    }

    public void clearReviewImageBucket() {
        deleteAllObjectsInBucket(reviewBucketName);
    }

    public String uploadImageFromUrl(String imageUrl, String isbn) {
        if (!StringUtils.hasText(imageUrl)) {
            return defaultImageUrl;
        }

        try {
            validateImageUrl(imageUrl);

            // [수정 1] URL 객체 생성은 여기서 수행 (path 파싱을 위해 필요)
            URL url = URI.create(imageUrl).toURL();

            // [수정 2] 연결 생성 부분만 별도 메서드 호출 (Mocking 포인트)
            HttpURLConnection connection = getConnection(url);

            // 타임아웃 등 설정 (필요시 getConnection 내부나 여기서 설정)
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                log.warn("이미지 없음/에러 (HTTP {}): {} → 기본 이미지", responseCode, imageUrl);
                return defaultImageUrl;
            }

            // [수정 3] Content-Type을 우선 확인하고, 없으면 URL 경로에서 확장자 추출
            String contentType = connection.getContentType();
            String ext = extractExtension(contentType, url.getPath());

            byte[] imageBytes;
            try (InputStream inputStream = connection.getInputStream()) {
                imageBytes = inputStream.readAllBytes();
            }

            // 저장할 파일명 생성
            String storedFileName = isbn.trim() + "." + ext;

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bookBucketName)
                    .key(storedFileName)
                    .contentType("image/" + ext)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(imageBytes));

            return PROXY_BASE_URL_BOOK + "/" + storedFileName;

        } catch (IllegalArgumentException e) {
            log.warn("보안 위협이 감지된 URL 요청 차단: {} ({})", imageUrl, e.getMessage());
            return defaultImageUrl;
        } catch (Exception e) {
            log.warn("MinIO 업로드 실패: {} (원인: {}) → 기본 이미지", imageUrl, e.getMessage());
            return defaultImageUrl;
        }
    }
    // [Test Point] 테스트에서 오버라이딩하여 Mock Connection 반환
    protected HttpURLConnection getConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    // [Helper] 확장자 추출 로직 분리 (Content-Type 우선)
    private String extractExtension(String contentType, String path) {
        if (StringUtils.hasText(contentType) && contentType.startsWith("image/")) {
            // image/jpeg -> jpeg
            return contentType.substring(6);
        }

        // Content-Type이 없으면 기존 로직대로 URL 경로에서 파싱
        if (path.contains(".")) {
            String candidate = path.substring(path.lastIndexOf(".") + 1);
            if (candidate.matches("^[a-zA-Z0-9]{1,5}$")) {
                return candidate.toLowerCase();
            }
        }
        return "jpg"; // 기본값
    }
    public String uploadImage(MultipartFile file) {
        try{
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();

            if(contentType == null || !contentType.startsWith("image")){
                throw new IllegalArgumentException("이미지 파일만 업로드 가능");
            }

            String extension = StringUtils.getFilenameExtension(originalFilename);
            if(extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())){
                throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다." + extension);
            }

            String storedFileName = UUID.randomUUID() + "_" + originalFilename;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(reviewBucketName)
                    .key(storedFileName)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return PROXY_BASE_URL_REVIEW + "/" + storedFileName;

        }catch(IOException e){
            throw new RuntimeException("이미지 업로드 실패", e);
        }
    }

    // 이미지 삭제
    // 기존 리뷰 이미지 삭제 (다건)
    public void deleteReviewImages(List<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) return;

        for (String url : fileUrls) {
            try {
                // 리뷰용 상수를 넘겨줌
                String key = extractKeyFromUrl(url, PROXY_BASE_URL_REVIEW);

                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                        .bucket(reviewBucketName) // 리뷰 버킷
                        .key(key)
                        .build();

                s3Client.deleteObject(deleteRequest);
            } catch (Exception e) {
                log.error("리뷰 이미지 삭제 실패: {}", url, e);
            }
        }
    }

    // [추가] 책 이미지 삭제 (단건)
    public void deleteBookImage(String fileUrl) {
        if (!StringUtils.hasText(fileUrl) || fileUrl.equals(defaultImageUrl)) {
            return; // 기본 이미지는 삭제하지 않음
        }

        try {
            // 1. URL에서 Key 추출
            String key = extractKeyFromUrl(fileUrl, PROXY_BASE_URL_BOOK);

            // 2. 삭제 요청
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bookBucketName) // 책 버킷 지정
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("책 이미지 삭제 성공: {}", key);

        } catch (Exception e) {
            log.error("책 이미지 삭제 실패: {}", fileUrl, e);
        }
    }

    // 헬퍼 메서드
    // 기존 List<String> 받는 메서드 대신 단일 String 처리 로직을 분리
    private String extractKeyFromUrl(String fileUrl, String baseUrlPrefix) {
        try {
            // "https://nhnbook.shop/hi-five-bucket/" 같은 접두사 제거
            String prefix = baseUrlPrefix + "/";

            String tempKey;
            if (fileUrl.startsWith(prefix)) {
                tempKey = fileUrl.replace(prefix, "");
            } else {
                // 접두사가 안 맞으면 마지막 슬래시 뒤만 가져옴 (안전장치)
                int lastSlashIdx = fileUrl.lastIndexOf('/');
                if (lastSlashIdx != -1) {
                    tempKey = fileUrl.substring(lastSlashIdx + 1);
                } else {
                    tempKey = fileUrl;
                }
            }

            // URL 디코딩 (공백, 한글 처리)
            return URLDecoder.decode(tempKey, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("키 추출 실패: {}", fileUrl, e);
            throw new BusinessException(ErrorCode.URL_PARSING_ERROR);
        }
    }

    // 보안성 업
    private void validateImageUrl(String urlString) throws IOException {
        URL url = URI.create(urlString).toURL();

        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            throw new IllegalArgumentException("허용되지 않는 프로토콜: " + protocol);
        }

        String host = url.getHost();
        if (host.equalsIgnoreCase("localhost")
                || host.startsWith("127.")
                || host.startsWith("10.")
                || host.startsWith("192.168.")
                || host.endsWith(".internal")) {

            throw new IllegalArgumentException("내부 네트워크 접근 차단: " + host);
        }
    }

    public void deleteAllObjectsInBucket(String bucketName) {
        log.warn("⚠️ [{}] 버킷 전체 삭제 시작", bucketName);

        String continuationToken = null;

        do {
            ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .continuationToken(continuationToken)
                    .build();

            ListObjectsV2Response listRes = s3Client.listObjectsV2(listReq);

            if (listRes.contents().isEmpty()) {
                break;
            }

            List<ObjectIdentifier> objectsToDelete = listRes.contents().stream()
                    .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                    .toList();

            DeleteObjectsRequest deleteReq = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(d -> d.objects(objectsToDelete))
                    .build();

            s3Client.deleteObjects(deleteReq);

            log.info("🗑️ {}개 객체 삭제 완료", objectsToDelete.size());

            continuationToken = listRes.nextContinuationToken();

        } while (continuationToken != null);

        log.warn("✅ [{}] 버킷 전체 삭제 완료", bucketName);
    }

}