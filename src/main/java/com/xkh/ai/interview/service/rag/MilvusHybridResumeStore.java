package com.xkh.ai.interview.service.rag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusServiceClientProperties;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MilvusHybridResumeStore {

    private static final Logger logger = LoggerFactory.getLogger(MilvusHybridResumeStore.class);
    private static final int DASHSCOPE_EMBEDDING_BATCH_LIMIT = 10;
    private static final int MAX_TEXT_LENGTH = 8192;
    private static final String FIELD_ID = "id";
    private static final String FIELD_RESUME_ID = "resumeId";
    private static final String FIELD_FILE_NAME = "fileName";
    private static final String FIELD_CHUNK_INDEX = "chunkIndex";
    private static final String FIELD_CHUNK_COUNT = "chunkCount";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_DENSE_VECTOR = "denseVector";
    private static final String FIELD_SPARSE_VECTOR = "sparseVector";

    private final EmbeddingModel embeddingModel;
    private final MilvusServiceClientProperties clientProperties;
    private final MilvusVectorStoreProperties vectorStoreProperties;
    private final HybridRagProperties ragProperties;
    private final AtomicBoolean collectionReady = new AtomicBoolean(false);

    private MilvusClientV2 client;

    /**
     * Milvus hybrid collection 的底层访问类。
     *
     * 这类代码看起来多，是因为它在做 Spring AI VectorStore 当前没有直接暴露的事情：
     * 1. 创建一个支持 BM25 Function 的 Milvus collection。
     * 2. 入库时写入 dense 向量和原始文本。
     * 3. 由 Milvus 根据原始文本自动生成 sparse 向量。
     * 4. 查询时同时跑 dense 向量检索和 BM25 sparse 检索。
     * 5. 用 Milvus WeightedRanker 融合两路召回结果。
     */
    public MilvusHybridResumeStore(EmbeddingModel embeddingModel,
                                   MilvusServiceClientProperties clientProperties,
                                   MilvusVectorStoreProperties vectorStoreProperties,
                                   HybridRagProperties ragProperties) {
        this.embeddingModel = embeddingModel;
        this.clientProperties = clientProperties;
        this.vectorStoreProperties = vectorStoreProperties;
        this.ragProperties = ragProperties;
    }

    /**
     * 写入 hybrid collection 的入口。
     *
     * 主线：
     * 1. ensureCollection：确认 hybrid collection 存在，不存在就按官方 schema 创建。
     * 2. embeddingModel.embed：为每个 chunk 生成 dense 向量。
     * 3. insertBatch：写入 id、resumeId、chunk 元数据、文本和 denseVector。
     * 4. sparseVector 不在代码里手动生成，而是 Milvus BM25 Function 根据 text 字段自动生成。
     */
    public void addResumeChunks(List<Document> chunks) {
        if (!ragProperties.isHybridWriteEnabled() || chunks == null || chunks.isEmpty()) {
            return;
        }
        ensureCollection();

        for (int start = 0; start < chunks.size(); start += DASHSCOPE_EMBEDDING_BATCH_LIMIT) {
            int end = Math.min(start + DASHSCOPE_EMBEDDING_BATCH_LIMIT, chunks.size());
            List<Document> batch = chunks.subList(start, end);
            List<float[]> embeddings = embeddingModel.embed(batch.stream()
                    .map(Document::getText)
                    .toList());
            insertBatch(batch, embeddings);
        }
    }

    /**
     * 按 resumeId 删除 hybrid collection 中的旧片段。
     */
    public void deleteResume(String resumeId) {
        if (!ragProperties.isHybridWriteEnabled() || StringUtils.isBlank(resumeId)) {
            return;
        }
        ensureCollection();
        milvusClient().delete(DeleteReq.builder()
                .collectionName(collectionName())
                .filter(FIELD_RESUME_ID + " == \"" + escapeExprValue(resumeId) + "\"")
                .build());
    }

    /**
     * hybrid 检索入口。
     *
     * 主线：
     * 1. denseSearch：把 query 生成 embedding，查 denseVector，擅长语义相似。
     * 2. sparseSearch：用 EmbeddedText(query) 查 sparseVector，擅长关键词、技术名词、版本号。
     * 3. hybridSearch：把两路检索请求一次提交给 Milvus。
     * 4. WeightedRanker：按 denseWeight / sparseWeight 融合两路结果。
     * 5. toDocuments：把 Milvus 结果转成 Spring AI Document，交给后续 Rerank。
     */
    public List<Document> search(String queryText, String excludedResumeId, int topK) {
        if (!ragProperties.isHybridSearchEnabled() || StringUtils.isBlank(queryText)) {
            return List.of();
        }
        ensureCollection();

        HybridRagProperties.Hybrid hybrid = ragProperties.getHybrid();
        String expr = excludedResumeFilter(excludedResumeId);
        // dense 路径：语义相似召回，例如“高并发优化”和“性能调优”这种表达不完全相同但语义接近的内容。
        AnnSearchReq denseSearch = AnnSearchReq.builder()
                .vectorFieldName(FIELD_DENSE_VECTOR)
                .vectors(List.of(new FloatVec(embeddingModel.embed(queryText))))
                .topK(normalizeTopK(hybrid.getDenseTopK()))
                .expr(expr)
                .metricType(IndexParam.MetricType.COSINE)
                .params("{\"ef\":64}")
                .build();
        // sparse 路径：BM25 关键词召回，例如 Java 21、Milvus、WebSocket、错误码、框架名这类精确词。
        AnnSearchReq sparseSearch = AnnSearchReq.builder()
                .vectorFieldName(FIELD_SPARSE_VECTOR)
                .vectors(List.of(new EmbeddedText(queryText)))
                .topK(normalizeTopK(hybrid.getSparseTopK()))
                .expr(expr)
                .metricType(IndexParam.MetricType.BM25)
                .build();

        // 两路召回一起提交给 Milvus，由官方 WeightedRanker 做融合排序，不在业务代码里手写 BM25 分数。
        SearchResp response = milvusClient().hybridSearch(HybridSearchReq.builder()
                .databaseName(databaseName())
                .collectionName(collectionName())
                .searchRequests(List.of(denseSearch, sparseSearch))
                .ranker(new WeightedRanker(List.of(hybrid.getDenseWeight(), hybrid.getSparseWeight())))
                .topK(normalizeTopK(Math.min(topK, hybrid.getFinalTopK())))
                .outFields(List.of(FIELD_RESUME_ID, FIELD_FILE_NAME, FIELD_CHUNK_INDEX, FIELD_CHUNK_COUNT, FIELD_TEXT))
                .build());

        return toDocuments(response);
    }

    /**
     * 应用关闭时释放 Milvus V2 客户端连接。
     */
    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    /**
     * 懒初始化 collection，避免应用启动时因为 Milvus 不可用直接失败。
     */
    private void ensureCollection() {
        if (collectionReady.get()) {
            return;
        }
        synchronized (collectionReady) {
            if (collectionReady.get()) {
                return;
            }
            Boolean exists = milvusClient().hasCollection(HasCollectionReq.builder()
                    .collectionName(collectionName())
                    .build());
            if (!Boolean.TRUE.equals(exists)) {
                if (!ragProperties.getHybrid().isInitializeSchema()) {
                    throw new IllegalStateException("Milvus hybrid collection 不存在：" + collectionName());
                }
                createHybridCollection();
            }
            loadCollection();
            collectionReady.set(true);
        }
    }

    /**
     * 创建带 dense vector、BM25 sparse vector 和文本 analyzer 的 hybrid collection。
     *
     * 字段含义：
     * - text：原始 chunk 文本，启用 analyzer 后才能用于 BM25。
     * - denseVector：DashScope embedding 生成的语义向量。
     * - sparseVector：Milvus BM25 Function 根据 text 自动生成的稀疏向量。
     *
     * 注意：这里没有自己实现 BM25，也没有自己维护倒排索引，这些都交给 Milvus。
     */
    private void createHybridCollection() {
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .enableDynamicField(false)
                .fieldSchemaList(List.of(
                        CreateCollectionReq.FieldSchema.builder()
                                .name(FIELD_ID)
                                .dataType(DataType.VarChar)
                                .maxLength(128)
                                .isPrimaryKey(true)
                                .autoID(false)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name(FIELD_RESUME_ID)
                                .dataType(DataType.VarChar)
                                .maxLength(64)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name(FIELD_FILE_NAME)
                                .dataType(DataType.VarChar)
                                .maxLength(512)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name(FIELD_CHUNK_INDEX)
                                .dataType(DataType.Int32)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name(FIELD_CHUNK_COUNT)
                                .dataType(DataType.Int32)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name(FIELD_TEXT)
                                .dataType(DataType.VarChar)
                                .maxLength(MAX_TEXT_LENGTH)
                                .enableAnalyzer(true)
                                .enableMatch(true)
                                .analyzerParams(Map.of("type", ragProperties.getHybrid().getAnalyzerType()))
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name(FIELD_DENSE_VECTOR)
                                .dataType(DataType.FloatVector)
                                .dimension(vectorStoreProperties.getEmbeddingDimension())
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name(FIELD_SPARSE_VECTOR)
                                .dataType(DataType.SparseFloatVector)
                                .build()
                ))
                .functionList(List.of(CreateCollectionReq.Function.builder()
                        .name("text_bm25_function")
                        .functionType(FunctionType.BM25)
                        .inputFieldNames(List.of(FIELD_TEXT))
                        .outputFieldNames(List.of(FIELD_SPARSE_VECTOR))
                        .build()))
                .build();

        milvusClient().createCollection(CreateCollectionReq.builder()
                .databaseName(databaseName())
                .collectionName(collectionName())
                .collectionSchema(schema)
                .indexParams(List.of(
                        IndexParam.builder()
                                .fieldName(FIELD_DENSE_VECTOR)
                                .indexName("dense_vector_idx")
                                .indexType(IndexParam.IndexType.HNSW)
                                .metricType(IndexParam.MetricType.COSINE)
                                .extraParams(Map.of("M", 16, "efConstruction", 200))
                                .build(),
                        IndexParam.builder()
                                .fieldName(FIELD_SPARSE_VECTOR)
                                .indexName("sparse_bm25_idx")
                                .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                                .metricType(IndexParam.MetricType.BM25)
                                .build()
                ))
                .build());
        logger.info("Milvus hybrid collection created, collection={}", collectionName());
    }

    /**
     * 将当前 collection 加载到 Milvus query node，已加载时忽略重复加载异常。
     */
    private void loadCollection() {
        try {
            milvusClient().loadCollection(LoadCollectionReq.builder()
                    .collectionName(collectionName())
                    .sync(true)
                    .build());
        } catch (Exception ex) {
            logger.debug("Milvus hybrid collection load skipped, collection={}, reason={}",
                    collectionName(), ex.getMessage());
        }
    }

    /**
     * 将一批 chunk 和对应 dense embedding 写入 Milvus。
     *
     * 这里每条 row 不写 sparseVector，是因为 collection schema 里配置了 BM25 Function：
     * 只要写入 text 字段，Milvus 会在内部生成 sparseVector。
     */
    private void insertBatch(List<Document> batch, List<float[]> embeddings) {
        List<JsonObject> rows = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            rows.add(toRow(batch.get(i), embeddings.get(i)));
        }
        milvusClient().insert(InsertReq.builder()
                .collectionName(collectionName())
                .data(rows)
                .build());
    }

    /**
     * 将 Spring AI Document 转换为 Milvus insert row。
     */
    private JsonObject toRow(Document document, float[] embedding) {
        Map<String, Object> metadata = document.getMetadata();
        String resumeId = StringUtils.defaultString(asString(metadata.get(FIELD_RESUME_ID)));
        int chunkIndex = asInt(metadata.get(FIELD_CHUNK_INDEX), 0);
        int chunkCount = asInt(metadata.get(FIELD_CHUNK_COUNT), 1);
        String text = StringUtils.abbreviate(StringUtils.defaultString(document.getText()), MAX_TEXT_LENGTH);

        JsonObject row = new JsonObject();
        row.addProperty(FIELD_ID, buildPrimaryKey(resumeId, chunkIndex));
        row.addProperty(FIELD_RESUME_ID, resumeId);
        row.addProperty(FIELD_FILE_NAME, StringUtils.defaultString(asString(metadata.get(FIELD_FILE_NAME))));
        row.addProperty(FIELD_CHUNK_INDEX, chunkIndex);
        row.addProperty(FIELD_CHUNK_COUNT, chunkCount);
        row.addProperty(FIELD_TEXT, text);
        row.add(FIELD_DENSE_VECTOR, toJsonArray(embedding));
        return row;
    }

    /**
     * 将 Milvus hybrid search 结果转回 Spring AI Document。
     */
    private List<Document> toDocuments(SearchResp response) {
        if (response == null || response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
            return List.of();
        }
        return response.getSearchResults().get(0).stream()
                .map(this::toDocument)
                .toList();
    }

    /**
     * 转换单条 Milvus SearchResult，同时保留 hybridScore 方便后续 Rerank 观察。
     */
    private Document toDocument(SearchResp.SearchResult result) {
        Map<String, Object> entity = result.getEntity() == null ? Map.of() : result.getEntity();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(FIELD_RESUME_ID, entity.get(FIELD_RESUME_ID));
        metadata.put(FIELD_FILE_NAME, entity.get(FIELD_FILE_NAME));
        metadata.put(FIELD_CHUNK_INDEX, entity.get(FIELD_CHUNK_INDEX));
        metadata.put(FIELD_CHUNK_COUNT, entity.get(FIELD_CHUNK_COUNT));
        metadata.put("searchMode", "hybrid");
        metadata.put("hybridRecallScore", result.getScore());
        String text = StringUtils.defaultString(asString(entity.get(FIELD_TEXT)));

        return new Document(StringUtils.defaultString(asString(result.getId()), UUID.randomUUID().toString()), text, metadata)
                .mutate()
                .score(result.getScore() == null ? 0D : result.getScore().doubleValue())
                .build();
    }

    /**
     * 复用 Spring AI Milvus 连接参数创建 V2 SDK 客户端。
     */
    private MilvusClientV2 milvusClient() {
        if (client == null) {
            String uri = StringUtils.defaultIfBlank(clientProperties.getUri(),
                    (clientProperties.isSecure() ? "https://" : "http://")
                            + clientProperties.getHost() + ":" + clientProperties.getPort());
            ConnectConfig.ConnectConfigBuilder<?, ?> builder = ConnectConfig.builder()
                    .uri(uri)
                    .dbName(databaseName())
                    .secure(clientProperties.isSecure());
            if (StringUtils.isNotBlank(clientProperties.getToken())) {
                builder.token(clientProperties.getToken());
            }
            if (StringUtils.isNotBlank(clientProperties.getUsername())) {
                builder.username(clientProperties.getUsername());
            }
            if (StringUtils.isNotBlank(clientProperties.getPassword())) {
                builder.password(clientProperties.getPassword());
            }
            if (clientProperties.getConnectTimeoutMs() > 0) {
                builder.connectTimeoutMs(clientProperties.getConnectTimeoutMs());
            }
            if (clientProperties.getRpcDeadlineMs() > 0) {
                builder.rpcDeadlineMs(clientProperties.getRpcDeadlineMs());
            }
            client = new MilvusClientV2(builder.build());
        }
        return client;
    }

    /**
     * 构建排除当前简历的 Milvus filter，避免相似简历参考召回当前候选人自己。
     */
    private String excludedResumeFilter(String excludedResumeId) {
        if (StringUtils.isBlank(excludedResumeId)) {
            return "";
        }
        return FIELD_RESUME_ID + " != \"" + escapeExprValue(excludedResumeId) + "\"";
    }

    /**
     * 构造确定性主键，重复导入前先 delete，避免同一简历 chunk 重复写入。
     */
    private String buildPrimaryKey(String resumeId, int chunkIndex) {
        if (StringUtils.isBlank(resumeId)) {
            return UUID.randomUUID().toString();
        }
        return resumeId + "_" + chunkIndex;
    }

    /**
     * 将 float[] 转成 Milvus insert 需要的 JSON 数组。
     */
    private JsonArray toJsonArray(float[] values) {
        JsonArray array = new JsonArray();
        if (values == null) {
            return array;
        }
        for (float value : values) {
            array.add(value);
        }
        return array;
    }

    /**
     * 规整 topK 范围，防止一次召回过多内容进入后续 Rerank。
     */
    private int normalizeTopK(int topK) {
        return Math.max(1, Math.min(topK, 50));
    }

    /**
     * 从 Milvus entity 或 Document metadata 中安全读取字符串。
     */
    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 从 metadata 中读取整数，兼容 Milvus 返回的不同 Number 类型。
     */
    private int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    /**
     * 转义 Milvus filter 中的双引号和反斜杠。
     */
    private String escapeExprValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String collectionName() {
        return ragProperties.getHybrid().getCollectionName();
    }

    private String databaseName() {
        return StringUtils.defaultIfBlank(vectorStoreProperties.getDatabaseName(), "default");
    }
}
