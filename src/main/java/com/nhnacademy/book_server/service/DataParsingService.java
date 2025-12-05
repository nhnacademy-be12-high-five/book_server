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
import jakarta.persistence.EntityManager;
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
public class DataParsingService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;
    private final BookAuthorRepository bookAuthorRepository;
    private final EntityManager entityManager;

    private final TransactionalService transactionalService;

    private static final int BATCH_SIZE = 1000;

    public void saveAll(List<ParsingDto> records) {
        if (records == null || records.isEmpty()) return;

        log.info("총 {}건의 데이터 파싱 완료. 데이터 처리(Upsert) 시작...", records.size());

        Set<String> allPublisherNames = new HashSet<>();
        Set<String> allAuthorNames = new HashSet<>();

        // 1. 데이터 수집 (출판사, 작가 이름 모으기)
        for (ParsingDto dto : records) {
            if (StringUtils.hasText(dto.getPublisher())) {
                allPublisherNames.add(dto.getPublisher().trim());
            }
            if (StringUtils.hasText(dto.getAuthor())) {
                for (String authorName : dto.getAuthor().split("[,;]")) {
                    if (StringUtils.hasText(authorName)) {
                        allAuthorNames.add(authorName.trim());
                    }
                }
            }
        }

<<<<<<< Updated upstream
        // 2. 출판사/작가 처리 (트랜잭션 분리하여 미리 확보)
        Map<String, Publisher> publisherMap = transactionalService.executeInNewTransaction(
                () -> resolvePublishers(allPublisherNames)
        );
        Map<String, Author> authorMap = transactionalService.executeInNewTransaction(
                () -> resolveAuthors(allAuthorNames)
        );

        // 3. 책 데이터 배치 처리 (Insert + Update)
        saveBooksInBatch(records, publisherMap, authorMap);
    }

    private void saveBooksInBatch(List<ParsingDto> dtos, Map<String, Publisher> publisherMap, Map<String, Author> authorMap) {
        int total = dtos.size();
=======
        Map<String, Publisher> publisherMap = resolvePublishers(allPublisherNames);
        Map<String, Author> authorMap = resolveAuthors(allAuthorNames);

        // 4. 이미 존재하는 ISBN 확인 (배치 조회)
        Set<String> existingIsbnSet = new HashSet<>();
        List<String> isbnList = new ArrayList<>(allIsbns);

        for (int i = 0; i < isbnList.size(); i += BATCH_SIZE) {
            List<String> batch = isbnList.subList(i, Math.min(isbnList.size(), i + BATCH_SIZE));
            // 읽기 전용 트랜잭션은 괜찮음
            transactionalService.executeInNewTransaction(() -> {
                bookRepository.findAllByIsbn13In(new HashSet<>(batch))
                        .forEach(book -> existingIsbnSet.add(book.getIsbn13()));
                return null;
            });
        }

        // 5. Book 객체 생성
        List<Book> newBooks = new ArrayList<>();
        List<ParsingDto> booksToSave = new ArrayList<>();

        for (ParsingDto dto : records) {
            String isbn = dto.getIsbn() != null ? dto.getIsbn().trim() : "";

            if (isbn.isEmpty() || existingIsbnSet.contains(isbn)) {
                continue;
            }

            existingIsbnSet.add(isbn); // CSV 내부 중복 방지

            String pubName = dto.getPublisher() != null ? dto.getPublisher().trim() : "";
            Publisher publisher = publisherMap.get(pubName);

            // [중요] 맵에서 못 찾았을 경우 경고 로그 출력!!
            if (publisher == null) {
                if (!pubName.isEmpty()) {
                    // 이름은 있는데 맵에 없다? -> 로직 문제
                    log.error("🚨 비상: 출판사 매핑 실패! 이름: [{}]", pubName);
                } else {
                    // 이름 자체가 비어있다? -> CSV 파서 문제 (인덱스 확인 필요)
                    log.warn("⚠️ 경고: 출판사 데이터가 비어있음. ISBN: {}", isbn);
                }
            }

//            Publisher publisher = publisherMap.get(dto.getPublisher().trim());

            Book book = Book.builder()
                    .isbn13(isbn)
                    .title(dto.getTitle())
                    .publisher(publisher)
                    .price(parsePrice(dto.getPrice()))
                    .content(dto.getDescription())
                    .image(dto.getImageUrl())
                    .publishedDate(parseDate(dto.getPubDate()).toString())
                    .build();

            newBooks.add(book);
            booksToSave.add(dto);
        }

        // 6. Book 일괄 저장 (배치 저장)
        if (!newBooks.isEmpty()) {
            saveBooksInBatch(newBooks, booksToSave, authorMap);
        }
    }

    // 도서와 작가 관계를 배치로 나누어 저장하는 메서드
    // [수정된 메서드] 유령 출판사/작가 복구 로직 추가
    private void saveBooksInBatch(List<Book> books, List<ParsingDto> dtos, Map<String, Author> authorMap) {
        int total = books.size();
        log.info("총 {}권의 도서 저장을 시작합니다.", total);
>>>>>>> Stashed changes

        for (int i = 0; i < total; i += BATCH_SIZE) {
            int start = i;
            int end = Math.min(total, i + BATCH_SIZE);

<<<<<<< Updated upstream
            transactionalService.executeInNewTransaction(() -> {
                List<ParsingDto> batchDtos = dtos.subList(start, end);

                // 1) 현재 배치의 ISBN 목록 추출
                Set<String> batchIsbns = batchDtos.stream()
                        .map(dto -> dto.getIsbn() != null ? dto.getIsbn().trim() : "")
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toSet());

                // 2) DB에서 이미 존재하는 책들을 조회 (Map으로 변환: ISBN -> Book)
                List<Book> existingBooks = bookRepository.findAllByIsbn13In(batchIsbns);
                Map<String, Book> bookMap = existingBooks.stream()
                        .collect(Collectors.toMap(Book::getIsbn13, book -> book));

                List<Book> booksToSave = new ArrayList<>();
                // 신규 생성된 책인지 여부를 추적하기 위한 Set (ISBN 기준)
                Set<String> newBookIsbns = new HashSet<>();

                // 3) DTO 루프: 있으면 Update, 없으면 Create
                for (ParsingDto dto : batchDtos) {
                    String isbn = dto.getIsbn() != null ? dto.getIsbn().trim() : "";
                    if (!StringUtils.hasText(isbn)) continue;

                    Publisher publisher = publisherMap.get(dto.getPublisher() != null ? dto.getPublisher().trim() : "");
                    // [중요] 출판사 Merge (준영속 상태 -> 영속 상태로 전환)
                    Publisher managedPublisher = (publisher != null) ? entityManager.merge(publisher) : null;

                    Book book = bookMap.get(isbn);

                    if (book != null) {
                        // A. 이미 존재함 -> 정보 업데이트 (Dirty Checking)
                        book.updateBookInfo(
                                dto.getTitle(),
                                managedPublisher,
                                parsePrice(dto.getPrice()),
                                dto.getDescription(),
                                dto.getImageUrl(),
                                parseDate(dto.getPubDate()).toString()
                        );
                        // 기존 책은 booksToSave에 넣어서 saveAll 호출 (merge 효과)
                        booksToSave.add(book);
                    } else {
                        // B. 없음 -> 신규 생성
                        book = Book.builder()
                                .isbn13(isbn)
                                .title(dto.getTitle())
                                .publisher(managedPublisher)
                                .price(parsePrice(dto.getPrice()))
                                .content(dto.getDescription())
                                .image(dto.getImageUrl())
                                .publishedDate(parseDate(dto.getPubDate()).toString())
                                .build();

                        booksToSave.add(book);
                        newBookIsbns.add(isbn); // 신규 책임을 표시
                    }
                }

                // 4) 책 일괄 저장 (영속화)
                // saveAll은 신규 엔티티는 persist, 기존 엔티티는 merge 처리합니다.
                List<Book> savedBooks = bookRepository.saveAll(booksToSave);
                entityManager.flush();

                // 5) 작가 연결 (신규 책인 경우에만 수행하여 중복 방지)
                List<BookAuthor> bookAuthorsToSave = new ArrayList<>();

                // savedBooks 순서는 booksToSave 순서와 동일함
                for (int idx = 0; idx < savedBooks.size(); idx++) {
                    Book book = savedBooks.get(idx);
                    // 방금 저장된 책이 '신규'인 경우에만 작가 관계를 맺음 (기존 책은 관계 유지)
                    if (newBookIsbns.contains(book.getIsbn13())) {
                        // 원본 DTO 찾기 (ISBN으로 매칭하거나 순서 이용)
                        // 여기서는 순서가 같으므로 batchDtos가 아니라 booksToSave 생성 시점의 DTO 매칭이 필요하지만,
                        // 구조상 batchDtos를 순회하며 booksToSave를 만들었으므로 약간의 인덱스 차이가 있을 수 있음.
                        // 안전하게 ISBN으로 DTO를 다시 찾습니다. (성능상 Map 사용 추천하지만 여기선 간단히)
                        ParsingDto matchedDto = findDtoByIsbn(batchDtos, book.getIsbn13());

                        if (matchedDto != null && StringUtils.hasText(matchedDto.getAuthor())) {
                            String[] splitAuthors = matchedDto.getAuthor().split("[,;]");
                            for (String rawName : splitAuthors) {
                                Author author = authorMap.get(rawName.trim());
                                if (author != null) {
                                    Author managedAuthor = entityManager.merge(author);
                                    bookAuthorsToSave.add(BookAuthor.builder()
                                            .book(book)
                                            .author(managedAuthor)
                                            .build());
=======
            try {
                // 트랜잭션 시작
                transactionalService.executeInNewTransaction(() -> {
                    List<Book> bookBatch = books.subList(start, end);
                    List<ParsingDto> dtoBatch = dtos.subList(start, end);

                    // 1) [중요] 책 저장 전, 출판사 유효성 검사 (Ghost ID 치료)
                    for (Book book : bookBatch) {
                        Publisher pub = book.getPublisher();
                        if (pub != null) {
                            // ID가 있는데 DB에 없는지 확인 (1차 캐시 혹은 DB 조회)
                            boolean isGhost = false;
                            if (pub.getPublisherId() != null) {
                                // DB에 진짜 있는지 확인
                                if (entityManager.find(Publisher.class, pub.getPublisherId()) == null) {
                                    isGhost = true;
                                }
                            }

                            if (isGhost) {
                                log.warn("👻 유령 출판사 발견! (ID: {}, 이름: {}). 복구를 시도합니다.", pub.getPublisherId(), pub.getName());
                                // 이름으로 다시 찾기
                                Publisher realPub = publisherRepository.findByName(pub.getName()).orElse(null);

                                if (realPub == null) {
                                    // 진짜 없으면 새로 만듦 (ID 초기화 후 저장)
                                    realPub = Publisher.builder().name(pub.getName()).build();
                                    realPub = publisherRepository.save(realPub);
                                }
                                // 책에 진짜 출판사 연결
                                book.setPublisher(realPub);
                            } else {
                                // 유령이 아니면 안전하게 merge (영속성 컨텍스트 연결)
                                // ID가 없으면(null) save 할 때 cascade 되거나 에러날 수 있으나, 보통 위 로직에서 걸러짐
                                if (pub.getPublisherId() != null) {
                                    book.setPublisher(entityManager.merge(pub));
                                }
                            }
                        }
                    }

                    // 2) 책 일괄 저장
                    bookRepository.saveAll(bookBatch);

                    // 3) 책-작가 관계 생성
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
                                    // [안전 장치] 작가도 유령일 수 있으므로 merge 시도
                                    Author managedAuthor = null;
                                    if (author.getId() != null) {
                                        Author found = entityManager.find(Author.class, author.getId());
                                        if (found != null) {
                                            managedAuthor = found; // 유령 아님, 정상!
                                        }
                                    }

                                    if (managedAuthor != null) {
                                        bookAuthors.add(BookAuthor.builder()
                                                .book(book)
                                                .author(managedAuthor)
                                                .build());
                                    }
>>>>>>> Stashed changes
                                }
                            }
                        }
                    }
<<<<<<< Updated upstream
                }

                if (!bookAuthorsToSave.isEmpty()) {
                    bookAuthorRepository.saveAll(bookAuthorsToSave);
                }

                entityManager.flush();
                entityManager.clear(); // 1차 캐시 비우기
                return null;
            });

            log.info("진행률: {}/{} 권 처리 완료", end, total);
        }
        log.info("모든 데이터 처리 완료!");
    }

    // 리스트에서 ISBN으로 DTO 찾는 헬퍼 메서드
    private ParsingDto findDtoByIsbn(List<ParsingDto> dtos, String isbn) {
        for (ParsingDto dto : dtos) {
            String dtoIsbn = dto.getIsbn() != null ? dto.getIsbn().trim() : "";
            if (dtoIsbn.equals(isbn)) {
                return dto;
            }
        }
        return null;
=======

                    if (!bookAuthors.isEmpty()) {
                        bookAuthorRepository.saveAll(bookAuthors);
                    }
                    return null;
                });

                log.info("진행률: {}/{} 권 저장 성공", end, total);

            } catch (Exception e) {
                log.error("❌ 배치 저장 실패 (Index: {} ~ {}). 원인: {}", start, end, e.getMessage());
                // 상세 원인 파악을 위해 필요시 주석 해제
                // e.printStackTrace();
            }
        }
        log.info("모든 데이터 저장 로직 종료!");
