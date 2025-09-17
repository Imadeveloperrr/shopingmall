package com.example.crud.common.config;

import com.vladmihalcea.hibernate.type.array.ListArrayType;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.service.ServiceRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * PostgreSQL 배열 타입 지원을 위한 Hibernate 설정
 */
@Configuration
public class HibernateConfig {

    /**
     * PostgreSQL 배열 타입 기여자 (vladmihalcea 라이브러리 사용)
     */
    public static class PgVectorTypeContributor implements TypeContributor {

        @Override
        public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
            // PostgreSQL 배열 타입 지원 추가
            typeContributions.contributeType(new ListArrayType());
        }
    }
}