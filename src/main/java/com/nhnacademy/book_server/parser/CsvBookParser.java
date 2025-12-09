package com.nhnacademy.book_server.parser;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CsvBookParser implements DataParser {

    // 괄호, 특수문자 제거 패턴
    private static final Pattern CLEAN_PATTERN = Pattern.compile(
            "\\[.*?\\]|\\(.*?\\)|<.*?>|\\{.*?\\}|[:;]|^\\s*[-=]\\s*"
    );

    // 의미 없는 문자열(숫자/특수문자만 있는 경우) 필터링용
    private static final Pattern GARBAGE_CHECK = Pattern.compile("^[0-9\\s\\p{Punct}]*$");

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
                if (data == null || data.length < 15) {
                    continue;
                }

                try {
                    ParsingDto dto = new ParsingDto();
                    dto.setIsbn(data[1]);
                    dto.setTitle(data[3]); // 제목은 원본 유지

                    // 1. 작가 정제 (여기가 핵심)
                    List<String> authorList = parseAuthors(data[4]);
                    dto.setAuthor(String.join(",", authorList));

                    // 2. 출판사 정제
                    String rawPublisher = data[5];
                    if (StringUtils.hasText(rawPublisher)) {
                        String cleanPub = cleanName(rawPublisher);
                        dto.setPublisher(cleanPub.isEmpty() ? "알 수 없음" : cleanPub);
                    } else {
                        dto.setPublisher("알 수 없음");
                    }

                    // 3. 가격 정제
                    String stringPrice = data[8];
                    if (StringUtils.hasText(stringPrice)) {
                        String cleanPrice = stringPrice.replaceAll("[^0-9]", "");
                        dto.setPrice(cleanPrice.isEmpty() ? "0" : cleanPrice);
                    } else {
                        dto.setPrice("0");
                    }

                    dto.setImageUrl(data[9]);
                    dto.setDescription(data[10]);

                    if (data.length > 14) {
                        dto.setPubDate(data[14]);
                    }

                    records.add(dto);

                } catch (Exception e) {
                    log.error("CSV 파싱 중 에러 발생 (ISBN: {}): {}", data[1], e.getMessage());
                }
            }
            return records;

        } catch (IOException e) {
            throw new RuntimeException("CSV 파일을 읽는 중 치명적 오류 발생", e);
        }
    }

    public List<String> parseAuthors(String authorField) {
        Set<String> uniqueNames = new HashSet<>();
        if (!StringUtils.hasText(authorField)) {
            return new ArrayList<>();
        }

        String[] groups = authorField.split("[,;/]");

        for (String group : groups) {
            String cleaned = cleanName(group);
            // 너무 짧거나(1자 이하) 특수문자만 남은 경우 제외
            if (StringUtils.hasText(cleaned) && cleaned.length() > 1 && !GARBAGE_CHECK.matcher(cleaned).matches()) {
                uniqueNames.add(cleaned);
            }
        }
        return new ArrayList<>(uniqueNames);
    }

    // [업그레이드된 청소기]
    private String cleanName(String input) {
        if (input == null) return "";

        // 1. 괄호 내용 삭제
        String temp = CLEAN_PATTERN.matcher(input).replaceAll(" ");

        // 2. [강력해진 필터] 한글, 영어, 한자, 베트남어 역할 표기 제거
        // 著(저), 譯/訳(역), 編(편), 絵(그림), 監修(감수), dịch(번역) 등
        temp = temp.replaceAll("지은이|글|그림|옮긴이|역자|저자|편저|엮음|감수|사진|원작|illustrator|written by|著|譯|訳|編|絵|監修|dịch", "");

        // 3. 법인격 제거
        temp = temp.replaceAll("\\(주\\)|\\(사\\)|\\(재\\)", "");

        return temp.trim();
    }
}