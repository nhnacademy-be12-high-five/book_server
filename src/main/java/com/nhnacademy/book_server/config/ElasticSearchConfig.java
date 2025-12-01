package com.nhnacademy.book_server.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource; // 이거 import 필수!
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

// 인덱스 컴포넌트 생성
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticSearchConfig {

    private final ElasticsearchClient client;

    @PostConstruct
    public void createBookIndex() throws Exception {
        String index = "book_index";

        try {
            // 1) 인덱스 존재 여부 확인
            boolean exists = client.indices()
                    .exists(e -> e.index(index))
                    .value();

            if (exists) {
            System.out.println("ES: book_index 이미 존재");
            return;
        }

        // [수정된 부분] ClassPathResource 사용
        // 경로는 "src/main/resources/" 를 빼고 그 뒷부분부터 적어야 합니다.
        ClassPathResource resource = new ClassPathResource("Elastic/book_index.json");

        // 파일을 InputStream으로 바로 가져옵니다. (String 변환 불필요)
        try (InputStream jsonStream = resource.getInputStream()) {

            // 인덱스 생성
            client.indices().create(c -> c.index(index).withJson(jsonStream));

            System.out.println("ES: book_index 생성 완료");
        }

            if (exists) {
                log.info("ES: book_index 이미 존재");
                return;
            }

            // 2) Json 파일 읽기
            String mappingJson = Files.readString(
                    Paths.get("src/main/resources/Elastic/book_index.json")
            );

            // 3) Json 문자열 -> InputStream 변환
            InputStream jsonStream =
                    new ByteArrayInputStream(mappingJson.getBytes(StandardCharsets.UTF_8));

            // 4) 인덱스 생성
            client.indices().create(c -> c.index(index).withJson(jsonStream));

            log.info("ES: book_index 생성 완료");

        } catch (Exception e) {
            // 🔥 여기서 예외를 먹어버리기 때문에
            // ES 서버가 꺼져 있어도 애플리케이션은 계속 뜹니다.
            log.warn("Elasticsearch 인덱스 초기화 실패. 서버는 계속 실행합니다.", e);
        }
    }
}