package com.xkh.ai.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.service.llm.AiJsonResponseParser;
import com.xkh.ai.interview.service.llm.AiModelFallbackResponseFactory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiModelFallbackResponseFactoryTests {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final AiModelFallbackResponseFactory fallbackFactory = new AiModelFallbackResponseFactory();
    private final AiJsonResponseParser parser = new AiJsonResponseParser(new ObjectMapper(), VALIDATOR);

    @Test
    void resumeAnalysisFallbackMatchesStructuredOutputContract() throws Exception {
        String fallback = fallbackFactory.fallbackFor("resume-analysis").orElseThrow();

        assertTrue(parser.parseResumeScoreResult(fallback).getSummary().contains("降级结果"));
    }

    @Test
    void interviewQuestionFallbackMatchesStructuredOutputContract() throws Exception {
        String fallback = fallbackFactory.fallbackFor("interview-question-generation").orElseThrow();

        assertTrue(parser.parseInterviewQuestions(fallback).getQuestions().isEmpty());
    }
}
