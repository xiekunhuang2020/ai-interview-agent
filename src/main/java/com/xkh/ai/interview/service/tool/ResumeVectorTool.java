package com.xkh.ai.interview.service.tool;

import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResumeVectorTool {

    private static final int CHUNK_SIZE = 800;
    private static final int DASHSCOPE_EMBEDDING_BATCH_LIMIT = 10;

    private final VectorStore vectorStore;
    private final RecursiveCharacterTextSplitter textSplitter;

    /**
     * 注入 Spring AI 向量库，并使用 Spring AI Alibaba 提供的递归文本切分器处理简历入库。
     */
    public ResumeVectorTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.textSplitter = new RecursiveCharacterTextSplitter(CHUNK_SIZE);
    }

    /**
     * 将简历文本切分成多个语义片段后写入 Milvus，避免整份简历作为单个向量导致召回粒度过粗。
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
        return chunks.size();
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
