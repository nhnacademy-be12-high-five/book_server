package com.nhnacademy.book_server.service;

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
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.net.URI;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioImageService {

    private final S3Client s3Client;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.url}") // yml에서 도메인 주입 받음
    private String minioUrl;

    @Value("${minio.default-image-url}")
    private String defaultImageUrl;

    public String uploadImageFromUrl(String imageUrl, String isbn) {
        // 1. 애초에 주소가 없으면 -> 기본 이미지 반환
        if (!StringUtils.hasText(imageUrl)) {
            return defaultImageUrl;
        }

        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0...");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();

            // 2. 접속했는데 404(없음)나 500(에러)이면 -> 기본 이미지 반환
            if (responseCode != 200) {
                log.warn("이미지 없음 (HTTP {}): {} -> 기본 이미지로 대체", responseCode, imageUrl);
                return defaultImageUrl;
            }

            try (InputStream inputStream = connection.getInputStream()) {
                byte[] imageBytes = inputStream.readAllBytes();

                // 파일명: ISBN.확장자 (중복 방지)
                String ext = imageUrl.substring(imageUrl.lastIndexOf(".") + 1);
                if (ext.length() > 4 || !ext.matches("^[a-zA-Z0-9]*$")) ext = "jpg";

                String storedFileName = isbn.trim() + "." + ext;

                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(storedFileName)
                        .contentType("image/jpeg") // 혹은 유동적으로 설정
                        .build();

                s3Client.putObject(request, RequestBody.fromBytes(imageBytes));

                return String.format("%s/%s/%s", minioUrl, bucketName, storedFileName);
            }

        } catch (Exception e) {
            // 3. 타임아웃, 연결 끊김 등 에러 발생 시 -> 기본 이미지 반환
            log.warn("이미지 업로드 실패: {} (원인: {}) -> 기본 이미지로 대체", imageUrl, e.getMessage());
            return defaultImageUrl;
        }
    }


    public String uploadImage(MultipartFile file) {
        try {
            String contentType = file.getContentType();
            if (!contentType.startsWith("image")) {
                throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
            }
            // 1. 파일 이름 중복 방지 (UUID 사용)
            String originalFilename = file.getOriginalFilename();
            String storedFileName = UUID.randomUUID() + "_" + originalFilename;

            // 2. 업로드 요청 객체 생성
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storedFileName) // 저장될 파일 이름
                    .contentType(file.getContentType())
                    .build();

            // 3. S3(MinIO)로 전송
            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // 4. 업로드된 이미지의 접근 URL 반환
            return String.format("%s/%s/%s", minioUrl, bucketName, storedFileName);

        } catch (IOException e) {
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

                // 3. (중요) MinIO나 Path-Style을 쓴다면 버킷 이름 제거 로직 필요
                // 만약 URL이 "http://localhost:9000/my-bucket/reviews/photo.jpg" 형태라면
                // path는 "/my-bucket/reviews/photo.jpg"가 됨.
                // 여기서 버킷명("/my-bucket/")을 잘라내야 함.
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
}