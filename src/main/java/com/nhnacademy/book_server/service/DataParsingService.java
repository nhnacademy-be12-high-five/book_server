package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.Author;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookAuthor;
import com.nhnacademy.book_server.entity.Publisher;
import com.nhnacademy.book_server.parser.DataParser;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.authorRepository;
import com.nhnacademy.book_server.repository.bookAuthorRepository;
import com.nhnacademy.book_server.repository.publisherRepository;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DataParsingService {

    @Autowired
    private final BookService bookService;
    private final DataParserResolver dataParserResolver;
    private final AuthorService authorService;
    private final authorRepository authorRepository;
    private final publisherRepository publisherRepository;
    private final BookRepository bookRepository;
    private final bookAuthorRepository bookAuthorRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @PostConstruct
    public void init() {
        try {
            loadData("classpath:data/*.*");
        } catch (IOException e) {
            log.error("데이터 로드 실패", e);
        }
    }

    public void loadData(String location) throws IOException {
        // file, classpath에 위치한 리소스를 제공해주는 Resource 라는 추상화된 인터페이스를 제공해준다.
        PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

        // 1. 리소스 탐색
        Resource[] resources = resourceResolver.getResources(location);

        for (Resource r : resources) {
            String fileName = r.getFilename();
            if (fileName == null) {
                continue;
            }

            // // 2. 파서 결정 (예: "book.csv" -> CsvDataParser)
            DataParser parser = dataParserResolver.getDataParser(fileName);

            if (parser != null) {
                File file = r.getFile();
                List<ParsingDto> records = parser.parsing(file);
                saveDataBulk(records);
            } else {
                log.error("지원하는 parser가 없습니다.");
            }
        }
    }

    private void saveData (List <ParsingDto> records) throws IOException {
        for (ParsingDto dto : records) {
            try {
                Book book;
                Author author;
                List<String> authorList = dto.getAuthors();

                book = bookService.createBook(dto);

                for (String name : authorList) {
                    author = authorService.save(name);
                    BookAuthor bookAuthor = bookAuthorService.save(book, author);
                    Publisher publisher = publisherService.save(name);
                }
            }
            catch (IllegalArgumentException e) {
                log.error("데이터를 저장할 수 없습니다.");
            }
            catch (Exception e) {
                log.error("데이터를 저장할 수 없습니다.");
            }
        }
    }

    private void saveDataBulk(List<ParsingDto> records) {
        Set<String> allAuthorNames = new HashSet<>();  // 작가 이름 가져오는 hashset
        Set<String> allPublisherNames = new HashSet<>();  // 출판사 가져오는 hashset
        Set<String> allIsbns = new HashSet<>();

        for (ParsingDto dto : records) {
            if (dto.getAuthor() != null) {
                allAuthorNames.addAll(dto.getAuthors());
            }
            if (dto.getPublisher() != null) {
                allPublisherNames.add(dto.getPublisher());
            }

            if (dto.getIsbn() != null){
                allIsbns.add(dto.getIsbn());
            }
        }

        // // 1-1. Author ID Resolution

        // 기존의 작가가 존재하는지 find
        List<Author> existingAuthors = authorRepository.findAllByNameIn(allAuthorNames);
        Map<String, Author> authorMap = existingAuthors.stream()
                .collect(Collectors.toMap(Author::getName, a -> a));

        // // 💡 Batch Save 1: 새로운 Author를 한 번에 저장 (ID 생성)

        // 새로운 작가 저장
        List<Author> newAuthors = new ArrayList<>();
        for (String name : allAuthorNames) {
            if (!authorMap.containsKey(name)) {
                Author newAuthor = Author.builder().name(name).build();
                newAuthors.add(newAuthor);
                // 나중에 Map에서 꺼내 쓸 수 있게 미리 넣어둠 (ID는 아직 없음)
                authorMap.put(name, newAuthor);
            }
        }

        List<Author> savedNewAuthors = authorRepository.saveAll(newAuthors);

        // savedNewAuthors를 authorMap에 다시 넣어 ID가 부여된 객체로 업데이트 (선택적)
        savedNewAuthors.forEach(a -> authorMap.put(a.getName(), a));

        // // 1-2. Publisher ID Resolution
        // 기존의 출판사 아이디가 존재하는지 검증
        List<Publisher> existingPublishers = publisherRepository.findAllByNameIn(allPublisherNames);
        Map<String, Publisher> publisherMap = existingPublishers.stream()
                .collect(Collectors.toMap(Publisher::getName, p -> p));

        // 새로운 출판사
        List<Publisher> newPublishers = new ArrayList<>();
        for (String name : allPublisherNames) {
            if (name != null && !publisherMap.containsKey(name)) {
                Publisher newPub = Publisher.builder().name(name).build();
                newPublishers.add(newPub);
                publisherMap.put(name, newPub);
            }
        }
        // 새로운 출판사 추가
        List<Publisher> savedNewPublishers = publisherRepository.saveAll(newPublishers);
        savedNewPublishers.forEach(p -> publisherMap.put(p.getName(), p)); // ID 업데이트


        // 기존의 책과 기존의 isbn이 존재하는지 검증
        List<Book> existingBooks = bookRepository.findAllByIsbnIn(allIsbns);
        Set<String> existingIsbns = existingBooks.stream()
                .map(Book::getIsbn)
                .collect(Collectors.toSet());

        List<Book> newBooks = new ArrayList<>();
        // 나중에 BookAuthor 연결을 위해 DTO 인덱스와 매칭할 임시 리스트
        List<ParsingDto> targetDtos = new ArrayList<>();

        for (ParsingDto dto : records) {
            // 이미 있는 책이면 스킵 (업데이트 로직이 필요하면 여기서 처리)
            if (existingIsbns.contains(dto.getIsbn())) {
                continue;
            }

            Publisher publisher = publisherMap.get(dto.getPublisher());

            Book book = Book.builder()
                    .title(dto.getTitle())
                    .isbn(dto.getIsbn())
                    .content(dto.getContent())
                    .price(dto.getPrice())
                    .PublishedDate(dto.getPublishedDate())
                    .publisher(publisher)
                    .build();

            newBooks.add(book);
            targetDtos.add(dto); // 저장할 책과 짝이 되는 DTO도 순서대로 저장

            for (int i = 0; i < 10; i++) {
                log.info(newBooks.get(i).getTitle());
            }
        }

        // 4-2. 책 한방에 저장
        List<Book> savedBooks = bookRepository.saveAll(newBooks);

        //
//    // ==========================================
//    // 5. [책-작가 연결] BookAuthor 저장
//    // ==========================================

        List<BookAuthor> bookAuthors = new ArrayList<>();
        for (int i = 0; i < savedBooks.size(); i++) {
            Book book=savedBooks.get(i);
            ParsingDto dto = targetDtos.get(i);

            if (dto.getAuthors() == null){
                continue;
            }

            for (String authorName : dto.getAuthors()) {
                Author author = authorMap.get(authorName);

                if (author != null) {
                    bookAuthors.add(BookAuthor.builder()
                            .book(book)
                            .author(author)
                            .build());
                }
            }

        }

        bookAuthorRepository.saveAll(bookAuthors);
    }

    // savedBooks와 targetDtos는 인덱스 순서가 같음
    private LocalDate parseDateSafe(String pubdate) {
        try {
            return StringUtils.hasText(pubdate)
                    ? LocalDate.parse(pubdate, DATE_FORMATTER)
                    : LocalDate.of(1900, 1, 1);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private Integer parseIntFromString (String value){
        if (StringUtils.hasText(value)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        } else {
            return 0;
        }
    }
}
