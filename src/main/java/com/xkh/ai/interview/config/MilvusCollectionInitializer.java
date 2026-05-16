package com.xkh.ai.interview.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MilvusCollectionInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MilvusCollectionInitializer.class);

    public static final String COLLECTION_NAME = "resume_vector";
    public static final String FIELD_RESUME_ID = "resume_id";
    public static final String FIELD_VECTOR = "vector";
    public static final String FIELD_RESUME_TEXT = "resume_text";
    public static final String FIELD_FILE_NAME = "file_name";
    public static final String FIELD_CREATE_TIME = "create_time";
    public static final int VECTOR_DIM = 1536;

    private final MilvusServiceClient milvusClient;

    public MilvusCollectionInitializer(MilvusServiceClient milvusClient) {
        this.milvusClient = milvusClient;
    }

    @Override
    public void run(String... args) {
        try {
            boolean hasCollection = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .build()
            ).getData();

            if (hasCollection) {
                logger.info("Milvus collection '{}' already exists", COLLECTION_NAME);
                return;
            }

            FieldType resumeIdField = FieldType.newBuilder()
                    .withName(FIELD_RESUME_ID)
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .withPrimaryKey(true)
                    .withAutoID(false)
                    .build();

            FieldType vectorField = FieldType.newBuilder()
                    .withName(FIELD_VECTOR)
                    .withDataType(DataType.FloatVector)
                    .withDimension(VECTOR_DIM)
                    .build();

            FieldType resumeTextField = FieldType.newBuilder()
                    .withName(FIELD_RESUME_TEXT)
                    .withDataType(DataType.VarChar)
                    .withMaxLength(65535)
                    .build();

            FieldType fileNameField = FieldType.newBuilder()
                    .withName(FIELD_FILE_NAME)
                    .withDataType(DataType.VarChar)
                    .withMaxLength(255)
                    .build();

            FieldType createTimeField = FieldType.newBuilder()
                    .withName(FIELD_CREATE_TIME)
                    .withDataType(DataType.Int64)
                    .build();

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withDescription("Resume vector store for RAG retrieval")
                    .addFieldType(resumeIdField)
                    .addFieldType(vectorField)
                    .addFieldType(resumeTextField)
                    .addFieldType(fileNameField)
                    .addFieldType(createTimeField)
                    .build();

            milvusClient.createCollection(createParam);
            logger.info("Milvus collection '{}' created successfully", COLLECTION_NAME);

            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFieldName(FIELD_VECTOR)
                    .withIndexType(IndexType.HNSW)
                    .withMetricType(MetricType.COSINE)
                    .withExtraParam("{\"M\":16,\"efConstruction\":64}")
                    .withSyncMode(Boolean.TRUE)
                    .build();

            milvusClient.createIndex(indexParam);
            logger.info("Milvus index created for collection '{}'", COLLECTION_NAME);

            milvusClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .build()
            );
            logger.info("Milvus collection '{}' loaded into memory", COLLECTION_NAME);

        } catch (Exception e) {
            logger.error("Failed to initialize Milvus collection", e);
        }
    }
}
