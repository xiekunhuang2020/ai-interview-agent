package com.xkh.ai.interview.service.tool;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ResumeVectorTool {

    private final VectorStore vectorStore;

    public ResumeVectorTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void addResume(String resumeId, String fileName, String resumeText) {
        Document doc = new Document(resumeText, Map.of("resumeId", resumeId, "fileName", fileName));
        vectorStore.add(List.of(doc));
    }

    public List<Document> search(String queryText, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(queryText)
                .topK(topK)
                .build());
    }
}
