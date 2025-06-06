package com.example.crud.common.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

@Configuration
public class MessageSourceConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages/error_messages"); // message 폴더 아래의 error_messages.properties 파일을 사용
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(60); // 60초 캐싱
        messageSource.setUseCodeAsDefaultMessage(true); // 메시지가 없을 경우 코드 반환
        return messageSource;
    }

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver localeResolver = new SessionLocaleResolver();
        localeResolver.setDefaultLocale(Locale.KOREAN); // 기본 로케일을 한국어로 설정
        return localeResolver;
    }
}
