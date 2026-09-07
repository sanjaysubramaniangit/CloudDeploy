package com.clouddeploy.service.ai;

import com.clouddeploy.exception.TextExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger( TextExtractionService.class);
    private static final int MAX_TEXT_LENGTH = 100000; // 100k chars safety limit

    /**
     * Extracts and normalizes text from a resume input stream.
     *
     * @param inputStream Input stream of the file
     * @param contentType Content type (e.g., application/pdf)
     * @param fileName    Original filename for extension-based fallback
     * @return Cleaned and normalized text
     */
    public String extractText(InputStream inputStream, String contentType, String fileName) {
        if (inputStream == null) {
            throw new TextExtractionException("Input stream cannot be null");
        }

        String lowerName = (fileName != null) ? fileName.toLowerCase() : "";
        String lowerContentType = (contentType != null) ? contentType.toLowerCase() : "";

        try {
            String rawText;
            if (lowerName.endsWith(".pdf") || lowerContentType.contains("pdf")) {
                rawText = extractFromPdf(inputStream);
            } else if (lowerName.endsWith(".docx") || lowerContentType.contains("wordprocessingml") || lowerContentType.contains("docx")) {
                rawText = extractFromDocx(inputStream);
            } else if (lowerName.endsWith(".txt") || lowerContentType.contains("text/plain")) {
                rawText = extractFromTxt(inputStream);
            } else {
                throw new TextExtractionException("Unsupported file type for text extraction: " + fileName + " (" + contentType + ")");
            }

            String normalizedText = normalizeText(rawText);
            if (normalizedText.trim().isEmpty()) {
                throw new TextExtractionException("Extracted document text is empty or contains no readable text");
            }

            if (normalizedText.length() > MAX_TEXT_LENGTH) {
                log.warn("Extracted text exceeds max limit ({} chars), truncating to {} chars", normalizedText.length(), MAX_TEXT_LENGTH);
                normalizedText = normalizedText.substring(0, MAX_TEXT_LENGTH);
            }

            return normalizedText;
        } catch (TextExtractionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to extract text from document {}: {}", fileName, e.getMessage(), e);
            throw new TextExtractionException("Failed to parse and extract text from document: " + e.getMessage(), e);
        }
    }

    private String extractFromPdf(InputStream inputStream) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractFromDocx(InputStream inputStream) throws Exception {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractFromTxt(InputStream inputStream) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        // Normalize line endings
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");

        // Strip non-printable control characters, preserving tab and newline
        normalized = normalized.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");

        // Collapse 3+ consecutive newlines to double newlines
        normalized = normalized.replaceAll("\n{3,}", "\n\n");

        return normalized.trim();
    }
}
