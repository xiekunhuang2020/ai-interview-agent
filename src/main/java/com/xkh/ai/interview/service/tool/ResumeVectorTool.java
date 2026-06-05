package com.xkh.ai.interview.service.tool;

import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import com.xkh.ai.interview.service.rag.MilvusHybridResumeStore;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResumeVectorTool {

    private static final Logger logger = LoggerFactory.getLogger(ResumeVectorTool.class);
    private static final int CHUNK_SIZE = 800;
    private static final int DASHSCOPE_EMBEDDING_BATCH_LIMIT = 10;

    private final VectorStore vectorStore;
    private final MilvusHybridResumeStore hybridResumeStore;
    private final RecursiveCharacterTextSplitter textSplitter;

    /**
     * 简历入库主线：
     * 1. 先把简历文本清洗、切分成 chunk。
     * 2. 默认写入 Spring AI VectorStore，对应原来的 Milvus 向量检索链路。
     * 3. hybrid 模式开启时，再同步写入独立的 Milvus hybrid collection。
     *
     * 这样做是为了保留原向量检索能力，同时让 hybrid 检索可以单独测试和回退。
     */
    public ResumeVectorTool(VectorStore vectorStore,
                            ObjectProvider<MilvusHybridResumeStore> hybridResumeStoreProvider) {
        this.vectorStore = vectorStore;
        this.hybridResumeStore = hybridResumeStoreProvider.getIfAvailable();
        this.textSplitter = new RecursiveCharacterTextSplitter(CHUNK_SIZE);
    }

    /**
     * 上传简历后的入库入口。
     *
     * 主线顺序：
     * 1. cleanResumeText：去掉多余空白，保留换行语义。
     * 2. textSplitter.split：使用 Spring AI Alibaba 的递归切分器生成 chunk。
     * 3. withChunkMetadata：给每个 chunk 标记 resumeId、文件名、chunkIndex、chunkCount。
     * 4. addInBatches：写入默认向量库，保持原来的 vector 检索可用。
     * 5. addHybridStoreIfEnabled：hybrid 模式下额外写入 BM25 + dense 混合检索 collection。
     */
    public int addResume(String resumeId, String fileName, String resumeText) {
        String cleanedText = cleanResumeText(resumeText);
        if (StringUtils.isBlank(cleanedText)) {
            return 0;
        }

        String indexedAt = LocalDateTime.now().toString();
        Document sourceDocument = new Document(cleanedText, buildResumeMetadata(resumeId, fileName, indexedAt));
        List<Document> chunks = withChunkMetadata(textSplitter.split(sourceDocument));
        addInBatches(chunks);
        addHybridStoreIfEnabled(chunks);
        return chunks.size();
    }

    /**
     * 先删除同一 resumeId 的旧向量，再写入新向量，用于固定评测样例回放。
     */
    public int replaceResume(String resumeId, String fileName, String resumeText) {
        deleteResume(resumeId);
        return addResume(resumeId, fileName, resumeText);
    }

    /**
     * 按 resumeId 删除向量片段，避免评测数据重复写入 Milvus。
     */
    public void deleteResume(String resumeId) {
        if (StringUtils.isBlank(resumeId)) {
            return;
        }
        vectorStore.delete(new FilterExpressionBuilder()
                .eq("resumeId", resumeId)
                .build());
        deleteHybridStoreIfEnabled(resumeId);
    }

    /**
     * 基于查询文本从 Milvus 检索相似简历片段，topK 由调用方控制。
     */
    public List<Document> search(String queryText, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(queryText)
                .topK(topK)
                .build());
    }

    /**
     * 给切分后的片段补充 chunkIndex 和 chunkCount，方便后续追踪检索结果来自哪一段简历。
     */
    private List<Document> withChunkMetadata(List<Document> chunks) {
        List<Document> enrichedChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("chunkIndex", i);
            metadata.put("chunkCount", chunks.size());
            enrichedChunks.add(new Document(chunk.getText(), metadata));
        }
        return enrichedChunks;
    }

    /**
     * 按 DashScope embedding 单次最多 10 条文本的限制分批写入，仍复用 Spring AI VectorStore 官方入口。
     */
    private void addInBatches(List<Document> chunks) {
        for (int start = 0; start < chunks.size(); start += DASHSCOPE_EMBEDDING_BATCH_LIMIT) {
            int end = Math.min(start + DASHSCOPE_EMBEDDING_BATCH_LIMIT, chunks.size());
            vectorStore.add(chunks.subList(start, end));
        }
    }

    /**
     * hybrid 模式开启时同步写入 Milvus 官方 BM25 collection。
     *
     * 这里失败不抛出给上传流程，是因为默认向量库已经写入成功；
     * hybrid 是增强链路，不能因为增强链路失败导致用户连基础简历分析都不能用。
     */
    private void addHybridStoreIfEnabled(List<Document> chunks) {
        if (hybridResumeStore == null) {
            return;
        }
        try {
            hybridResumeStore.addResumeChunks(chunks);
        } catch (Exception ex) {
            logger.warn("Hybrid resume store add failed, fallback keeps vector store available, reason={}", ex.getMessage());
        }
    }

    /**
     * hybrid 模式开启时同步删除旧片段。
     *
     * replaceResume 会先 delete 再 add，所以这里用于避免同一 resumeId 重复入库。
     * 如果 hybrid 删除失败，主向量库删除仍然有效，后续检索失败也会回退到 vector 链路。
     */
    private void deleteHybridStoreIfEnabled(String resumeId) {
        if (hybridResumeStore == null) {
            return;
        }
        try {
            hybridResumeStore.deleteResume(resumeId);
        } catch (Exception ex) {
            logger.warn("Hybrid resume store delete failed, fallback keeps vector store available, reason={}", ex.getMessage());
        }
    }

    /**
     * 清洗简历文本中的多余空白，保留换行边界帮助文本切片维持语义连续性。
     */
    private String cleanResumeText(String resumeText) {
        if (resumeText == null) {
            return "";
        }
        List<String> lines = resumeText.replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(line -> line.replaceAll("[\\t ]+", " ").trim())
                .filter(StringUtils::isNotBlank)
                .toList();
        return String.join("\n", lines);
    }

    /**
     * 创建简历向量入库的基础元数据，避免写不可靠的业务 section 硬编码。
     */
    private Map<String, Object> buildResumeMetadata(String resumeId, String fileName, String indexedAt) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("resumeId", StringUtils.defaultString(resumeId));
        metadata.put("fileName", StringUtils.defaultString(fileName));
        metadata.put("indexedAt", indexedAt);
        metadata.put("sourceType", "CURRENT_RESUME_FACT");
        return metadata;
    }
}
