package com.xkh.ai.interview.support;

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

    private static final int MAX_CONTEXT_CHARS_PER_RESUME = 1200;

    private final VectorStore vectorStore;

    public ResumeRagAdvisorFactory(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public Advisor createAdvisor(String excludedResumeId, int topK, String retrievalQuery) {
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(createRetrievalQueryTransformer(retrievalQuery))
                .documentRetriever(createDocumentRetriever(excludedResumeId, topK))
                .queryAugmenter(createQueryAugmenter())
                .build();
    }

    QueryTransformer createRetrievalQueryTransformer(String retrievalQuery) {
        return query -> new Query(
                StringUtils.defaultIfBlank(retrievalQuery, query.text()),
                query.history(),
                query.context()
        );
    }

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

    private ContextualQueryAugmenter createQueryAugmenter() {
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .promptTemplate(new PromptTemplate("""
                        {query}

                        ## RAG 检索上下文
                        以下内容来自向量检索，仅用于参考同类简历的项目深度、技能证据和追问方向。
                        不得把这些参考内容当成当前候选人的真实经历。

                        {context}
                        """))
                .documentFormatter(this::formatDocuments)
                .build();
    }

    private String formatDocuments(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            Object resumeId = document.getMetadata().get("resumeId");
            Object fileName = document.getMetadata().get("fileName");
            context.append("参考片段 ").append(i + 1).append("：\n");
            if (resumeId != null) {
                context.append("resumeId: ").append(resumeId).append("\n");
            }
            if (fileName != null) {
                context.append("fileName: ").append(fileName).append("\n");
            }
            context.append(truncate(document.getText())).append("\n\n");
        }
        return context.toString();
    }

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_CONTEXT_CHARS_PER_RESUME) {
            return text == null ? "" : text;
        }
        return text.substring(0, MAX_CONTEXT_CHARS_PER_RESUME) + "\n...[truncated]";
    }

    private int normalizeTopK(int topK) {
        return Math.max(1, Math.min(topK, 20));
    }
}
