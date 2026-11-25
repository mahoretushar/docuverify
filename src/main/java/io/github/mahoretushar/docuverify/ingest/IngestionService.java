package io.github.mahoretushar.docuverify.ingest;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Reads a PDF/Markdown/text file, splits it into token-sized chunks and stores their embeddings. */
@Service
public class IngestionService {

    public record IngestResult(String documentId, String source, int chunks) {
    }

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;

    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(400)
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(10)
                .withKeepSeparator(true)
                .build();
    }

    public IngestResult ingest(String owner, String filename, byte[] content) {
        if (content.length == 0) {
            throw new IllegalArgumentException("File is empty");
        }
        String documentId = UUID.randomUUID().toString();
        Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        List<Document> pages = read(filename, resource);
        List<Document> chunks = splitter.apply(pages);
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().put("owner", owner);
            chunks.get(i).getMetadata().put("documentId", documentId);
            chunks.get(i).getMetadata().put("source", filename);
            chunks.get(i).getMetadata().put("chunk", i);
        }
        vectorStore.add(chunks);
        return new IngestResult(documentId, filename, chunks.size());
    }

    private static List<Document> read(String filename, Resource resource) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return new PagePdfDocumentReader(resource).get();
        }
        if (name.endsWith(".txt") || name.endsWith(".md")) {
            return new TextReader(resource).get();
        }
        throw new IllegalArgumentException("Unsupported file type: " + filename + " (use .pdf, .md or .txt)");
    }
}
