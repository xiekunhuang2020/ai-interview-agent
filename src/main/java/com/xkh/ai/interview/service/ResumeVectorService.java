package com.xkh.ai.interview.service;

import com.xkh.ai.interview.config.MilvusCollectionInitializer;
import com.xkh.ai.interview.service.dto.ResumeData;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.MetricType;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ResumeVectorService {

    private static final Logger logger = LoggerFactory.getLogger(ResumeVectorService.class);

    private final MilvusServiceClient milvusClient;
    private final EmbeddingModel embeddingModel;

    public ResumeVectorService(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel) {
        this.milvusClient = milvusClient;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 将简历文本向量化并写入Milvus
     */
    public void upsertResumeVector(String resumeId, String fileName, String resumeText) {
        try {
            List<Float> vector = embedText(resumeText);

            List<InsertParam.Field> fields = Arrays.asList(
                    new InsertParam.Field(MilvusCollectionInitializer.FIELD_RESUME_ID,
                            Collections.singletonList(resumeId)),
                    new InsertParam.Field(MilvusCollectionInitializer.FIELD_VECTOR,
                            Collections.singletonList(vector)),
                    new InsertParam.Field(MilvusCollectionInitializer.FIELD_RESUME_TEXT,
                            Collections.singletonList(truncateText(resumeText, 60000))),
                    new InsertParam.Field(MilvusCollectionInitializer.FIELD_FILE_NAME,
                            Collections.singletonList(fileName != null ? fileName : "")),
                    new InsertParam.Field(MilvusCollectionInitializer.FIELD_CREATE_TIME,
                            Collections.singletonList(System.currentTimeMillis()))
            );

            milvusClient.insert(InsertParam.newBuilder()
                    .withCollectionName(MilvusCollectionInitializer.COLLECTION_NAME)
                    .withFields(fields)
                    .build());

            logger.info("Resume vector upserted successfully, resumeId={}", resumeId);
        } catch (Exception e) {
            logger.error("Failed to upsert resume vector, resumeId={}", resumeId, e);
        }
    }

    /**
     * 根据简历文本检索相似简历
     */
    public List<SimilarResumeResult> searchSimilarResumes(String queryText, int topK) {
        try {
            List<Float> vector = embedText(queryText);
            return doSearch(vector, topK);
        } catch (Exception e) {
            logger.error("Failed to search similar resumes", e);
            return Collections.emptyList();
        }
    }

    /**
     * 根据已有简历ID检索相似简历
     */
    public List<SimilarResumeResult> searchByResumeId(String resumeId, int topK) {
        try {
            ResumeData resumeData = getResumeDataFromMilvus(resumeId);
            if (resumeData == null || resumeData.getResumeText() == null) {
                logger.warn("Resume text not found for resumeId={}", resumeId);
                return Collections.emptyList();
            }
            List<Float> vector = embedText(resumeData.getResumeText());
            return doSearch(vector, topK);
        } catch (Exception e) {
            logger.error("Failed to search by resumeId={}", resumeId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除简历向量
     */
    public void deleteResumeVector(String resumeId) {
        try {
            milvusClient.delete(DeleteParam.newBuilder()
                    .withCollectionName(MilvusCollectionInitializer.COLLECTION_NAME)
                    .withExpr("resume_id == '" + resumeId + "'")
                    .build());
            logger.info("Resume vector deleted, resumeId={}", resumeId);
        } catch (Exception e) {
            logger.error("Failed to delete resume vector, resumeId={}", resumeId, e);
        }
    }

    /**
     * 文本向量化
     */
    private List<Float> embedText(String text) {
        String truncated = truncateText(text, 2000);
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(truncated), null)
        );
        List<Embedding> embeddings = response.getResults();
        if (embeddings.isEmpty()) {
            throw new RuntimeException("Embedding result is empty");
        }
        float[] rawVector = embeddings.get(0).getOutput();
        List<Float> vector = new ArrayList<>(rawVector.length);
        for (float v : rawVector) {
            vector.add(v);
        }
        return vector;
    }

    /**
     * 执行Milvus向量搜索
     */
    private List<SimilarResumeResult> doSearch(List<Float> vector, int topK) {
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(MilvusCollectionInitializer.COLLECTION_NAME)
                .withMetricType(MetricType.COSINE)
                .withTopK(topK)
                .withVectors(Collections.singletonList(vector))
                .withVectorFieldName(MilvusCollectionInitializer.FIELD_VECTOR)
                .withParams("{\"ef\":64}")
                .addOutField(MilvusCollectionInitializer.FIELD_RESUME_ID)
                .addOutField(MilvusCollectionInitializer.FIELD_RESUME_TEXT)
                .addOutField(MilvusCollectionInitializer.FIELD_FILE_NAME)
                .build();

        var searchResults = milvusClient.search(searchParam);
        SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResults.getData().getResults());

        List<SimilarResumeResult> results = new ArrayList<>();
        List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);

        for (int i = 0; i < idScores.size(); i++) {
            SearchResultsWrapper.IDScore idScore = idScores.get(i);
            SimilarResumeResult result = new SimilarResumeResult();
            result.setResumeId(idScore.getStrID());
            result.setFileName((String) idScore.get(MilvusCollectionInitializer.FIELD_FILE_NAME));
            result.setResumeText((String) idScore.get(MilvusCollectionInitializer.FIELD_RESUME_TEXT));
            result.setScore(idScore.getScore());
            results.add(result);
        }
        return results;
    }

    /**
     * 从Milvus查询简历文本（用于相似搜索时的源向量获取）
     */
    private ResumeData getResumeDataFromMilvus(String resumeId) {
        try {
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusCollectionInitializer.COLLECTION_NAME)
                    .withMetricType(MetricType.COSINE)
                    .withTopK(1)
                    .withVectors(Collections.singletonList(Collections.nCopies(MilvusCollectionInitializer.VECTOR_DIM, 0.0f)))
                    .withVectorFieldName(MilvusCollectionInitializer.FIELD_VECTOR)
                    .withExpr("resume_id == '" + resumeId + "'")
                    .addOutField(MilvusCollectionInitializer.FIELD_RESUME_TEXT)
                    .build();

            var searchResults = milvusClient.search(searchParam);
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResults.getData().getResults());
            List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);

            if (idScores.isEmpty()) {
                return null;
            }

            ResumeData data = new ResumeData();
            data.setResumeId(resumeId);
            data.setResumeText((String) idScores.get(0).get(MilvusCollectionInitializer.FIELD_RESUME_TEXT));
            return data;
        } catch (Exception e) {
            logger.error("Failed to get resume data from Milvus, resumeId={}", resumeId, e);
            return null;
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    public static class SimilarResumeResult {
        private String resumeId;
        private String fileName;
        private String resumeText;
        private float score;

        public String getResumeId() { return resumeId; }
        public void setResumeId(String resumeId) { this.resumeId = resumeId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getResumeText() { return resumeText; }
        public void setResumeText(String resumeText) { this.resumeText = resumeText; }
        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }
    }
}
