package com.xkh.ai.interview.service.rag;

import com.alibaba.cloud.ai.document.DocumentWithScore;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class ResumeRagAdvisorFactory {

    private static final Logger logger = LoggerFactory.getLogger(ResumeRagAdvisorFactory.class);
    private static final double RERANK_SCORE_WEIGHT = 0.7D;
    private static final double VECTOR_SCORE_WEIGHT = 0.2D;
    private static final double METADATA_BOOST_WEIGHT = 0.1D;

    private final VectorStore vectorStore;
    private final PromptContextBudgetService contextBudgetService;
    private final RerankModel rerankModel;
    private final MilvusHybridResumeStore hybridResumeStore;
    private final HybridRagProperties ragProperties;

    @Value("${ai-interview.rag.rerank.enabled:true}")
    private boolean rerankEnabled;
    @Value("${ai-interview.rag.rerank.top-n:6}")
    private int rerankTopN;
    @Value("${spring.ai.dashscope.rerank.options.model:qwen3-rerank}")
    private String rerankModelName;

    /**
     * 岗位出题 RAG 的装配类。
     *
     * 阅读主线从 createAdvisor 开始：
     * 1. createRetrievalQueryTransformer：决定“拿什么文本去检索”，当前固定优先使用 JD。
     * 2. createDocumentRetriever：决定“用哪条检索链路”，hybrid 或 vector。
     * 3. rerankAndLimitDocuments：召回后做 Rerank，再做上下文预算裁剪。
     * 4. createQueryAugmenter：把最终片段塞回 Prompt，并强调来源边界。
     */
    public ResumeRagAdvisorFactory(VectorStore vectorStore,
                                   PromptContextBudgetService contextBudgetService,
                                   ObjectProvider<RerankModel> rerankModelProvider,
                                   MilvusHybridResumeStore hybridResumeStore,
                                   HybridRagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.contextBudgetService = contextBudgetService;
        this.rerankModel = rerankModelProvider.getIfAvailable();
        this.hybridResumeStore = hybridResumeStore;
        this.ragProperties = ragProperties;
    }

    /**
     * 创建岗位定制出题用的 Spring AI RAG Advisor。
     *
     * 这个方法是检索增强的总入口：
     * - 检索前：把检索 query 固定为岗位 JD，避免完整 Prompt 里的 JSON 约束干扰召回。
     * - 检索中：按配置选择 hybrid 检索或原 vector 检索。
     * - 检索后：Rerank 精排，再按 Token 预算裁剪。
     * - 注入 Prompt：把相似简历片段标成参考资料，不允许当成当前候选人的真实经历。
     */
    public Advisor createAdvisor(String excludedResumeId, int topK, String retrievalQuery) {
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(createRetrievalQueryTransformer(retrievalQuery))
                .documentRetriever(createDocumentRetriever(excludedResumeId, topK))
                .documentPostProcessors(this::rerankAndLimitDocuments)
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
     * 根据配置选择召回链路。
     *
     * 默认现在是 hybrid：
     * - 先走 Milvus dense + BM25 sparse 混合检索。
     * - 如果 hybrid collection 不可用、没有结果或查询失败，再回退到原 vector 检索。
     *
     * 这样你本地能直接测 hybrid，同时线上仍有兜底，不会因为实验链路失败导致出题失败。
     */
    DocumentRetriever createDocumentRetriever(String excludedResumeId, int topK) {
        DocumentRetriever vectorRetriever = createVectorDocumentRetriever(excludedResumeId, topK);
        if (ragProperties.isHybridSearchEnabled()) {
            return new HybridResumeDocumentRetriever(
                    hybridResumeStore,
                    vectorRetriever,
                    excludedResumeId,
                    normalizeTopK(topK));
        }
        return vectorRetriever;
    }

    /**
     * 创建 Spring AI 官方向量检索器，并排除当前候选人的简历片段。
     */
    private DocumentRetriever createVectorDocumentRetriever(String excludedResumeId, int topK) {
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
            appendScoreLine(context, document);
            context.append(document.getText()).append("\n\n");
        }
        return context.toString();
    }

    /**
     * 使用 DashScopeRerankModel 对向量召回结果二次精排，再进入上下文预算裁剪。
     */
    private List<Document> rerankAndLimitDocuments(Query query, List<Document> documents) {
        if (!shouldRerank(query, documents)) {
            return contextBudgetService.limitRagDocuments(documents);
        }

        try {
            RerankRequest request = new RerankRequest(query.text(), documents, DashScopeRerankOptions.builder()
                    .model(rerankModelName)
                    .topN(normalizeTopK(Math.min(rerankTopN, documents.size())))
                    .returnDocuments(true)
                    .build());
            RerankResponse response = rerankModel.call(request);
            List<Document> rerankedDocuments = toHybridScoredDocuments(response);
            if (rerankedDocuments.isEmpty()) {
                return contextBudgetService.limitRagDocuments(documents);
            }
            logger.info("RAG rerank completed, model={}, original={}, reranked={}",
                    rerankModelName, documents.size(), rerankedDocuments.size());
            return contextBudgetService.limitRagDocuments(rerankedDocuments);
        } catch (Exception ex) {
            logger.warn("RAG rerank failed, fallback to vector recall, model={}, reason={}",
                    rerankModelName, ex.getMessage());
            return contextBudgetService.limitRagDocuments(documents);
        }
    }

    /**
     * 判断当前召回结果是否需要进入 Rerank，避免空 query 或单条文档浪费一次模型调用。
     */
    private boolean shouldRerank(Query query, List<Document> documents) {
        return rerankEnabled
                && rerankModel != null
                && query != null
                && StringUtils.isNotBlank(query.text())
                && documents != null
                && documents.size() > 1;
    }

    /**
     * 将 Rerank 分、向量分和元数据权重合成为最终排序分。
     */
    private List<Document> toHybridScoredDocuments(RerankResponse response) {
        if (response == null || response.getResults() == null) {
            return List.of();
        }
        return response.getResults().stream()
                .filter(result -> result != null && result.getOutput() != null)
                .map(this::toHybridScoredDocument)
                .sorted(Comparator.comparingDouble(this::scoreOf).reversed())
                .toList();
    }

    /**
     * 将单个 Rerank 结果转换为带可追踪打分元数据的 Document。
     */
    private Document toHybridScoredDocument(DocumentWithScore result) {
        Document document = result.getOutput();
        double rerankScore = normalizeScore(result.getScore(), 0D);
        double vectorScore = normalizeScore(document.getScore(), rerankScore);
        double metadataBoost = metadataBoostOf(document);
        double hybridScore = RERANK_SCORE_WEIGHT * rerankScore
                + VECTOR_SCORE_WEIGHT * vectorScore
                + METADATA_BOOST_WEIGHT * metadataBoost;

        return document.mutate()
                .score(hybridScore)
                .metadata("rerankScore", roundScore(rerankScore))
                .metadata("vectorScore", roundScore(vectorScore))
                .metadata("metadataBoost", roundScore(metadataBoost))
                .metadata("hybridScore", roundScore(hybridScore))
                .build();
    }

    /**
     * 根据段落类型做轻量业务加权，岗位出题更关注项目、技能和工作经历片段。
     */
    private double metadataBoostOf(Document document) {
        Object section = document.getMetadata().get("section");
        String sectionCode = section == null ? "" : section.toString();
        if (StringUtils.equalsAny(sectionCode, "project_experience", "skills")) {
            return 1D;
        }
        if (StringUtils.equals(sectionCode, "work_experience")) {
            return 0.9D;
        }
        return 0.7D;
    }

    /**
     * 读取 Document 当前排序分，空值时按 0 处理。
     */
    private double scoreOf(Document document) {
        return normalizeScore(document.getScore(), 0D);
    }

    /**
     * 将模型分数归一到 0-1 区间，异常值直接使用兜底分。
     */
    private double normalizeScore(Double score, double defaultScore) {
        if (score == null || score.isNaN() || score.isInfinite()) {
            return defaultScore;
        }
        return Math.max(0D, Math.min(score, 1D));
    }

    /**
     * 保留四位小数，方便日志和 Prompt 中查看 Rerank 效果。
     */
    private double roundScore(double score) {
        return Math.round(score * 10000D) / 10000D;
    }

    /**
     * 在 RAG 上下文中展示排序分，方便判断最终进入 Prompt 的片段来源。
     */
    private void appendScoreLine(StringBuilder context, Document document) {
        Object hybridScore = document.getMetadata().get("hybridScore");
        if (hybridScore == null) {
            return;
        }
        context.append("score: hybrid=").append(formatScore(hybridScore))
                .append(", rerank=").append(formatScore(document.getMetadata().get("rerankScore")))
                .append(", vector=").append(formatScore(document.getMetadata().get("vectorScore")))
                .append(", metadata=").append(formatScore(document.getMetadata().get("metadataBoost")))
                .append("\n");
    }

    /**
     * 格式化打分，避免 Prompt 中出现过长小数。
     */
    private String formatScore(Object score) {
        if (score instanceof Number number) {
            return String.format(Locale.ROOT, "%.4f", number.doubleValue());
        }
        return "--";
    }

    /**
     * 限制 RAG topK 范围，防止请求过大导致上下文膨胀。
     */
    private int normalizeTopK(int topK) {
        return Math.max(1, Math.min(topK, 20));
    }
}
