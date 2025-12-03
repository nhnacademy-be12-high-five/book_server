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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

                    if (!StringUtils.hasText(data[5])){
                        dto.setPublisher("알 수 없음");
                    }
                    else{
                        dto.setPublisher(data[5]);
                    }

                    log.info("파싱 확인 - 제목: {}, 출판사: {}", data[3], data[5]);

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
        Set<String> uniqueNames = new HashSet<>();
        if (!StringUtils.hasText(authorField)) {
            // [수정] 결과가 없으면 빈 리스트를 반환합니다.
            return new ArrayList<>(uniqueNames);
        }

        String[] groups = authorField.split("[,;]"); // 쉼표와 세미콜론으로 분리

        // [핵심] 괄호 밖의 모든 역할 정보를 정규 표현식으로 정의 (가장 강력한 클렌징)
        String rolePattern = "(저자:|편저자:|글 그림:|지은이:|연구위원:|선 \\[공\\]|원작|역자:|엮음|역\\s|\\s著|\\s譯|\\s지음|\\s그림|\\s사진|\\s역)";

        for (String group : groups) {
            String cleanedName = group.trim();

            // 1. 괄호 안의 역할 제거 및 주 저자 판별
            boolean isPrimaryAuthor = true;
            int roleStartIndex = cleanedName.lastIndexOf('(');

            if (roleStartIndex != -1) {
                String roleText = cleanedName.substring(roleStartIndex).trim();

                // 주 저자 역할이 아니면 무시 (옮긴이, 그림 제외)
                if (!(roleText.contains("지은이") || roleText.contains("저") || roleText.contains("著"))) {
                    isPrimaryAuthor = false;
                }
                cleanedName = cleanedName.substring(0, roleStartIndex).trim(); // 괄호 부분 제거
            }

            // 2. 주 저자가 아니면 (옮긴이, 그림 등) 해당 이름은 무시하고 다음으로 넘어감
            if (!isPrimaryAuthor) {
                continue;
            }

            // 3. [최종 클렌징] 괄호 밖의 모든 불필요한 접두사/접미사 제거
            cleanedName = cleanedName.replaceAll(rolePattern, "").strip();

            // 4. 최종 이름 추가 (중복 제거)
            if (StringUtils.hasText(cleanedName)) {
                uniqueNames.add(cleanedName);
            }
        }

        // [수정] Set에 담긴 고유 이름들을 리스트로 변환하여 반환
        return new ArrayList<>(uniqueNames);
    }
}