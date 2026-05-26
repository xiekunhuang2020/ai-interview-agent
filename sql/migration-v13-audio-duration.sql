-- v13: 记录 ASR 语音转写输入音频时长
ALTER TABLE ai_model_call_log
    ADD COLUMN audio_duration_ms BIGINT DEFAULT NULL COMMENT '语音时长毫秒数' AFTER audio_sample_rate;
