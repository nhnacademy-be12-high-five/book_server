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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioImageService {

    private final S3Client s3Client;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.default-image-url}")
    private String defaultImageUrl;

    @Value("${minio.url}")
    private String minioUrl;

    private static final String PROXY_BASE_URL = "https://nhnbook.shop/hi-five-bucket";
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
                    .bucket(bucketName)
                    .key(storedFileName)
                    .contentType("image/" + ext) // 이미지 타입 지정
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(imageBytes));

            // MinIO에 올라간 파일을 프록시 URL로 리턴
            return PROXY_BASE_URL + "/" + storedFileName;

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
                    .bucket(bucketName)
                    .key(storedFileName)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return String.format("%s/%s/%s", minioUrl, bucketName, storedFileName);

        }catch(IOException e){
            throw new RuntimeException("이미지 업로드 실패", e);
        }
    }

    // 이미지 삭제
    public void deleteImages(List<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) return;

        List<String> keys = extractKeyFromUrl(fileUrls);

        List<ObjectIdentifier> toDelete = keys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();

        try {
            DeleteObjectsRequest deleteObjectsRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(toDelete).build())
                    .build();

            s3Client.deleteObjects(deleteObjectsRequest);

        } catch (S3Exception e) {
            throw new RuntimeException("이미지 일괄 삭제 실패", e);
        }
    }

    // 헬퍼 메서드
    private List<String> extractKeyFromUrl(List<String> fileUrls) {
        List<String> extractedKeys = new ArrayList<>();

        for (String fileUrl : fileUrls) {
            try {
                // 1. URI 파싱 (프로토콜, 도메인 제외하고 경로만 가져옴)
                URI uri = new URI(fileUrl);
                String path = uri.getPath();

                // 2. 맨 앞의 슬래시(/) 제거
                // S3 Key는 슬래시 없이 시작해야 함 (예: "reviews/2024/photo.jpg")
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }

                String bucketPrefix = bucketName + "/";
                if (path.startsWith(bucketPrefix)) {
                    path = path.substring(bucketPrefix.length());
                }

                // 4. 한글/특수문자 디코딩
                String decodedKey = URLDecoder.decode(path, StandardCharsets.UTF_8);
                extractedKeys.add(decodedKey);

            } catch (Exception e) {
                log.error("URL 파싱 실패: {}", fileUrl, e);
            }
        }
        return extractedKeys;
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