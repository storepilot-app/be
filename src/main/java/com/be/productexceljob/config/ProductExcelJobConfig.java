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
        executor.setCorePoolSize(2); //기본적으로 작업 처리 스레드 2개 유지
        executor.setMaxPoolSize(4); //최대 4개 스레드
        executor.setQueueCapacity(50); //동시에 처리 못 하는 작업은 최대 50개까지 대기열에 쌓음
        executor.setThreadNamePrefix("product-excel-job-"); //로그에서 찍히는 스레드 이름
        executor.initialize();
        return executor;
    }
}
