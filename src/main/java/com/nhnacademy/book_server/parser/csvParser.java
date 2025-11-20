package com.nhnacademy.book_server.parser;

import com.nhnacademy.book_server.entity.book;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class csvParser implements BookParser {

    private static final String FILE_TYPE = ".csv";

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }

    @Override
    public List parsing(File file) throws IOException {
        List<book> books=new ArrayList<>();

        try(BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            boolean isHeader=true;

            while ((line = bufferedReader.readLine()) != null){
                if (isHeader){
                    isHeader=false;
                    continue;
                }

                String[] data = line.split(",");

                String bookId=data[0].trim();
                String isbn=data[1].trim();  // 책의 고유번호
                String title=data[2].trim();  // 책의 제목
                String summary=data[4].trim();
                String author=data[5].trim();
                String publisher=data[6].trim();
//                String price=data[].trim();
            }

        }
        catch (RuntimeException e){

        }
        return List.of();
    }
}
