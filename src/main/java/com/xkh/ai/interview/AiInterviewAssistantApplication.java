package com.xkh.ai.interview;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class AiInterviewAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiInterviewAssistantApplication.class, args);
    }

    @Bean
    public DashScopeApi dashScopeApi(DashScopeConnectionProperties connectionProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(60 * 1000);  // 连接超时 60 秒
        requestFactory.setReadTimeout(10 * 60 * 1000); // 读取超时 5 分钟
        return DashScopeApi.builder()
                .apiKey(connectionProperties.getApiKey())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
    }
}
