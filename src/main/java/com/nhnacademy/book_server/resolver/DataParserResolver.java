package com.nhnacademy.book_server.resolver;

import com.nhnacademy.book_server.parser.DataParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 로그 추가
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j // 1. 로그 어노테이션 추가
@Component
@RequiredArgsConstructor
public class DataParserResolver {

    private final List<DataParser> parsers;

    public DataParser getDataParser(String fileName) {
        // 2. 파서 리스트가 잘 들어왔는지 확인 (최초 1회 확인용)
        if (parsers.isEmpty()) {
            log.error("CRITICAL: 등록된 파서가 하나도 없습니다!");
            return null;
        }

        if (fileName == null) {
            return null;
        }

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) {
            log.warn("확장자가 없는 파일이 감지됨: {}", fileName);
            return null;
        }

        String fileType = fileName.substring(lastDot); // .csv

        // 3. 어떤 파일을 처리 중인지 로그 찍기
        log.info("파서 찾는 중 - 파일명: {}, 확장자: {}", fileName, fileType);

        for (DataParser parser : parsers) {
            if (parser.getFileType().equalsIgnoreCase(fileType)) {
                log.info("매칭된 파서 발견: {}", parser.getClass().getSimpleName());
                return parser;
            }
        }

        // 4. 여기서 어떤 파일 때문에 실패했는지 정확히 알려줌
        log.warn("지원하지 않는 파일 형식입니다: {} (확장자: {})", fileName, fileType);
        return null;
    }
}