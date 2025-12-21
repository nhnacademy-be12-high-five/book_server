package com.nhnacademy.book_server.service;

import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model .*;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
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

    public String uploadImageFromUrl(String imageUrl, String isbn) {
        if (!StringUtils.hasText(imageUrl)) {
            return defaultImageUrl;
        }

        try {
            validateImageUrl(imageUrl);
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                log.warn("이미지 없음/에러 (HTTP {}): {} → 기본 이미지", responseCode, imageUrl);
                return defaultImageUrl;
            }

            byte[] imageBytes;
            try (InputStream inputStream = connection.getInputStream()) {
                imageBytes = inputStream.readAllBytes();
            }

            // 확장자 판단
            String ext = "jpg";
            String path = url.getPath();
            if (path.contains(".")) {
                String candidate = path.substring(path.lastIndexOf(".") + 1);
                if (candidate.matches("^[a-zA-Z0-9]{1,5}$")) {
                    ext = candidate.toLowerCase();
                }
            }

            // 저장할 파일명 생성
            String storedFileName = isbn.trim() + "." + ext;

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bookBucketName)
                    .key(storedFileName)
                    .contentType("image/" + ext) // 이미지 타입 지정
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(imageBytes));

            // MinIO에 올라간 파일을 프록시 URL로 리턴
            return PROXY_BASE_URL_BOOK + "/" + storedFileName;

        }catch (IllegalArgumentException e) {
            log.warn("보안 위협이 감지된 URL 요청 차단: {} ({})", imageUrl, e.getMessage());
            return defaultImageUrl;
        }catch (Exception e) {
            log.warn("MinIO 업로드 실패: {} (원인: {}) → 기본 이미지", imageUrl, e.getMessage());
            return defaultImageUrl;
        }
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
            // 필요 시 예외를 던져서 상위 서비스가 알게 함
            // throw new RuntimeException("책 이미지 삭제 실패", e);
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
            throw new RuntimeException("URL 파싱 오류", e);
        }
    }

    // 보안성 업
    private void validateImageUrl(String urlString) throws IOException {
        URL url = new URL(urlString);

        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            throw new IllegalArgumentException("허용되지 않는 프로토콜입니다: " + protocol);
        }

        InetAddress address = InetAddress.getByName(url.getHost());

        if (address.isLoopbackAddress() ||
                address.isSiteLocalAddress() ||
                address.isLinkLocalAddress() ||
                address.isAnyLocalAddress()) {

            throw new IllegalArgumentException("내부 네트워크(Private IP) 접근이 차단되었습니다: " + url.getHost());
        }
    }
}