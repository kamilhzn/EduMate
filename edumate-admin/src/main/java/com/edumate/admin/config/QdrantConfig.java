package com.edumate.admin.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutionException;

/**
 * Qdrant 向量存储配置 —— 手动创建 EmbeddingStore Bean，启动时自动创建 Collection
 * <p>
 * 使用 gRPC 协议连接 Qdrant（默认端口 6334）。
 */
@Configuration
public class QdrantConfig {

    private static final Logger log = LoggerFactory.getLogger(QdrantConfig.class);

    @Value("${qdrant.host:localhost}")
    private String host;

    @Value("${qdrant.grpc-port:6334}")
    private int grpcPort;

    @Value("${qdrant.collection-name:edumate-docs}")
    private String collectionName;

    /**
     * 应用启动时确保 Qdrant Collection 存在
     */
    @PostConstruct
    void ensureCollectionExists() {
        QdrantClient client = new QdrantClient(QdrantGrpcClient.newBuilder(host, grpcPort, false).build());
        try {
            boolean exists = client.collectionExistsAsync(collectionName).get();
            if (!exists) {
                client.createCollectionAsync(
                        collectionName,
                        VectorParams.newBuilder()
                                .setDistance(Distance.Cosine)
                                .setSize(1024)  // text-embedding-v3 维度
                                .build()
                ).get();
                log.info("Qdrant Collection '{}' 创建成功 (维度=1024, 距离=Cosine)", collectionName);
            } else {
                log.info("Qdrant Collection '{}' 已存在，跳过创建", collectionName);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("Qdrant Collection 检查/创建失败: {}", e.getMessage());
        } finally {
            client.close();
        }
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return QdrantEmbeddingStore.builder()
                .host(host)
                .port(grpcPort)
                .collectionName(collectionName)
                .build();
    }
}