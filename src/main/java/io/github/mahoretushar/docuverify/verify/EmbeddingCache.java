package io.github.mahoretushar.docuverify.verify;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Chunks are re-retrieved for many questions; their embeddings never change, so compute each once. */
@Component
public class EmbeddingCache {

    private static final int MAX_ENTRIES = 10_000;

    private final EmbeddingModel model;
    private final Map<String, float[]> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                    return size() > MAX_ENTRIES;   // LRU eviction
                }
            });

    public EmbeddingCache(EmbeddingModel model) {
        this.model = model;
    }

    public float[] of(Document chunk) {
        return cache.computeIfAbsent(chunk.getId(), id -> model.embed(chunk.getText()));
    }

    public float[] of(String text) {
        return model.embed(text);
    }

    int size() {
        return cache.size();
    }
}
