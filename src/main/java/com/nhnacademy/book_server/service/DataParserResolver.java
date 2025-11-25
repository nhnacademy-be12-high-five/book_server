package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.parser.DataParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataParserResolver {

    // 💡 모든 DataParser 구현체를 주입받는 필드를 추가했는지 확인
    private final List<DataParser> parsers;

    public DataParser getDataParser(String fileName) {
        // ... (fileName null 체크 및 확장자 추출 로직)
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) {
            return null;
        }
        String fileType = fileName.substring(lastDot); // 예: ".csv"

        // 💡 주입받은 파서 목록을 순회하며 일치하는 파서를 찾도록 로직을 구현했는지 확인
        for (DataParser parser : parsers) {
            if (parser.getFileType().equalsIgnoreCase(fileType)) {
                return parser;
            }
        }

        return null; // 일치하는 파서가 없으면 null 반환
    }
}