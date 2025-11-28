package com.nhnacademy.book_server.parser;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 로깅 추가 권장
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j // 로그를 찍어 확인하기 위해 추가
@Component
@RequiredArgsConstructor
public class CsvBookParser implements DataParser {

    @Override
    public String getFileType() {
        return ".csv";
    }

    @Override
    public List<ParsingDto> parsing(File file) {
        List<ParsingDto> records = new ArrayList<>();

        CSVParser parser = new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .build();

        try (FileReader fileReader = new FileReader(file, StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReaderBuilder(fileReader)
                     .withSkipLines(1)
                     .withCSVParser(parser)
                     .build()) {

            for (String[] data : csvReader) {
                // 수정 1: 실제로 사용하는 마지막 인덱스는 14이므로, 15개 이상이면 통과시킴 (유연성 확보)
                if (data == null || data.length < 15) {
                    continue;
                }

                try {
                    ParsingDto dto = new ParsingDto();
                    dto.setIsbn(data[1]);
                    dto.setTitle(data[3]);
                    List<String> authorList = parseAuthors(data[4]);
                    dto.setAuthor(String.join(",", authorList));
                    dto.setPublisher(data[5]);

                    // 수정 2: 가격 파싱 안전하게 처리 (콤마 제거)
                    String stringPrice = data[8];
                    if (StringUtils.hasText(stringPrice)) {
                        // "15,000" -> "15000" 변환 후 파싱
                        String cleanPrice = stringPrice.replaceAll("[^0-9]", "");
                        dto.setPrice(cleanPrice);
                    } else {
                        dto.setPrice("0"); // null 대신 기본값 0 추천 (선택사항)
                    }

                    dto.setImageUrl(data[9]);
                    dto.setDescription(data[10]);

                    // 날짜 데이터가 비어있을 경우 대비
                    if(data.length > 14) {
                        dto.setPubDate(data[14]);
                    }

                    records.add(dto);

                } catch (Exception e) {
                    // 수정 3: 특정 행에서 에러가 나도 로그만 찍고 다음 행으로 넘어감 (전체 중단 방지)
                    log.error("CSV 파싱 중 에러 발생 (ISBN: {}): {}", data[1], e.getMessage());
                }
            }

            return records;

        } catch (IOException e) {
            throw new RuntimeException("CSV 파일을 읽는 중 치명적 오류 발생", e);
        }
    }

    public List<String> parseAuthors(String authorField) {
        List<String> authorNames = new ArrayList<>();
        if (!StringUtils.hasText(authorField)) {
            return authorNames;
        }

        // 알라딘/교보문고 등 데이터 포맷: "홍길동 (지은이), 김철수 (옮긴이)"
        // 수정 4: 단순히 쉼표로 먼저 분리 후 처리 (Regex 의존도 낮춤)
        String[] groups = authorField.split(",");

        for (String group : groups) {
            group = group.trim();

            // "(지은이)" 같은 역할 표기가 있는 경우
            int roleStartIndex = group.lastIndexOf('(');

            if (roleStartIndex != -1) {
                // 괄호 앞부분이 이름
                String name = group.substring(0, roleStartIndex).trim();
                // 역할 확인 (지은이만 추출할 것인지, 아니면 그냥 이름은 다 넣을 것인지 결정)
                // 만약 '지은이'만 필요하다면 아래 조건 유지, 모든 저자가 필요하면 조건 제거
                if (group.contains("지은이") && StringUtils.hasText(name)) {
                    authorNames.add(name);
                }
            } else {
                // 괄호가 없는 경우 (이름만 있는 경우) -> 그냥 이름으로 추가
                if (StringUtils.hasText(group)) {
                    authorNames.add(group);
                }
            }
        }
        return authorNames;
    }
}