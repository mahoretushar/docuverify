package io.github.mahoretushar.docuverify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docuverify")
public record DocuverifyProperties(Retrieval retrieval, Verification verification) {

    /** How many chunks to fetch and the minimum vector similarity for a chunk to count. */
    public record Retrieval(int topK, double similarityThreshold) {
    }

    /**
     * supportThreshold: cosine similarity a sentence needs with some chunk before it is even considered.
     * minGroundedness: share of supported sentences below which the answer is flagged for review.
     * nliEnabled: run the entailment judge on sentences that pass the similarity stage.
     */
    public record Verification(double supportThreshold, double minGroundedness, boolean nliEnabled) {
    }
}
