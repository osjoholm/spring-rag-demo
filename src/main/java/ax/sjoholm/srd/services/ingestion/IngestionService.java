package ax.sjoholm.srd.services.ingestion;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import ax.sjoholm.srd.configuration.ApplicationConfiguration.MyTokenTextSplitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final DocumentReader reader;
    private final MyTokenTextSplitter splitter;
    private final DocumentProcessor processor;
    private final VectorStore vectorStore;

    public record IngestionReport(
            int docsRead,
            int docsKept,
            int chunksProduced,
            int chunksKept,
            long chunksLt50,
            long chunksLt200,
            long chunksLt500) {
    }

    public IngestionReport ingestLagtingetDocuments() {
        log.info("Starting ingestion…");

        List<Document> docs = reader.get();
        log.info("Extracted {} documents", docs.size());

        List<Document> processedDocs = processor.process(docs);
        log.info("Processed {} documents", processedDocs.size());

        List<Document> chunks = splitter.splitCustomized(processedDocs);
        log.info("Split into {} chunks", chunks.size());

        long lt50 = chunks.stream().filter(d -> d.getText().length() < 50).count();
        long lt200 = chunks.stream().filter(d -> d.getText().length() < 200).count();
        long lt500 = chunks.stream().filter(d -> d.getText().length() < 500).count();

        vectorStore.add(chunks);
        log.info("Ingestion done. keptDocs={} keptChunks={}", processedDocs.size(), chunks.size());

        return new IngestionReport(
                docs.size(),
                processedDocs.size(),
                chunks.size(),
                chunks.size(),
                lt50, lt200, lt500);
    }
}