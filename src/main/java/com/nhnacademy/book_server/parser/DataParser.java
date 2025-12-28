package com.nhnacademy.book_server.parser;

import java.io.File;
import java.util.List;

public interface DataParser {

    String getFileType();

    List<ParsingDto> parsing(File file);
}
