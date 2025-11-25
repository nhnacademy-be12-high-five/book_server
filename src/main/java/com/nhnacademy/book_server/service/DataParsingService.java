package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.Author;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookAuthor;
import com.nhnacademy.book_server.entity.Publisher;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.repository.AuthorRepository;
import com.nhnacademy.book_server.repository.BookAuthorRepository;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.PublisherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DataParsingService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;
    private final BookAuthorRepository bookAuthorRepository;

    public void saveAll(List<ParsingDto> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        Set<String> allPublisherNames = new HashSet<>();
        Set<String> allAuthorNames = new HashSet<>();
        Set<String> allIsbns = new HashSet<>();

        for (ParsingDto dto : records) {
            if (StringUtils.hasText(dto.getPublisher())) {
                allPublisherNames.add(dto.getPublisher().trim());
            }
            // 작가 이름이 "홍길동, 김철수" 처럼 콤마로 구분되어 있을 수 있으므로 분리해서 수집
            if (StringUtils.hasText(dto.getAuthor())) {
                String[] splitAuthors = dto.getAuthor().split(",");
                for (String authorName : splitAuthors) {
                    if (StringUtils.hasText(authorName)) {
                        allAuthorNames.add(authorName.trim());
                    }
                }
            }
            if (StringUtils.hasText(dto.getIsbn())) {
                allIsbns.add(dto.getIsbn().trim());
            }
        }

        // 2. 출판사 처리 (Map<이름, 객체>)
        Map<String, Publisher> publisherMap = resolvePublishers(allPublisherNames);

        // 3. 작가 처리 (Map<이름, 객체>)
        Map<String, Author> authorMap = resolveAuthors(allAuthorNames);

        // 4. 이미 존재하는 ISBN 확인 (중복 저장 방지)
        List<Book> existingBooks = bookRepository.findAllByIsbnIn(allIsbns);
        Set<String> existingIsbnSet = existingBooks.stream()
                .map(Book::getIsbn)
                .collect(Collectors.toSet());

        // 5. Book 객체 생성
        List<Book> newBooks = new ArrayList<>();
        // 나중에 BookAuthor 연결을 위해 Book과 DTO의 인덱스를 맞추거나 Map을 써야 함.
        // 여기서는 저장 후 처리를 위해 Pair처럼 DTO를 임시 보관할 리스트를 만듭니다.
        List<ParsingDto> booksToSaveDtos = new ArrayList<>();

        for (ParsingDto dto : records) {
            String isbn = dto.getIsbn() != null ? dto.getIsbn().trim() : "";

            // ISBN이 없거나 이미 DB에 있으면 스킵
            if (isbn.isEmpty() || existingIsbnSet.contains(isbn)) {
                continue;
            }

            // 중복된 ISBN이 CSV 내에 여러 개 있을 경우 방지
            existingIsbnSet.add(isbn);

            Publisher publisher = publisherMap.get(dto.getPublisher().trim());

            Book book = Book.builder()
                    .isbn(isbn)
                    .title(dto.getTitle())
                    .publisher(publisher)
                    .price(parsePrice(dto.getPrice()))
                    .content(dto.getDescription()) // Entity 필드명: content
                    .image(dto.getImageUrl())      // Entity 필드명: image
                    .publishedDate(parseDate(dto.getPubDate()).toString()) // Entity 타입이 LocalDate인 경우
                    .build();

            newBooks.add(book);
            booksToSaveDtos.add(dto);
        }

        // 6. Book 일괄 저장
        if (!newBooks.isEmpty()) {
            List<Book> savedBooks = bookRepository.saveAll(newBooks);
            log.info("새로운 도서 {}권 저장 완료", savedBooks.size());

            // 7. BookAuthor 연결 (책-작가 관계)
            List<BookAuthor> bookAuthors = new ArrayList<>();

            for (int i = 0; i < savedBooks.size(); i++) {
                Book book = savedBooks.get(i);
                ParsingDto dto = booksToSaveDtos.get(i); // 순서가 동일하므로 매칭 가능

                if (StringUtils.hasText(dto.getAuthor())) {
                    String[] splitAuthors = dto.getAuthor().split(",");
                    for (String rawName : splitAuthors) {
                        String name = rawName.trim();
                        Author author = authorMap.get(name);

                        if (author != null) {
                            bookAuthors.add(BookAuthor.builder()
                                    .book(book)
                                    .author(author)
                                    .build());
                        }
                    }
                }
            }

            if (!bookAuthors.isEmpty()) {
                bookAuthorRepository.saveAll(bookAuthors);
                log.info("도서-작가 관계 {}건 연결 완료", bookAuthors.size());
            }
        }
    }

    private Map<String, Publisher> resolvePublishers(Set<String> names) {
        Map<String, Publisher> map = new HashMap<>();
        if (names.isEmpty()) return map;

        // DB 조회
        List<Publisher> existing = publisherRepository.findAllByNameIn(names);
        existing.forEach(p -> map.put(p.getName(), p));

        // 없는 것만 필터링하여 저장
        List<Publisher> toSave = new ArrayList<>();
        for (String name : names) {
            if (!map.containsKey(name)) {
                toSave.add(Publisher.builder().name(name).build());
            }
        }

        if (!toSave.isEmpty()) {
            publisherRepository.saveAll(toSave).forEach(p -> map.put(p.getName(), p));
        }
        return map;
    }

    private Map<String, Author> resolveAuthors(Set<String> names) {
        Map<String, Author> map = new HashMap<>();
        if (names.isEmpty()) return map;

        // DB 조회
        List<Author> existing = authorRepository.findAllByNameIn(names);
        existing.forEach(a -> map.put(a.getName(), a));

        // 없는 것만 필터링하여 저장
        List<Author> toSave = new ArrayList<>();
        for (String name : names) {
            if (!map.containsKey(name)) {
                toSave.add(Author.builder().name(name).build());
            }
        }

        if (!toSave.isEmpty()) {
            authorRepository.saveAll(toSave).forEach(a -> map.put(a.getName(), a));
        }
        return map;
    }

    private int parsePrice(String priceStr) {
        if (!StringUtils.hasText(priceStr)) return 0;
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) return LocalDate.now();
        try {
            // 형식이 yyyy-MM-dd라고 가정
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }
}