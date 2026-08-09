package com.edumate.admin.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Neo4j Driver 配置 —— 原生 Java Driver，非 Spring Data
 * <p>
 * 当 Neo4j 不可用时，返回 null（KnowledgeGraphService 和 GraphSearchService 会降级处理）。
 */
@Configuration
public class Neo4jConfig {

    private static final Logger log = LoggerFactory.getLogger(Neo4jConfig.class);

    @Value("${neo4j.uri:bolt://localhost:7687}")
    private String uri;

    @Value("${neo4j.username:neo4j}")
    private String username;

    @Value("${neo4j.password:password123}")
    private String password;

    @Bean
    public Driver neo4jDriver() {
        try {
            Config config = Config.builder()
                    .withConnectionTimeout(10, TimeUnit.SECONDS)
                    .withMaxConnectionPoolSize(20)
                    .build();

            Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
            // 快速验证连接
            driver.verifyConnectivity();
            log.info("Neo4j 连接成功: {}", uri);
            return driver;
        } catch (Exception e) {
            log.warn("Neo4j 连接失败 ({}): {} —— 图谱功能将降级", uri, e.getMessage());
            return null;
        }
    }
}