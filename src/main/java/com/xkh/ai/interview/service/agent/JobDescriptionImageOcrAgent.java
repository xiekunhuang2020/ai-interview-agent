package com.xkh.ai.interview.service.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.chat.MessageFormat;
import com.alibaba.cloud.ai.dashscope.common.DashScopeApiConstants;
import com.xkh.ai.interview.service.llm.AiModelCallService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class JobDescriptionImageOcrAgent {

    private static final String OPERATION_NAME = "jd-image-ocr";
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    private final AiModelCallService aiModelCallService;

    @Value("${ai-interview.vision.jd-ocr-model:qwen-vl-max}")
    private String jdOcrModel;

    /**
     * 注入统一模型调用服务，岗位截图识别仍复用审计、Prompt 版本和 Token 观测链路。
     */
    public JobDescriptionImageOcrAgent(AiModelCallService aiModelCallService) {
        this.aiModelCallService = aiModelCallService;
    }

    /**
     * 使用 Spring AI 官方多模态 Message 能力，从岗位截图中提取 JD 文本。
     */
    public String extractJobDescription(MultipartFile file) throws IOException {
        validateImage(file);
        String originalFileName = StringUtils.defaultIfBlank(file.getOriginalFilename(), "jd-image");
        MimeType mimeType = resolveMimeType(file);
        Resource imageResource = buildImageResource(file, originalFileName);
        Message userMessage = UserMessage.builder()
                .text(buildPrompt())
                .metadata(Map.of(DashScopeApiConstants.MESSAGE_FORMAT, MessageFormat.IMAGE))
                .media(Media.builder()
                        .mimeType(mimeType)
                        .data(imageResource)
                        .name(originalFileName)
                        .build())
                .build();

        String text = aiModelCallService.call(OPERATION_NAME, List.of(userMessage), DashScopeChatOptions.builder()
                .model(StringUtils.defaultIfBlank(jdOcrModel, "qwen-vl-max"))
                .temperature(0.1D)
                .maxToken(1800)
                .enableThinking(false)
                .multiModel(true)
                .vlHighResolutionImages(true)
                .build());
        if (StringUtils.isBlank(text)) {
            throw new IllegalStateException("未能从截图中识别到岗位说明，请换一张更清晰的截图");
        }
        return text.trim();
    }

    /**
     * 校验截图是否为空，以及格式是否属于当前视觉模型支持的常见图片类型。
     */
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择岗位截图");
        }
        String contentType = StringUtils.defaultString(file.getContentType()).toLowerCase(Locale.ROOT);
        String extension = extensionOf(file.getOriginalFilename());
        if (!SUPPORTED_IMAGE_TYPES.contains(contentType) && !SUPPORTED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 PNG、JPG、JPEG 或 WEBP 格式的岗位截图");
        }
    }

    /**
     * 根据上传文件的 Content-Type 或文件后缀确定图片 MIME 类型。
     */
    private MimeType resolveMimeType(MultipartFile file) {
        String contentType = StringUtils.defaultString(file.getContentType()).toLowerCase(Locale.ROOT);
        if (SUPPORTED_IMAGE_TYPES.contains(contentType)) {
            return MimeTypeUtils.parseMimeType(contentType);
        }
        String extension = extensionOf(file.getOriginalFilename());
        if ("png".equals(extension)) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        if ("webp".equals(extension)) {
            return MimeTypeUtils.parseMimeType("image/webp");
        }
        return MimeTypeUtils.IMAGE_JPEG;
    }

    /**
     * 将 MultipartFile 转成 Spring Resource，让 Spring AI Media 直接携带原始图片内容。
     */
    private Resource buildImageResource(MultipartFile file, String originalFileName) throws IOException {
        byte[] bytes = file.getBytes();
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return originalFileName;
            }
        };
    }

    /**
     * 构造视觉模型提示词，要求只返回可粘贴到岗位说明框的中文 JD 文本。
     */
    private String buildPrompt() {
        return """
                请从这张招聘岗位截图中提取岗位说明文本。

                要求：
                1. 只保留岗位名称、岗位职责、任职要求、加分项、薪资地点等与 JD 相关的信息。
                2. 去掉截图里的时间、电量、按钮、导航栏、广告、聊天入口等界面噪音。
                3. 如果截图中有英文技术名词，请保留原文。
                4. 按原有语义整理成清晰的中文文本，方便后续直接做岗位匹配。
                5. 只输出识别后的岗位说明，不要输出解释、代码块或前后缀。
                """;
    }

    /**
     * 提取文件后缀，便于在 Content-Type 缺失时判断图片格式。
     */
    private String extensionOf(String fileName) {
        String safeName = StringUtils.defaultString(fileName);
        int dotIndex = safeName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == safeName.length() - 1) {
            return "";
        }
        return safeName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
