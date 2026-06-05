package com.xkh.ai.interview.service.rag;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;

public class HybridResumeDocumentRetriever implements DocumentRetriever {

    private static final Logger logger = LoggerFactory.getLogger(HybridResumeDocumentRetriever.class);

    private final MilvusHybridResumeStore hybridResumeStore;
    private final DocumentRetriever fallbackRetriever;
    private final String excludedResumeId;
    private final int topK;

    /**
     * Spring AI RAG 调用的检索器适配层。
     *
     * 它不关心 Prompt，也不关心出题逻辑，只负责一件事：
     * 根据 query 去 Milvus hybrid collection 召回相似简历片段。
     *
     * fallbackRetriever 是原来的 VectorStoreDocumentRetriever。
     * hybrid 有问题时直接回退，保证 RAG 出题主流程不断。
     */
    public HybridResumeDocumentRetriever(MilvusHybridResumeStore hybridResumeStore,
                                         DocumentRetriever fallbackRetriever,
                                         String excludedResumeId,
                                         int topK) {
        this.hybridResumeStore = hybridResumeStore;
        this.fallbackRetriever = fallbackRetriever;
        this.excludedResumeId = excludedResumeId;
        this.topK = topK;
    }

    /**
     * Spring AI RAG 会调用这个方法获取候选 Document。
     *
     * 主线：
     * 1. query 为空：没有可检索内容，直接走原 vector 检索兜底。
     * 2. hybridResumeStore.search：执行 Milvus dense + BM25 sparse 混合检索。
     * 3. 有结果：返回 hybrid 结果，后面会继续进入 Rerank 和 Token 裁剪。
     * 4. 无结果或异常：回退原 vector 检索，避免整个出题流程失败。
     */
    @Override
    public List<Document> retrieve(Query query) {
        if (query == null || StringUtils.isBlank(query.text())) {
            return fallbackRetriever.retrieve(query);
        }
        try {
            List<Document> documents = hybridResumeStore.search(query.text(), excludedResumeId, topK);
            if (!documents.isEmpty()) {
                logger.info("RAG hybrid recall completed, queryLength={}, topK={}, result={}",
                        query.text().length(), topK, documents.size());
                return documents;
            }
            logger.info("RAG hybrid recall empty, fallback to vector recall, topK={}", topK);
            return fallbackRetriever.retrieve(query);
        } catch (Exception ex) {
            logger.warn("RAG hybrid recall failed, fallback to vector recall, reason={}", ex.getMessage());
            return fallbackRetriever.retrieve(query);
        }
    }
}
