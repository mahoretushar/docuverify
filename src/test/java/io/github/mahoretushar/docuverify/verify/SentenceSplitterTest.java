package io.github.mahoretushar.docuverify.verify;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentenceSplitterTest {

    @Test
    void abbreviationsDoNotEndSentences() {
        var parts = GroundingVerifier.splitSentences(
                "Claims above Rs. 50,000 need approval from Dr. Rao [1]. Receipts must be PDFs [2].");
        assertThat(parts).containsExactly(
                "Claims above Rs. 50,000 need approval from Dr. Rao [1].",
                "Receipts must be PDFs [2].");
    }

    @Test
    void citationAfterTheFullStopStaysWithItsSentence() {
        var parts = GroundingVerifier.splitSentences("Claims above Rs. 25,000 need approval. [5] Laptops last 4 years. [1][2]");
        assertThat(parts).containsExactly("Claims above Rs. 25,000 need approval [5].", "Laptops last 4 years [1][2].");
    }

    @Test
    void unclearJudgeRepliesNeverCountAsSupport() {
        assertThat(LlmNliJudge.parse("Entailment.")).isEqualTo(NliJudge.Label.ENTAILMENT);
        assertThat(LlmNliJudge.parse("CONTRADICTION")).isEqualTo(NliJudge.Label.CONTRADICTION);
        assertThat(LlmNliJudge.parse("I am not sure")).isEqualTo(NliJudge.Label.NEUTRAL);
        assertThat(LlmNliJudge.parse(null)).isEqualTo(NliJudge.Label.NEUTRAL);
    }
}
