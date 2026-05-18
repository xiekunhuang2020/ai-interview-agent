package com.xkh.ai.interview.service.tool;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ResumeParseTool {

    public boolean supports(String fileName) {
        if (fileName == null) {
            return false;
        }
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".pdf")
                || normalized.endsWith(".doc")
                || normalized.endsWith(".docx")
                || normalized.endsWith(".txt");
    }

    public String parse(MultipartFile file) throws IOException {
        TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
        List<Document> documents = reader.read();
        return documents.stream()
                .map(Document::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n\n"))
                .trim();
    }
}
