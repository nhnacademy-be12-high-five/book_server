package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.Author;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookAuthor;
import com.nhnacademy.book_server.entity.Publisher;
import com.nhnacademy.book_server.parser.DataParser;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.repository.AuthorRepository;
import com.nhnacademy.book_server.repository.BookAuthorRepository;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.PublisherRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DataParsingService {

    private final DataParserResolver dataParserResolver;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;
    private final BookRepository bookRepository;
    private final BookAuthorRepository bookAuthorRepository;

    @PostConstruct
    public void init() {
        try {
            // data 폴더 아래의 모든 파일을 읽음
            loadData("classpath:data/*.*");
        } catch (IOException e) {
            log.error("초기 데이터 로드 중 심각한 오류 발생", e);
        }
    }

    public void loadData(String location) throws IOException {
        PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resourceResolver.getResources(location);

        for (Resource r : resources) {
            String fileName = r.getFilename();
            if (fileName == null || fileName.startsWith(".")) {
                // .DS_Store 등 숨김 파일 스킵
                continue;
            }

            // 1. 파서 결정
            DataParser parser = dataParserResolver.getDataParser(fileName);

            if (parser != null) {
                log.info("파일 파싱 시작: {} (Parser: {})", fileName, parser.getClass().getSimpleName());
                File file = r.getFile();
                List<ParsingDto> records = parser.parsing(file);

                if (!records.isEmpty()) {
                    saveDataBulk(records);
                    log.info("파일 파싱 및 저장 완료: {} (총 {}건)", fileName, records.size());
                }
            } else {
                // 파서가 없는 파일은 경고 대신 정보 로그로 남김 (불필요한 에러 로그 방지)
                log.info("스킵됨: 지원하는 파서가 없는 파일입니다. ({})", fileName);
            }
        }
    }

    private void saveDataBulk(List<ParsingDto> records) {
        // 중복 제거 및 데이터 정제용 Set
        // Map Key로 사용할 때는 반드시 '소문자'로 변환하여 대소문자 이슈(Duplicate entry) 방지
        Set<String> allAuthorNamesRaw = new HashSet<>();
        Set<String> allPublisherNamesRaw = new HashSet<>();
        Set<String> allIsbns = new HashSet<>();

        // 1. DTO에서 데이터 추출 및 공백 제거
        for (ParsingDto dto : records) {
            if (dto.getAuthors() != null) {
                for (String author : dto.getAuthors()) {
                    if (StringUtils.hasText(author)) {
                        allAuthorNamesRaw.add(author.trim());
                    }
                }
            }
            if (StringUtils.hasText(dto.getPublisher())) {
                allPublisherNamesRaw.add(dto.getPublisher().trim());
            }
            if (StringUtils.hasText(dto.getIsbn())) {
                allIsbns.add(dto.getIsbn().trim());
            }
        }

        // ==========================================
        // 2. Author 저장 및 매핑 (대소문자 구분 없이 처리)
        // ==========================================
        Map<String, Author> authorMap = new HashMap<>(); // Key: 소문자 이름

        // 2-1. 기존 DB에 있는 작가 조회
        List<Author> existingAuthors = authorRepository.findAllByNameIn(allAuthorNamesRaw);
        for (Author a : existingAuthors) {
            authorMap.put(a.getName().toLowerCase(), a);
        }

        // 2-2. 새로운 작가 선별 및 저장
        List<Author> newAuthors = new ArrayList<>();
        for (String rawName : allAuthorNamesRaw) {
            String lowerName = rawName.toLowerCase();
            if (!authorMap.containsKey(lowerName)) {
                Author newAuthor = Author.builder().name(rawName).build();
                newAuthors.add(newAuthor);
                authorMap.put(lowerName, newAuthor); // 중복 방지를 위해 맵에도 즉시 추가
            }
        }

        if (!newAuthors.isEmpty()) {
            List<Author> savedAuthors = authorRepository.saveAll(newAuthors);
            // 저장 후 ID가 생성된 객체로 Map 업데이트
            for (Author a : savedAuthors) {
                authorMap.put(a.getName().toLowerCase(), a);
            }
        }

        // ==========================================
        // 3. Publisher 저장 및 매핑 (대소문자 구분 없이 처리)
        // ==========================================
        Map<String, Publisher> publisherMap = new HashMap<>(); // Key: 소문자 이름

        List<Publisher> existingPublishers = publisherRepository.findAllByNameIn(allPublisherNamesRaw);
        for (Publisher p : existingPublishers) {
            publisherMap.put(p.getName().toLowerCase(), p);
        }

        List<Publisher> newPublishers = new ArrayList<>();
        for (String rawName : allPublisherNamesRaw) {
            String lowerName = rawName.toLowerCase();
            if (!publisherMap.containsKey(lowerName)) {
                Publisher newPub = Publisher.builder().name(rawName).build();
                newPublishers.add(newPub);
                publisherMap.put(lowerName, newPub); // 중복 방지
            }
        }

        if (!newPublishers.isEmpty()) {
            List<Publisher> savedPublishers = publisherRepository.saveAll(newPublishers);
            for (Publisher p : savedPublishers) {
                publisherMap.put(p.getName().toLowerCase(), p);
            }
        }

        // ==========================================
        // 4. Book 저장
        // ==========================================
        // 이미 존재하는 책 확인 (ISBN 기준)
        List<Book> existingBooks = bookRepository.findAllByIsbnIn(allIsbns);
        Set<String> existingIsbns = existingBooks.stream()
                .map(Book::getIsbn)
                .collect(Collectors.toSet());

        List<Book> newBooks = new ArrayList<>();
        List<ParsingDto> targetDtos = new ArrayList<>(); // 책과 1:1 매칭될 DTO

        for (ParsingDto dto : records) {
            String isbn = dto.getIsbn() != null ? dto.getIsbn().trim() : "";

            if (!StringUtils.hasText(isbn) || existingIsbns.contains(isbn)) {
                continue;
            }

            // Publisher 찾기 (소문자 키 사용)
            String pubKey = dto.getPublisher() != null ? dto.getPublisher().trim().toLowerCase() : "";
            Publisher publisher = publisherMap.get(pubKey);

            Book book = Book.builder()
                    .title(dto.getTitle())
                    .isbn(isbn)
                    .content(dto.getContent())
                    .price(dto.getPrice())
                    .PublishedDate(dto.getPublishedDate()) // 날짜 변환 메서드 사용 (아래 정의)
                    .image(dto.getImage()) // 이미지 필드 누락되어 추가함
                    .publisher(publisher)
                    .build();

            newBooks.add(book);
            targetDtos.add(dto);
            existingIsbns.add(isbn); // 현재 배치 내 중복 ISBN 방지
        }

        // 4-1. 책 저장
        List<Book> savedBooks = bookRepository.saveAll(newBooks);

        // 4-2. 로그 출력 (IndexOutOfBoundsException 수정됨)
        int logLimit = Math.min(savedBooks.size(), 10);
        for (int i = 0; i < logLimit; i++) {
            log.info("Saved Book: {}", savedBooks.get(i).getTitle());
        }

        // ==========================================
        // 5. BookAuthor 연결 (책-작가 관계 저장)
        // ==========================================
        List<BookAuthor> bookAuthors = new ArrayList<>();

        for (int i = 0; i < savedBooks.size(); i++) {
            Book book = savedBooks.get(i);
            ParsingDto dto = targetDtos.get(i);

            if (dto.getAuthors() != null) {
                for (String authorName : dto.getAuthors()) {
                    if (!StringUtils.hasText(authorName)) continue;

                    // Author 찾기 (소문자 키 사용)
                    Author author = authorMap.get(authorName.trim().toLowerCase());

                    if (author != null) {
                        bookAuthors.add(BookAuthor.builder()
                                .book(book)
                                .author(author)
                                .build());
                    }
                }
            }
        }

        bookAuthorRepository.saveAll(bookAuthors);
    }

    // 날짜 파싱 유틸 메서드 (DTO는 String으로 넘어오므로 변환 필요)
    private java.time.LocalDate parseDateSafe(String dateStr) {
        if (!StringUtils.hasText(dateStr)) {
            return java.time.LocalDate.now();
        }
        try {
            // yyyy-MM-dd 형식을 가정. 포맷이 다르면 DateTimeFormatter 수정 필요
            return java.time.LocalDate.parse(dateStr);
        } catch (Exception e) {
            return java.time.LocalDate.now(); // 파싱 실패 시 현재 날짜 혹은 null
        }
    }
}