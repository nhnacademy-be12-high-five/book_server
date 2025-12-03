//package com.nhnacademy.book_server.parser;
//
//import com.nhnacademy.book_server.service.DataParsingService;
//import com.opencsv.bean.CsvToBean;
//import com.opencsv.bean.CsvToBeanBuilder;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.stereotype.Component;
//
//import java.io.InputStreamReader;
//import java.io.Reader;
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//
//@Slf4j
////@Component
//@RequiredArgsConstructor
//public class csvParser implements CommandLineRunner {
//
//    private final DataParsingService dataParsingService;
//
//    @Override
//    public void run(String... args) throws Exception {
//        // src/main/resources/Data/book.csv 파일을 읽어옵니다.
//        ClassPathResource resource = new ClassPathResource("Data/book.csv");
//
//        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
//            CsvToBean<ParsingDto> csvToBean = new CsvToBeanBuilder<ParsingDto>(reader)
//                    .withType(ParsingDto.class)
//                    .withIgnoreLeadingWhiteSpace(true)
//                    .build();
//
//            List<ParsingDto> parsingList = csvToBean.parse();
//            log.info("CSV Parsing 완료: {} 건", parsingList.size());
//
//            // 파싱된 데이터를 서비스로 전달하여 DB에 저장
//            dataParsingService.saveAll(parsingList);
//        } catch (Exception e) {
//            log.error("CSV 파싱 중 오류 발생", e);
//        }
//    }
//}