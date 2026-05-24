package com.xkh.ai.interview.service.rag;

import com.xkh.ai.interview.service.llm.PromptContextBudgetService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResumeRagAdvisorFactory {

    private final VectorStore vectorStore;
    private final PromptContextBudgetService contextBudgetService;

    /**
     * 注入 Spring AI VectorStore，后续由官方 RAG Advisor 完成检索增强。
     */
    public ResumeRagAdvisorFactory(VectorStore vectorStore,
                                   PromptContextBudgetService contextBudgetService) {
        this.vectorStore = vectorStore;
        this.contextBudgetService = contextBudgetService;
    }

    /**
     * 创建面向岗位定制出题的 RAG Advisor，限制召回当前简历之外的参考片段。
     */
    public Advisor createAdvisor(String excludedResumeId, int topK, String retrievalQuery) {
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(createRetrievalQueryTransformer(retrievalQuery))
                .documentRetriever(createDocumentRetriever(excludedResumeId, topK))
                .documentPostProcessors((query, documents) -> contextBudgetService.limitRagDocuments(documents))
                .queryAugmenter(createQueryAugmenter())
                .build();
    }

    /**
     * 将 RAG 检索 query 固定为岗位 JD，避免直接拿完整 prompt 做向量检索。
     */
    QueryTransformer createRetrievalQueryTransformer(String retrievalQuery) {
        return query -> new Query(
                StringUtils.defaultIfBlank(retrievalQuery, query.text()),
                query.history(),
                query.context()
        );
    }

    /**
     * 创建向量库检索器，并排除当前候选人的简历片段。
     */
    DocumentRetriever createDocumentRetriever(String excludedResumeId, int topK) {
        VectorStoreDocumentRetriever.Builder builder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(normalizeTopK(topK));

        if (StringUtils.isNotBlank(excludedResumeId)) {
            builder.filterExpression(new FilterExpressionBuilder()
                    .ne("resumeId", excludedResumeId)
                    .build());
        }

        return builder.build();
    }

    /**
     * 创建上下文增强器，将相似简历片段注入到用户问题中。
     */
    private ContextualQueryAugmenter createQueryAugmenter() {
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .promptTemplate(new PromptTemplate("""
                        {query}

                        ## RAG 检索上下文
                        以下内容来自向量检索，仅用于参考同类简历的项目深度、技能证据和追问方向。
                        不得把这些参考内容当成当前候选人的真实经历。
                        如果基于这些片段设计问题，输出 evidenceSource 必须标为 SIMILAR_RESUME_REFERENCE。

                        {context}
                        """))
                .documentFormatter(this::formatDocuments)
                .build();
    }

    /**
     * 格式化检索到的 Document，给模型明确标注参考片段来源和引用类型。
     */
    private String formatDocuments(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            Object resumeId = document.getMetadata().get("resumeId");
            Object fileName = document.getMetadata().get("fileName");
            Object chunkIndex = document.getMetadata().get("chunkIndex");
            Object chunkCount = document.getMetadata().get("chunkCount");
            context.append("参考片段 ").append(i + 1).append("：\n");
            context.append("referenceType: SIMILAR_RESUME_REFERENCE\n");
            context.append("referenceName: 相似简历参考\n");
            if (resumeId != null) {
                context.append("resumeId: ").append(resumeId).append("\n");
            }
            if (fileName != null) {
                context.append("fileName: ").append(fileName).append("\n");
            }
            if (chunkIndex != null && chunkCount != null) {
                context.append("chunk: ").append(chunkIndex).append("/").append(chunkCount).append("\n");
            }
            context.append(document.getText()).append("\n\n");
        }
        return context.toString();
    }

    /**
     * 限制 RAG topK 范围，防止请求过大导致上下文膨胀。
     */
    private int normalizeTopK(int topK) {
        return Math.max(1, Math.min(topK, 20));
    }
}
