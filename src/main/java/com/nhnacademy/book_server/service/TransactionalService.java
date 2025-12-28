package com.nhnacademy.book_server.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
public class TransactionalService {

    // 🚨 중요: 반드시 REQUIRES_NEW여야 독립적인 저장이 보장됩니다!
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T executeInNewTransaction(Supplier<T> supplier) {
        return supplier.get();
    }

    // 리턴값이 없는 경우를 위한 오버로딩 (필요하다면)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeInNewTransaction(Runnable runnable) {
        runnable.run();
    }
}