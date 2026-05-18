package com.xkh.ai.interview.service.rag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class ResumeRagAdvisorFactoryTests {

    @Test
    void retrieverPushesTopKAndCurrentResumeFilterToVectorStore() {
        Document document = new Document("matched resume");
        CapturingVectorStore vectorStore = new CapturingVectorStore(List.of(document));

        ResumeRagAdvisorFactory factory = new ResumeRagAdvisorFactory(vectorStore);
        DocumentRetriever retriever = factory.createDocumentRetriever("resume-1", 50);

        List<Document> documents = retriever.retrieve(new Query("java backend jd"));

        SearchRequest request = vectorStore.lastRequest;
        assertEquals("java backend jd", request.getQuery());
        assertEquals(20, request.getTopK());
        assertEquals(Filter.ExpressionType.NE, request.getFilterExpression().type());
        assertSame(document, documents.get(0));
    }

    @Test
    void retrieverSkipsFilterWhenResumeIdIsBlank() {
        CapturingVectorStore vectorStore = new CapturingVectorStore(List.of());

        ResumeRagAdvisorFactory factory = new ResumeRagAdvisorFactory(vectorStore);
        DocumentRetriever retriever = factory.createDocumentRetriever(" ", 3);

        retriever.retrieve(new Query("java backend jd"));

        SearchRequest request = vectorStore.lastRequest;
        assertEquals(3, request.getTopK());
        assertFalse(request.hasFilterExpression());
    }

    @Test
    void queryTransformerUsesJobDescriptionForRetrieval() {
        ResumeRagAdvisorFactory factory = new ResumeRagAdvisorFactory(new CapturingVectorStore(List.of()));
        QueryTransformer transformer = factory.createRetrievalQueryTransformer("需要 Java、Redis、AI Agent 经验");

        Query transformed = transformer.transform(new Query("候选人完整简历和 JD 混合提示词"));

        assertEquals("需要 Java、Redis、AI Agent 经验", transformed.text());
    }

    @Test
    void queryTransformerFallsBackToOriginalQueryWhenJobDescriptionIsBlank() {
        ResumeRagAdvisorFactory factory = new ResumeRagAdvisorFactory(new CapturingVectorStore(List.of()));
        QueryTransformer transformer = factory.createRetrievalQueryTransformer(" ");

        Query transformed = transformer.transform(new Query("候选人完整简历和 JD 混合提示词"));

        assertEquals("候选人完整简历和 JD 混合提示词", transformed.text());
    }

    private static class CapturingVectorStore implements VectorStore {

        private final List<Document> documents;
        private SearchRequest lastRequest;

        private CapturingVectorStore(List<Document> documents) {
            this.documents = documents;
        }

        @Override
        public void add(List<Document> documents) {
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            this.lastRequest = request;
            return documents;
        }
    }
}
