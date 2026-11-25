package io.github.mahoretushar.docuverify.qa;

import io.github.mahoretushar.docuverify.config.DocuverifyProperties;
import io.github.mahoretushar.docuverify.verify.GroundingVerifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Retrieve, generate with numbered citations, then verify the answer against the sources. */
@Service
public class AnswerService {

    static final String NOT_FOUND = "I could not find this in the uploaded documents.";

    private static final String SYSTEM_PROMPT = """
            You answer questions using ONLY the numbered sources provided.
            Cite every factual sentence with its source number in square brackets, e.g. [1].
            If the sources do not contain the answer, reply exactly: %s
            Keep the answer under 120 words.""".formatted(NOT_FOUND);

    public record Citation(int number, String source, Object chunk, double score, String excerpt) {
    }

    public record Answer(String question, String answer, List<Citation> citations,
                         GroundingVerifier.Report verification) {
    }

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final GroundingVerifier verifier;
    private final DocuverifyProperties.Retrieval retrieval;

    public AnswerService(ChatClient chatClient, VectorStore vectorStore, GroundingVerifier verifier,
                         DocuverifyProperties properties) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.verifier = verifier;
        this.retrieval = properties.retrieval();
    }

    public Answer ask(String owner, String question, Integer topK) {
        int k = topK == null ? retrieval.topK() : topK;
        List<Document> sources = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(k)
                .similarityThreshold(retrieval.similarityThreshold())
                .filterExpression("owner == '" + owner.replace("'", "") + "'")   // tenant isolation
                .build());

        if (sources.isEmpty()) {
            // Refuse instead of letting the model guess: no LLM call when nothing relevant was retrieved.
            return new Answer(question, NOT_FOUND, List.of(), new GroundingVerifier.Report(0.0, false, List.of()));
        }

        String context = IntStream.range(0, sources.size())
                .mapToObj(i -> "[" + (i + 1) + "] " + sources.get(i).getText())
                .collect(Collectors.joining("\n\n"));

        String text = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(u -> u.text("Sources:\n{context}\n\nQuestion: {question}\n\n"
                                + "Cite only source numbers from 1 to {count}, the numbers in square brackets above.")
                        .param("context", context)
                        .param("question", question)
                        .param("count", String.valueOf(sources.size())))
                .call()
                .content();

        String answer = text == null ? "" : text.strip();
        List<Citation> citations = IntStream.range(0, sources.size())
                .mapToObj(i -> toCitation(i + 1, sources.get(i)))
                .toList();

        if (answer.equals(NOT_FOUND)) {
            return new Answer(question, answer, citations, new GroundingVerifier.Report(0.0, false, List.of()));
        }
        return new Answer(question, answer, citations, verifier.verify(answer, sources));
    }

    private static Citation toCitation(int n, Document d) {
        String text = d.getText() == null ? "" : d.getText();
        String excerpt = text.length() > 200 ? text.substring(0, 200) + "..." : text;
        double score = d.getScore() == null ? 0.0 : d.getScore();
        return new Citation(n, String.valueOf(d.getMetadata().get("source")), d.getMetadata().get("chunk"),
                Math.round(score * 1000) / 1000.0, excerpt);
    }
}
