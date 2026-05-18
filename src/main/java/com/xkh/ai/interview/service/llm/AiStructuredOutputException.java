package com.xkh.ai.interview.service.llm;

public class AiStructuredOutputException extends RuntimeException {

    public AiStructuredOutputException(String message) {
        super(message);
    }

    public AiStructuredOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