>>>>>>> Stashed changes
    }

    private Map<String, Publisher> resolvePublishers(Set<String> names) {
        Map<String, Publisher> map = new HashMap<>();
        if (names.isEmpty()) return map;

        List<String> nameList = new ArrayList<>(names);
        Map<String, Publisher> lowerCaseMap = new HashMap<>();

        for (int i = 0; i < nameList.size(); i += BATCH_SIZE) {
            List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH_SIZE));
            transactionalService.executeInNewTransaction(() -> {
                publisherRepository.findAllByNameIn(new HashSet<>(batch))
                        .forEach(p -> lowerCaseMap.put(p.getName().toLowerCase(), p));
                return null;
            });
        }

        List<Publisher> toSave = new ArrayList<>();
        for (String name : names) {
            String lowerName = name.toLowerCase();
            if (!lowerCaseMap.containsKey(lowerName)) {
                Publisher newPub = Publisher.builder().name(name).build();
                toSave.add(newPub);
                lowerCaseMap.put(lowerName, newPub);
            }
        }

        if (!toSave.isEmpty()) {
            for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
                List<Publisher> batch = toSave.subList(i, Math.min(toSave.size(), i + BATCH_SIZE));
                try {
<<<<<<< Updated upstream
                    publisherRepository.saveAll(batch).forEach(p ->
                            lowerCaseMap.put(p.getName().toLowerCase(), p)
                    );
=======
                    // [시도 A] 시원하게 한 번에 저장
                    transactionalService.executeInNewTransaction(() -> {
                        publisherRepository.saveAll(batch); // 여기서 실패하면 이 트랜잭션만 롤백됨
                        return null;
                    });

                    // 성공 시 맵에 등록
                    batch.forEach(p -> lowerCaseMap.put(p.getName().toLowerCase(), p));
>>>>>>> Stashed changes
                } catch (Exception e) {
                    entityManager.clear();
                    // 개별 저장 로직 (생략 없이 이전 코드와 동일하게 사용하시면 됩니다)
                    // ... (이전 답변의 resolvePublishers 안전 저장 로직 참조)
                    for (Publisher p : batch) {
                        try {
<<<<<<< Updated upstream
                            Publisher saved = publisherRepository.save(p);
                            lowerCaseMap.put(saved.getName().toLowerCase(), saved);
=======
                            // 개별 건마다 새로운 트랜잭션 사용
                            transactionalService.executeInNewTransaction(() -> {
                                Publisher saved = publisherRepository.save(p);
                                lowerCaseMap.put(saved.getName().toLowerCase(), saved);
                                return null;
                            });
>>>>>>> Stashed changes
                        } catch (Exception ex) {
                            entityManager.clear();
                            try {
<<<<<<< Updated upstream
                                Publisher existing = publisherRepository.findByName(p.getName()).orElseThrow();
                                lowerCaseMap.put(existing.getName().toLowerCase(), existing);
                            } catch (Exception fatal) {}
=======
                                Publisher existing = transactionalService.executeInNewTransaction(() ->
                                        publisherRepository.findByName(p.getName()).orElse(null)
                                );
                                if (existing != null) {
                                    lowerCaseMap.put(existing.getName().toLowerCase(), existing);
                                }
                            } catch (Exception fatal) {
                                log.error("🚨 출판사 최종 실패: {}", p.getName());
                            }
>>>>>>> Stashed changes
                        }
                    }
                }
            }
        }

        for (String name : names) {
            Publisher p = lowerCaseMap.get(name.toLowerCase());
            if (p != null) map.put(name, p);
        }
        return map;
    }

    private Map<String, Author> resolveAuthors(Set<String> names) {
        // ... (resolvePublishers와 동일한 로직, AuthorRepository 사용) ...
        // 코드가 너무 길어져서 생략했지만, 이전 답변의 resolveAuthors 메서드를 그대로 쓰시면 됩니다.
        Map<String, Author> map = new HashMap<>();
        if (names.isEmpty()) return map;
        List<String> nameList = new ArrayList<>(names);
        Map<String, Author> lowerCaseMap = new HashMap<>();

        for (int i = 0; i < nameList.size(); i += BATCH_SIZE) {
            List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH_SIZE));
            transactionalService.executeInNewTransaction(() -> {
                authorRepository.findAllByNameIn(new HashSet<>(batch))
                        .forEach(a -> lowerCaseMap.put(a.getName().toLowerCase(), a));
                return null;
            });
        }

        List<Author> toSave = new ArrayList<>();
        for (String name : names) {
            String lowerName = name.toLowerCase();
            if (!lowerCaseMap.containsKey(lowerName)) {
                Author newAuthor = Author.builder().name(name).build();
                toSave.add(newAuthor);
                lowerCaseMap.put(lowerName, newAuthor);
            }
        }

        if (!toSave.isEmpty()) {
            for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
                List<Author> batch = toSave.subList(i, Math.min(toSave.size(), i + BATCH_SIZE));
                try {
<<<<<<< Updated upstream
                    authorRepository.saveAll(batch).forEach(a -> lowerCaseMap.put(a.getName().toLowerCase(), a));
=======
                    // [시도 A] 배치 저장 (새 트랜잭션)
                    transactionalService.executeInNewTransaction(() -> {
                        authorRepository.saveAll(batch);
                        return null;
                    });

                    batch.forEach(a -> lowerCaseMap.put(a.getName().toLowerCase(), a));

>>>>>>> Stashed changes
                } catch (Exception e) {
                    entityManager.clear();
                    for (Author a : batch) {
                        try {
                            transactionalService.executeInNewTransaction(() -> {
                                Author saved = authorRepository.save(a);
                                lowerCaseMap.put(saved.getName().toLowerCase(), saved);
                                return null;
                            });
                        } catch (Exception ex) {
                            entityManager.clear();
                            try {
<<<<<<< Updated upstream
                                Author existing = authorRepository.findByName(a.getName()).orElseThrow();
                                lowerCaseMap.put(existing.getName().toLowerCase(), existing);
                            } catch (Exception fatal) {}
=======
                                Author existing = transactionalService.executeInNewTransaction(() ->
                                        authorRepository.findByName(a.getName()).orElse(null)
                                );
                                if (existing != null) {
                                    lowerCaseMap.put(existing.getName().toLowerCase(), existing);
                                }
                            } catch (Exception fatal) {
                                log.error("🚨 작가 최종 실패: {}", a.getName());
                            }
>>>>>>> Stashed changes
                        }
                    }
                }
            }
        }

        for (String name : names) {
            Author a = lowerCaseMap.get(name.toLowerCase());
            if (a != null) map.put(name, a);
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

    // DataParsingService.java

    // 🛠️ 날짜 수정 전용 긴급 복구 메서드
    public void fixDatesOnly(List<ParsingDto> records) {
        if (records == null || records.isEmpty()) return;

        log.info("📅 날짜 복구 작업 시작! 총 {}건", records.size());

        // 배치 사이즈만큼 나눠서 처리 (메모리 보호)
        for (int i = 0; i < records.size(); i += BATCH_SIZE) {
            int end = Math.min(records.size(), i + BATCH_SIZE);
            List<ParsingDto> batchDtos = records.subList(i, end);

            // 1. 이번 배치의 ISBN 목록 추출
            Set<String> isbns = batchDtos.stream()
                    .map(dto -> dto.getIsbn().trim())
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());

            transactionalService.executeInNewTransaction(() -> {
                // 2. DB에서 해당 ISBN을 가진 책들을 한꺼번에 조회 (성능 최적화)
                List<Book> books = bookRepository.findAllByIsbn13In(isbns);

                // 검색 속도를 위해 Map으로 변환 (ISBN -> Book)
                Map<String, Book> bookMap = books.stream()
                        .collect(Collectors.toMap(Book::getIsbn13, book -> book));

                List<Book> dirtyBooks = new ArrayList<>();

                // 3. 날짜 업데이트 진행
                for (ParsingDto dto : batchDtos) {
                    String isbn = dto.getIsbn().trim();
                    Book book = bookMap.get(isbn);

                    if (book != null) {
                        String newDateStr = parseDate(dto.getPubDate()).toString(); // 이제 제대로 된 날짜가 옴

                        // 기존 날짜와 다를 때만 업데이트 (불필요한 DB 쓰기 방지)
                        if (!newDateStr.equals(book.getPublishedDate())) {
                            book.setPublishedDate(newDateStr);
                            dirtyBooks.add(book);
                        }
                    }
                }

                // 4. 변경된 책들만 일괄 저장 (JPA Dirty Checking이 동작하지만 명시적으로 saveAll 권장)
                if (!dirtyBooks.isEmpty()) {
                    bookRepository.saveAll(dirtyBooks);
                }

                return null;
            });

            // 메모리 청소
            entityManager.clear();
        }
        log.info("🎉 모든 날짜 복구 작업이 완료되었습니다!");
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