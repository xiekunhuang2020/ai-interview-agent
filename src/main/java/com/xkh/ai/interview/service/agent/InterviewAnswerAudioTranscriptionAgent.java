package com.xkh.ai.interview.service.agent;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.dto.AnswerAudioTranscriptionResultDTO;
import com.xkh.ai.interview.service.audit.AiModelCallAuditRecorder;
import com.xkh.ai.interview.service.llm.AiModelCallException;
import com.xkh.ai.interview.service.llm.PromptVersionRegistry;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class InterviewAnswerAudioTranscriptionAgent {

    public static final String ANSWER_OPERATION_NAME = "answer-audio-transcription";
    public static final String ASSISTANT_OPERATION_NAME = "assistant-audio-transcription";
    private static final int DEFAULT_SAMPLE_RATE = 16000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiModelCallAuditRecorder auditRecorder;
    private final PromptVersionRegistry promptVersionRegistry;
    private final String modelName;
    private final String apiKey;
    private final long maxFileSize;

    /**
     * 注入 DashScope 语音识别配置和审计记录器。
     */
    public InterviewAnswerAudioTranscriptionAgent(
            AiModelCallAuditRecorder auditRecorder,
            PromptVersionRegistry promptVersionRegistry,
            @Value("${ai-interview.audio.answer-transcription.model:paraformer-realtime-v2}") String modelName,
            @Value("${spring.ai.dashscope.api-key:}") String apiKey,
            @Value("${ai-interview.audio.answer-transcription.max-file-size:10485760}") long maxFileSize) {
        this.auditRecorder = auditRecorder;
        this.promptVersionRegistry = promptVersionRegistry;
        this.modelName = modelName;
        this.apiKey = apiKey;
        this.maxFileSize = maxFileSize;
    }

    /**
     * 将浏览器录制的 WAV 语音回答转写成文本，供前端回填到答案框。
     */
    public AnswerAudioTranscriptionResultDTO transcribe(MultipartFile file, Integer sampleRate) throws IOException {
        return transcribe(file, sampleRate, ANSWER_OPERATION_NAME);
    }

    /**
     * 按指定业务场景转写浏览器录制的 WAV 语音，便于审计区分面试回答和 AI 顾问提问。
     */
    public AnswerAudioTranscriptionResultDTO transcribe(MultipartFile file,
                                                        Integer sampleRate,
                                                        String operationName) throws IOException {
        validateAudioFile(file);

        String originalFileName = StringUtils.defaultIfBlank(file.getOriginalFilename(), "answer.wav");
        int normalizedSampleRate = normalizeSampleRate(sampleRate);
        String safeOperationName = StringUtils.defaultIfBlank(operationName, ANSWER_OPERATION_NAME);
        String promptVersion = promptVersionRegistry.versionOf(safeOperationName);
        AiModelCallAuditRecorder.ModelUsage modelUsage =
                new AiModelCallAuditRecorder.ModelUsage(modelName, null, null, null);
        AiModelCallAuditRecorder.AudioUsage audioUsage =
                new AiModelCallAuditRecorder.AudioUsage(file.getSize(), normalizedSampleRate, resolveAudioDurationMs(file));
        long start = System.currentTimeMillis();
        Path tempAudioFile = Files.createTempFile("answer-audio-", ".wav");
        try {
            file.transferTo(tempAudioFile);
            RecognitionParam param = buildRecognitionParam(normalizedSampleRate);
            String rawText = StringUtils.trimToEmpty(new Recognition().call(param, tempAudioFile.toFile()));
            String text = extractFinalSentence(rawText);
            if (StringUtils.isBlank(text)) {
                throw new IllegalStateException("语音转写结果为空，请靠近麦克风重新录制");
            }
            auditRecorder.recordAudio(safeOperationName, promptVersion, true, 1,
                    System.currentTimeMillis() - start, null, modelUsage, audioUsage);
            return new AnswerAudioTranscriptionResultDTO(text, originalFileName, file.getSize(), modelName);
        } catch (RuntimeException e) {
            auditRecorder.recordAudio(safeOperationName, promptVersion, false, 1,
                    System.currentTimeMillis() - start, e, modelUsage, audioUsage);
            throw new AiModelCallException("AI 模型调用失败，operation=" + safeOperationName
                    + "，原因=" + rootCauseMessage(e), e);
        } finally {
            Files.deleteIfExists(tempAudioFile);
        }
    }

    /**
     * 校验语音文件是否为空、是否过大，以及是否为当前前端录制的 WAV 格式。
     */
    private void validateAudioFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("语音文件不能为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("语音文件不能超过 10MB");
        }
        String fileName = StringUtils.defaultString(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".wav")) {
            throw new IllegalArgumentException("当前仅支持浏览器录制的 WAV 语音");
        }
    }

    /**
     * 构造 DashScope 官方 Java SDK 的实时语音识别参数。
     */
    private RecognitionParam buildRecognitionParam(int sampleRate) {
        RecognitionParam.RecognitionParamBuilder<?, ?> builder = RecognitionParam.builder()
                .model(modelName)
                .apiKey(apiKey)
                .format("wav")
                .sampleRate(sampleRate)
                .parameter("language_hints", new String[]{"zh", "en"})
                .parameter("semantic_punctuation_enabled", true)
                .parameter("heartbeat", true);
        return builder.build();
    }

    /**
     * 规范采样率，前端默认下采样到 16000Hz，异常输入时用默认值兜底。
     */
    private int normalizeSampleRate(Integer sampleRate) {
        if (sampleRate == null || sampleRate < 8000 || sampleRate > 48000) {
            return DEFAULT_SAMPLE_RATE;
        }
        return sampleRate;
    }

    /**
     * 使用 JDK 官方音频 API 读取 WAV 时长，读取失败时返回空值，不影响主流程转写。
     */
    private Long resolveAudioDurationMs(MultipartFile file) {
        try (InputStream input = new BufferedInputStream(file.getInputStream())) {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(input);
            AudioFormat format = fileFormat.getFormat();
            int frameLength = fileFormat.getFrameLength();
            float frameRate = format.getFrameRate();
            if (frameLength > 0 && frameRate > 0) {
                return Math.round(frameLength * 1000D / frameRate);
            }
            int frameSize = format.getFrameSize();
            if (frameSize > 0 && frameRate > 0 && file.getSize() > 44) {
                return Math.round((file.getSize() - 44) * 1000D / (frameSize * frameRate));
            }
        } catch (IOException | UnsupportedAudioFileException e) {
            return null;
        }
        return null;
    }

    /**
     * 从 DashScope SDK 返回的句子列表中取最终完整句，避免把中间增量结果回填给用户。
     */
    private String extractFinalSentence(String rawText) {
        if (StringUtils.isBlank(rawText)) {
            return "";
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawText);
            JsonNode sentences = root.path("sentences");
            if (!sentences.isArray()) {
                return rawText;
            }
            String latestText = "";
            for (JsonNode sentence : sentences) {
                String text = StringUtils.trimToEmpty(sentence.path("text").asText());
                if (StringUtils.isNotBlank(text)) {
                    latestText = text;
                }
                if (sentence.path("sentence_end").asBoolean(false) && StringUtils.isNotBlank(text)) {
                    return text;
                }
            }
            return latestText;
        } catch (Exception e) {
            return rawText;
        }
    }

    /**
     * 提取异常链路中最靠近根因的可读错误信息，避免前端只看到 java.lang.Exception。
     */
    private String rootCauseMessage(Throwable error) {
        Throwable current = error;
        String message = null;
        while (current != null) {
            if (StringUtils.isNotBlank(current.getMessage())) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return StringUtils.defaultIfBlank(message, error.getClass().getSimpleName());
    }
}
