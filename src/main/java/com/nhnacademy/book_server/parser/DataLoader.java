package com.nhnacademy.book_server.parser;

import com.nhnacademy.book_server.resolver.DataParserResolver;
import com.nhnacademy.book_server.service.DataParsingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Slf4j
//@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final DataParserResolver parserResolver;
    private final DataParsingService parsingService;

    @Override
    public void run(String... args) throws Exception {
        log.info("========== 데이터 로딩 시작 ==========");

        // 1. resources/Data/book.csv 파일 가져오기
        // 주의: 배포 환경(JAR)에서는 getFile() 사용 시 에러가 날 수 있으나, 로컬 개발/테스트 중에는 작동합니다.
        try {
            ClassPathResource resource = new ClassPathResource("Data/book.csv");
            if (!resource.exists()) {
                log.error("파일을 찾을 수 없습니다: Data/book.csv");
                return;
            }
            File file = resource.getFile();

            // 2. 파서 선택하기 (DataParserResolver 사용)
            DataParser parser = parserResolver.getDataParser(file.getName());

            if (parser != null) {
                // 3. 파싱 실행
                List<ParsingDto> parsedData = parser.parsing(file);
                log.info("파싱된 데이터 개수: {}", parsedData.size());

                // 4. DB 저장 실행
                parsingService.saveAll(parsedData);
                log.info("========== 데이터 저장 완료 ==========");
            } else {
                log.warn("적절한 파서를 찾지 못했습니다.");
            }

        } catch (Exception e) {
            log.error("데이터 로딩 중 오류 발생", e);
        }
    }
}