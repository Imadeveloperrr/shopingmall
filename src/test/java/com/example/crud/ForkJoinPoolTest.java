package com.example.crud;

import org.junit.jupiter.api.Test;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

/**
 * ForkJoinPool 동작 확인 테스트
 */
@Slf4j
public class ForkJoinPoolTest {

    @Test
    public void testForkJoinPoolSize() {
        // ForkJoinPool 기본 크기 확인
        int poolSize = ForkJoinPool.commonPool().getParallelism();
        int cpuCores = Runtime.getRuntime().availableProcessors();

        log.info("=== ForkJoinPool 정보 ===");
        log.info("CPU 코어 수: {}", cpuCores);
        log.info("ForkJoinPool 크기: {}", poolSize);
        log.info("예상 크기 (코어-1): {}", cpuCores - 1);
    }

    @Test
    public void testCompletableFutureThread() throws Exception {
        log.info("=== CompletableFuture 스레드 확인 ===");

        // 메인 스레드 확인
        log.info("메인 스레드: {}", Thread.currentThread().getName());

        // CompletableFuture로 비동기 실행
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            log.info("supplyAsync 스레드: {}", Thread.currentThread().getName());
            return "결과";
        });

        future.thenApply(result -> {
            log.info("thenApply 스레드: {}", Thread.currentThread().getName());
            return result + " 처리완료";
        }).get();  // 결과 대기

        Thread.sleep(100);  // 로그 출력 대기
    }

    @Test
    public void testWorkStealing() throws Exception {
        log.info("=== Work-Stealing 확인 ===");

        // 10개의 비동기 작업 생성
        IntStream.range(0, 10).forEach(i -> {
            CompletableFuture.runAsync(() -> {
                log.info("작업 {} - 스레드: {}", i, Thread.currentThread().getName());
                try {
                    Thread.sleep(100);  // 작업 시뮬레이션
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });

        Thread.sleep(1000);  // 모든 작업 완료 대기
    }
}
