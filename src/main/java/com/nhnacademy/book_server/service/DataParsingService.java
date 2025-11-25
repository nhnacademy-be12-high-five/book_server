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

    // 한 번에 처리할 배치 사이즈 (DB 파라미터 제한 회피용)
    private static final int BATCH_SIZE = 1000;

    public void saveAll(List<ParsingDto> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        log.info("총 {}건의 데이터 파싱 완료. 중복 확인 및 저장 시작...", records.size());

        Set<String> allPublisherNames = new HashSet<>();
        Set<String> allAuthorNames = new HashSet<>();
        Set<String> allIsbns = new HashSet<>();

        // 1. 데이터 수집
        for (ParsingDto dto : records) {
            if (StringUtils.hasText(dto.getPublisher())) {
                allPublisherNames.add(dto.getPublisher().trim());
            }
            if (StringUtils.hasText(dto.getAuthor())) {
                String[] splitAuthors = dto.getAuthor().split("[,;]");
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

        // 2. 출판사 처리 (배치 조회)
        Map<String, Publisher> publisherMap = resolvePublishers(allPublisherNames);

        // 3. 작가 처리 (배치 조회)
        Map<String, Author> authorMap = resolveAuthors(allAuthorNames);

        // 4. 이미 존재하는 ISBN 확인 (배치 조회)
        Set<String> existingIsbnSet = new HashSet<>();
        List<String> isbnList = new ArrayList<>(allIsbns);

        for (int i = 0; i < isbnList.size(); i += BATCH_SIZE) {
            List<String> batch = isbnList.subList(i, Math.min(isbnList.size(), i + BATCH_SIZE));
            bookRepository.findAllByIsbnIn(new HashSet<>(batch))
                    .forEach(book -> existingIsbnSet.add(book.getIsbn()));
        }

        // 5. Book 객체 생성
        List<Book> newBooks = new ArrayList<>();
        List<ParsingDto> booksToSaveDtos = new ArrayList<>();

        for (ParsingDto dto : records) {
            String isbn = dto.getIsbn() != null ? dto.getIsbn().trim() : "";

            if (isbn.isEmpty() || existingIsbnSet.contains(isbn)) {
                continue;
            }
            existingIsbnSet.add(isbn); // CSV 내부 중복 방지

            Publisher publisher = publisherMap.get(dto.getPublisher().trim());

            Book book = Book.builder()
                    .isbn(isbn)
                    .title(dto.getTitle())
                    .publisher(publisher)
                    .price(parsePrice(dto.getPrice()))
                    .content(dto.getDescription())
                    .image(dto.getImageUrl())
                    .publishedDate(parseDate(dto.getPubDate()).toString())
                    .build();

            newBooks.add(book);
            booksToSaveDtos.add(dto);
        }

        // 6. Book 일괄 저장 (배치 저장)
        if (!newBooks.isEmpty()) {
            saveBooksInBatch(newBooks, booksToSaveDtos, authorMap);
        } else {
            log.info("저장할 새로운 도서가 없습니다.");
        }
    }

    // 도서와 작가 관계를 배치로 나누어 저장하는 메서드
    private void saveBooksInBatch(List<Book> books, List<ParsingDto> dtos, Map<String, Author> authorMap) {
        int total = books.size();
        log.info("새로운 도서 {}권 저장을 시작합니다.", total);

        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(total, i + BATCH_SIZE);
            List<Book> bookBatch = books.subList(i, end);
            List<ParsingDto> dtoBatch = dtos.subList(i, end);

            // 1) 책 저장
            bookRepository.saveAll(bookBatch);

            // 2) 책-작가 관계 생성
            List<BookAuthor> bookAuthors = new ArrayList<>();
            for (int j = 0; j < bookBatch.size(); j++) {
                Book book = bookBatch.get(j);
                ParsingDto dto = dtoBatch.get(j);

                if (StringUtils.hasText(dto.getAuthor())) {
                    String[] splitAuthors = dto.getAuthor().split("[,;]");
                    Set<Author> distinctAuthors = new HashSet<>();
                    for (String rawName : splitAuthors) {
                        String name = rawName.trim();
                        Author author = authorMap.get(name);
                        if (author != null && distinctAuthors.add(author)) {
                            bookAuthors.add(BookAuthor.builder()
                                    .book(book)
                                    .author(author)
                                    .build());
                        }
                    }
                }
            }

            // 3) 관계 저장
            if (!bookAuthors.isEmpty()) {
                bookAuthorRepository.saveAll(bookAuthors);
            }

            log.info("진행률: {}/{} 권 저장 완료", end, total);
        }
        log.info("모든 데이터 저장 완료!");
    }

    private Map<String, Publisher> resolvePublishers(Set<String> names) {
        Map<String, Publisher> map = new HashMap<>();
        if (names.isEmpty()) return map;

        List<String> nameList = new ArrayList<>(names);

        // 배치 조회 (있는 것 찾기)
        for (int i = 0; i < nameList.size(); i += BATCH_SIZE) {
            List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH_SIZE));
            publisherRepository.findAllByNameIn(new HashSet<>(batch))
                    .forEach(p -> map.put(p.getName(), p));
        }

        // 없는 것만 필터링하여 저장
        List<Publisher> toSave = new ArrayList<>();
        for (String name : names) {
            if (!map.containsKey(name)) {
                toSave.add(Publisher.builder().name(name).build());
            }
        }

        if (!toSave.isEmpty()) {
            // 배치 저장
            for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
                List<Publisher> batch = toSave.subList(i, Math.min(toSave.size(), i + BATCH_SIZE));
                publisherRepository.saveAll(batch).forEach(p -> map.put(p.getName(), p));
            }
        }
        return map;
    }

    private Map<String, Author> resolveAuthors(Set<String> names) {
        Map<String, Author> map = new HashMap<>();
        if (names.isEmpty()) return map;

        List<String> nameList = new ArrayList<>(names);

        // 배치 조회
        for (int i = 0; i < nameList.size(); i += BATCH_SIZE) {
            List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH_SIZE));
            authorRepository.findAllByNameIn(new HashSet<>(batch))
                    .forEach(a -> map.put(a.getName(), a));
        }

        // 없는 것 저장
        List<Author> toSave = new ArrayList<>();
        for (String name : names) {
            if (!map.containsKey(name)) {
                toSave.add(Author.builder().name(name).build());
            }
        }

        if (!toSave.isEmpty()) {
            // 배치 저장
            for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
                List<Author> batch = toSave.subList(i, Math.min(toSave.size(), i + BATCH_SIZE));
                authorRepository.saveAll(batch).forEach(a -> map.put(a.getName(), a));
            }
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
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }
}