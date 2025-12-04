package com.nhnacademy.book_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.net.URI;

@Service
@RequiredArgsConstructor
public class MinioImageService {

    private final S3Client s3Client;

    @Value("${minio.bucket-name}")
    private String bucketName;

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
            return String.format("http://storage.java21.net:8000/%s/%s", bucketName, storedFileName);

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
                System.err.println("URL 파싱 실패: " + fileUrl);
            }
        }
        return extractedKeys;
    }
}