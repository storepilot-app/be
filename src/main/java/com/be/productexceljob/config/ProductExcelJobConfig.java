package com.be.productexceljob.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ProductExcelJobConfig {
    @Bean(name = "productExcelJobExecutor")
    public Executor productExcelJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4); // 상품 엑셀 작업을 기본 4개까지 동시에 처리
        executor.setMaxPoolSize(4); // 동시에 실행할 수 있는 최대 작업 수
        executor.setQueueCapacity(50); // 처리하지 못한 작업은 최대 50개까지 대기
        executor.setThreadNamePrefix("product-excel-job-"); // 로그에서 작업 스레드를 구분할 접두사
        executor.initialize();
        return executor;
    }
}
