package ax.sjoholm.srd.services.ingestion;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Processes documents after ingestion by applying normalizations and enrichments.
 * This processor prepares documents for optimal embedding and retrieval.
 */
@Component
public class DocumentProcessor {

    private static final int MIN_CONTENT_LENGTH = 50;
    private static final int MAX_METADATA_PREVIEW_LENGTH = 100;

    /**
     * Processes a list of documents by normalizing text and enriching metadata.
     *
     * @param documents the documents to process
     * @return processed documents ready for embedding
     */
    public List<Document> process(List<Document> documents) {
        return documents.stream()
                .map(this::processDocument)
                .filter(this::hasValidContent)
                .collect(Collectors.toList());
    }

    private Document processDocument(Document document) {
        String normalizedText = normalizeText(document.getText());
        Map<String, Object> enrichedMetadata = enrichMetadata(document.getMetadata(), normalizedText);
        return new Document(normalizedText, enrichedMetadata);
    }

    private String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text;

        // Normalize line endings
        normalized = normalized.replace("\r\n", "\n").replace('\r', '\n');

        // Remove excessive whitespace while preserving paragraph structure
        normalized = normalized.replaceAll("[\\t ]{2,}", " ");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");

        // Apply heading normalization for legal documents
        normalized = HeadingNormalizer.normalizeHeadings(normalized);

        return normalized.trim();
    }

    private Map<String, Object> enrichMetadata(Map<String, Object> metadata, String text) {
        java.util.Map<String, Object> enriched = new java.util.HashMap<>(metadata);

        // Add content statistics
        enriched.put("char_count", text.length());
        enriched.put("word_count", countWords(text));

        // Add content preview for debugging
        if (text.length() > MAX_METADATA_PREVIEW_LENGTH) {
            enriched.put("preview", text.substring(0, MAX_METADATA_PREVIEW_LENGTH) + "...");
        } else {
            enriched.put("preview", text);
        }

        // Add processing timestamp
        enriched.put("processed_at", java.time.Instant.now().toString());

        return enriched;
    }

    private boolean hasValidContent(Document document) {
        String text = document.getText();
        return text != null && text.length() >= MIN_CONTENT_LENGTH;
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.split("\\s+").length;
    }
}
