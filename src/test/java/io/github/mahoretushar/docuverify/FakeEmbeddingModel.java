package io.github.mahoretushar.docuverify;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Deterministic hashed bag-of-words embedding so tests run without Ollama. */
public class FakeEmbeddingModel implements EmbeddingModel {

    private static final int DIM = 512;
    private static final Set<String> STOP = Set.of("the", "a", "an", "of", "to", "be", "is", "are", "and", "as",
            "within", "from", "must", "can", "need", "what", "how", "for", "do", "does", "in", "on", "every", "also");

    int calls;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        calls++;
        List<Embedding> out = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            out.add(new Embedding(vector(inputs.get(i)), i));
        }
        return new EmbeddingResponse(out);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public int dimensions() {
        return DIM;
    }

    static float[] vector(String text) {
        float[] v = new float[DIM];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() > 1 && !STOP.contains(token)) {
                v[Math.floorMod(token.hashCode(), DIM)] += 1f;
            }
        }
        return v;
    }
}
