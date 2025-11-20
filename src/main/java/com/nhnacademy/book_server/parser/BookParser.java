package com.nhnacademy.book_server.parser;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface BookParser {

    public String getFileType();

    public List parsing(File file) throws IOException;
}
