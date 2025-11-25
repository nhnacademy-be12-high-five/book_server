package com.nhnacademy.book_server.parser;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Publisher;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.tags.EditorAwareTag;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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

        // 파일 읽기
        CSVParser parser=new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .build();

        try (FileReader fileReader=new FileReader(file, StandardCharsets.UTF_8);
             CSVReader csvReader=new CSVReaderBuilder(fileReader)
                     .withSkipLines(1)
                     .withCSVParser(parser)
                     .build();
        ){
            for (String [] data:csvReader){
                if (data == null || data.length < 18){
                    continue;
                }

                ParsingDto dto=new ParsingDto();
                // book id는 auto increment
                dto.setIsbn(data[1]);
                dto.setTitle(data[3]);
                // 작가가 여러명일경우
                String authorField = data[4];
                dto.setAuthors(parseAuthors(authorField));
                dto.setPublisher(data[5]);
                String stringPrice = data[8];

                if (StringUtils.hasText(stringPrice)){
                    Double price=Double.parseDouble(stringPrice);
                    dto.setPrice(price.intValue());
                }
                else{
                    dto.setPrice(null);
                }

                dto.setImage(data[9]);
                dto.setContent(data[10]);
                dto.setPublishedDate(data[14]);

                records.add(dto);
            }

            return records;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> parseAuthors(String authorField) {

        // 작가 이름을 한번에 리스트로 받아옴
        List<String> authorNames = new ArrayList<>();
        if (authorField == null || authorField.isBlank()) {
            return authorNames;
        }

        String[] roleGroups = authorField.split("\\), ");
        for (String group:roleGroups){
            if (group.contains("지은이")){
                int roleStartIndex=group.lastIndexOf('(');
                if (roleStartIndex == -1){
                    continue;
                }

                String namesPart=group.substring(0,roleStartIndex).trim();

                String[] names = namesPart.split(",");
                for (String n:names){
                    String name=n.trim();

                    if (!name.isEmpty()){
                        authorNames.add(name);
                    }
                }
            }
        }

        return authorNames;
    }
}
