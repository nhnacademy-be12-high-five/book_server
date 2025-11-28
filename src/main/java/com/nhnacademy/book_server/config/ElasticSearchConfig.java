package com.nhnacademy.book_server.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource; // 이거 import 필수!
import org.springframework.stereotype.Component;

import java.io.InputStream;

// 인덱스 컴포넌트 생성
@Component
@RequiredArgsConstructor
public class ElasticSearchConfig {

    private final ElasticsearchClient client;

    @PostConstruct
    public void createBookIndex() throws Exception {
        String index = "book_index";

        boolean exists = client.indices().exists(e -> e.index(index)).value();

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
    }
}