package com.xkh.ai.interview.service.tool;

import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResumeVectorTool {

    private static final int CHUNK_SIZE = 800;

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
    public void addResume(String resumeId, String fileName, String resumeText) {
        Document sourceDocument = new Document(resumeText, Map.of("resumeId", resumeId, "fileName", fileName));
        List<Document> chunks = withChunkMetadata(textSplitter.split(sourceDocument));
        vectorStore.add(chunks);
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
}
