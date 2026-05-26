-- v12: 记录 ASR 语音转写的输入音频元信息
ALTER TABLE ai_model_call_log
    ADD COLUMN audio_file_size_bytes BIGINT DEFAULT NULL COMMENT '语音文件大小字节数' AFTER total_tokens,
    ADD COLUMN audio_sample_rate INT DEFAULT NULL COMMENT '语音采样率' AFTER audio_file_size_bytes;
