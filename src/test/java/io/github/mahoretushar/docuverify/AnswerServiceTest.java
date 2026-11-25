package io.github.mahoretushar.docuverify;

import io.github.mahoretushar.docuverify.config.DocuverifyProperties;
import io.github.mahoretushar.docuverify.ingest.IngestionService;
import io.github.mahoretushar.docuverify.qa.AnswerService;
import io.github.mahoretushar.docuverify.verify.EmbeddingCache;
import io.github.mahoretushar.docuverify.verify.GroundingVerifier;
import io.github.mahoretushar.docuverify.verify.NliJudge;
import io.github.mahoretushar.docuverify.verify.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerServiceTest {

    private static final String POLICY = """
            Travel Reimbursement Policy.
            Employees can claim travel reimbursement within 30 days of the trip.
            Claims above 50,000 rupees require approval from the finance head.
            Original receipts must be uploaded as PDF files on the expense portal.
            """;

    /** Test judge: treats a changed number as a contradiction, like a real NLI model would. */
    static final NliJudge NUMBER_AWARE_JUDGE = (evidence, claim) -> {
        for (String token : claim.split("[^0-9,]+")) {
            if (!token.isBlank() && !evidence.contains(token)) {
                return NliJudge.Label.CONTRADICTION;
            }
        }
        return NliJudge.Label.ENTAILMENT;
    };

    StubChatModel chat;
    FakeEmbeddingModel embeddings;
    SimpleVectorStore store;
    IngestionService ingestion;

    @BeforeEach
    void setUp() {
        embeddings = new FakeEmbeddingModel();
        store = SimpleVectorStore.builder(embeddings).build();
        chat = new StubChatModel();
        ingestion = new IngestionService(store);
        ingestion.ingest("asha", "travel-policy.md", POLICY.getBytes(StandardCharsets.UTF_8));
    }

    AnswerService service(boolean nli) {
        var props = new DocuverifyProperties(
                new DocuverifyProperties.Retrieval(3, 0.2),
                new DocuverifyProperties.Verification(0.5, 0.8, nli));
        return new AnswerService(ChatClient.builder(chat).build(), store,
                new GroundingVerifier(new EmbeddingCache(embeddings), NUMBER_AWARE_JUDGE, props), props);
    }

    @Test
    void groundedAnswerIsReturnedWithCitationsAndPassesVerification() {
        chat.reply = "Employees can claim travel reimbursement within 30 days of the trip [1].";

        var answer = service(true).ask("asha", "Within how many days can employees claim travel reimbursement?", null);

        assertThat(answer.citations().getFirst().source()).isEqualTo("travel-policy.md");
        assertThat(answer.verification().groundedness()).isEqualTo(1.0);
        assertThat(answer.verification().needsReview()).isFalse();
        assertThat(chat.lastPrompt.getContents()).contains("[1] ").contains("30 days");
    }

    @Test
    void inventedSentenceIsUnsupported() {
        chat.reply = "Employees can claim travel reimbursement within 30 days of the trip [1]. "
                + "The company provides free lunch to staff on Fridays [1].";

        var report = service(true).ask("asha", "How long do employees have to claim travel reimbursement?", null)
                .verification();

        assertThat(report.sentences()).extracting(GroundingVerifier.SentenceCheck::verdict)
                .containsExactly(Verdict.SUPPORTED, Verdict.UNSUPPORTED);
        assertThat(report.groundedness()).isEqualTo(0.5);
        assertThat(report.needsReview()).isTrue();
    }

    @Test
    void changedNumberLooksSimilarButIsCaughtByTheNliStage() {
        chat.reply = "Employees can claim travel reimbursement within 60 days of the trip [1].";
        String q = "travel reimbursement claim deadline";

        var withoutNli = service(false).ask("asha", q, null).verification().sentences().getFirst();
        var withNli = service(true).ask("asha", q, null).verification().sentences().getFirst();

        assertThat(withoutNli.verdict()).as("similarity alone is fooled").isEqualTo(Verdict.SUPPORTED);
        assertThat(withNli.verdict()).isEqualTo(Verdict.CONTRADICTED);
    }

    @Test
    void citationToASourceThatWasNotRetrievedIsInvalid() {
        chat.reply = "Employees can claim travel reimbursement within 30 days of the trip [7].";

        var check = service(true).ask("asha", "travel reimbursement claim deadline", null)
                .verification().sentences().getFirst();

        assertThat(check.verdict()).isEqualTo(Verdict.INVALID_CITATION);
        assertThat(check.invalidCitations()).containsExactly(7);
    }

    @Test
    void usersOnlySearchTheirOwnDocuments() {
        chat.reply = "should never be used";

        var answer = service(true).ask("ravi", "travel reimbursement claim deadline", null);

        assertThat(answer.answer()).isEqualTo("I could not find this in the uploaded documents.");
        assertThat(chat.calls).isZero();
    }

    @Test
    void unrelatedQuestionIsRefusedWithoutCallingTheModel() {
        var answer = service(true).ask("asha", "Explain quantum chromodynamics and gluon confinement", null);

        assertThat(answer.citations()).isEmpty();
        assertThat(chat.calls).isZero();
    }

    @Test
    void chunkEmbeddingsAreComputedOnceAcrossQuestions() {
        var svc = service(true);
        chat.reply = "Employees can claim travel reimbursement within 30 days of the trip [1].";
        svc.ask("asha", "travel reimbursement claim deadline", null);
        int afterFirst = embeddings.calls;
        svc.ask("asha", "travel reimbursement claim deadline", null);
        int secondQuestionCalls = embeddings.calls - afterFirst;

        // second question: 1 call for the query + 1 for the answer sentence, none for the cached chunk
        assertThat(secondQuestionCalls).isEqualTo(2);
    }

    @Test
    void unsupportedFileTypeIsRejected() {
        assertThatThrownBy(() -> ingestion.ingest("asha", "photo.png", new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file type");
    }
}
