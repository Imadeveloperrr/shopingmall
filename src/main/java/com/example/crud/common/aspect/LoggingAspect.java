package com.example.crud.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Service 메서드 로깅 자동화
 * - 모든 Service 메서드의 시작/종료/예의를 자동으로 로깅
 * - 각 메서드에 log.info 작성 불필요
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    /**
     * Service의 모든 public 메서드 실행 시 자동 로깅
     */
    @Around("execution(* com.example.crud.data.*.service.impl.*.*(..))")
    public Object logServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();

        log.debug("메서드 시작: {} - 파라미터: {}", methodName, Arrays.toString(args) );

        try {
            Object result = joinPoint.proceed();
            log.debug("메서드 완료: {}", methodName);
            return result;
        } catch (Exception e) {
            log.error("메서드 예외 발생: {} - 원인: {}", methodName, e.getMessage());
            throw e;
        }
    }
}
