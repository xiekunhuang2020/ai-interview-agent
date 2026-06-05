package com.xkh.ai.interview.service.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai-interview.rag")
public class HybridRagProperties {

    /**
     * RAG 检索模式：vector 使用原向量检索，hybrid 使用 Milvus dense + BM25 sparse 混合检索。
     */
    private String searchMode = "vector";
    /**
     * Milvus 官方 hybrid search 的配置。
     */
    private Hybrid hybrid = new Hybrid();

    /**
     * 判断当前是否启用 hybrid 检索链路。
     */
    public boolean isHybridSearchEnabled() {
        return "hybrid".equalsIgnoreCase(searchMode);
    }

    /**
     * 判断当前是否需要同步写入 hybrid collection。
     */
    public boolean isHybridWriteEnabled() {
        return isHybridSearchEnabled() && hybrid.isSyncWrites();
    }

    @Data
    public static class Hybrid {

        /**
         * 独立 hybrid collection 名称，避免影响 Spring AI VectorStore 默认 collection。
         */
        private String collectionName = "resume_hybrid_store";
        /**
         * 是否在写入向量库时同步写入 hybrid collection。
         */
        private boolean syncWrites = true;
        /**
         * collection 不存在时是否自动创建 schema。
         */
        private boolean initializeSchema = true;
        /**
         * 文本字段使用的 Milvus analyzer 类型，中文简历默认使用 chinese。
         */
        private String analyzerType = "chinese";
        /**
         * dense 向量召回候选数量。
         */
        private int denseTopK = 20;
        /**
         * BM25 sparse 召回候选数量。
         */
        private int sparseTopK = 20;
        /**
         * Milvus hybrid ranker 融合后的最终返回数量。
         */
        private int finalTopK = 12;
        /**
         * dense 向量结果在 WeightedRanker 中的权重。
         */
        private float denseWeight = 0.55F;
        /**
         * sparse BM25 结果在 WeightedRanker 中的权重。
         */
        private float sparseWeight = 0.45F;
    }
}
