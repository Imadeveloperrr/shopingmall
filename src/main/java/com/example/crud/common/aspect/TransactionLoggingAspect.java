package com.example.crud.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 트랜잭션 메서드 로깅
 * - @Transactional이 붙은 메서드만 로깅
 * - 비즈니스 로직 실행 내용만 기록
 */
@Aspect
@Component
@Slf4j
public class TransactionLoggingAspect {

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object logTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        log.info("트랜잭션 시작: {}.{}", className, methodName);

        try {
            Object result = joinPoint.proceed();
            log.info("트랜잭션 커밋: {}.{}", className, methodName);
            return result;
        } catch (Exception e) {
            log.error("트랜잭션 롤백: {}.{} - 원인: {}", className, methodName, e.getMessage());
            throw e;
        }
    }
}
