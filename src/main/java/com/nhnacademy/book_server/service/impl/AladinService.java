package com.nhnacademy.book_server.service.impl;

import com.nhnacademy.book_server.entity.AladinItem;
import com.nhnacademy.book_server.entity.Book;

import java.util.List;

public interface AladinService {

    List<AladinItem> searchBooks(String query, String queryType);
    Book convertToBookEntity(AladinItem item);
    AladinItem lookupBook(String isbn13);
    List<AladinItem> getBookList(String queryType);
    AladinItem convertEntityToAladinItem(Book book);
}
