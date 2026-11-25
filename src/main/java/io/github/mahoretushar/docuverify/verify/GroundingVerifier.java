package io.github.mahoretushar.docuverify.verify;

import io.github.mahoretushar.docuverify.config.DocuverifyProperties;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two-stage hallucination check for every answer sentence.
 * Stage 1 (cheap): embedding similarity finds the closest retrieved chunk; too far means UNSUPPORTED.
 * Stage 2 (precise): an NLI judge compares the sentence with that chunk and any chunk it cites, which catches
 * sentences that look similar but say the opposite ("within 30 days" vs "not within 30 days").
 */
@Component
public class GroundingVerifier {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");
    private static final Pattern ABBREVIATION = Pattern.compile(
            "\\b(Rs|Dr|Mr|Mrs|Ms|Prof|No|Sr|Jr|Inc|Ltd|vs|etc|e\\.g|i\\.e)\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_CITATION = Pattern.compile("([.!?])\\s*((?:\\[\\d+])+)");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z\\[])");
    private static final String DOT = "․";   // placeholder so abbreviations do not end sentences

    public record SentenceCheck(String sentence, Verdict verdict, boolean supported, double bestScore,
                                Integer bestSource, List<Integer> invalidCitations) {
    }

    public record Report(double groundedness, boolean needsReview, List<SentenceCheck> sentences) {
    }

    private final EmbeddingCache embeddings;
    private final NliJudge judge;
    private final DocuverifyProperties.Verification settings;

    public GroundingVerifier(EmbeddingCache embeddings, NliJudge judge, DocuverifyProperties properties) {
        this.embeddings = embeddings;
        this.judge = judge;
        this.settings = properties.verification();
    }

    public Report verify(String answer, List<Document> sources) {
        return verify(answer, sources, settings.nliEnabled());
    }

    public Report verify(String answer, List<Document> sources, boolean useNli) {
        List<String> sentences = splitSentences(answer);
        if (sentences.isEmpty() || sources.isEmpty()) {
            return new Report(0.0, true, List.of());
        }
        List<float[]> sourceVectors = sources.stream().map(embeddings::of).toList();

        List<SentenceCheck> checks = new ArrayList<>();
        int supported = 0;
        for (String sentence : sentences) {
            List<Integer> invalid = new ArrayList<>();
            Set<Integer> cited = citations(sentence, sources.size(), invalid);
            String claim = CITATION.matcher(sentence).replaceAll("").strip();

            float[] v = embeddings.of(claim);
            double best = -1;
            int bestIdx = 0;
            for (int i = 0; i < sourceVectors.size(); i++) {
                double s = cosine(v, sourceVectors.get(i));
                if (s > best) {
                    best = s;
                    bestIdx = i;
                }
            }

            Verdict verdict;
            if (!invalid.isEmpty()) {
                verdict = Verdict.INVALID_CITATION;
            } else if (best < settings.supportThreshold()) {
                verdict = Verdict.UNSUPPORTED;
            } else if (!useNli) {
                verdict = Verdict.SUPPORTED;
            } else {
                Set<Integer> evidenceIdx = new LinkedHashSet<>();
                evidenceIdx.add(bestIdx);
                cited.forEach(n -> evidenceIdx.add(n - 1));
                StringBuilder evidence = new StringBuilder();
                evidenceIdx.forEach(i -> evidence.append(sources.get(i).getText()).append("\n"));
                verdict = switch (judge.judge(evidence.toString(), claim)) {
                    case ENTAILMENT -> Verdict.SUPPORTED;
                    case CONTRADICTION -> Verdict.CONTRADICTED;
                    case NEUTRAL -> Verdict.UNSUPPORTED;
                };
            }
            boolean ok = verdict == Verdict.SUPPORTED;
            if (ok) {
                supported++;
            }
            checks.add(new SentenceCheck(sentence, verdict, ok, round(best), bestIdx + 1, invalid));
        }
        double groundedness = round((double) supported / sentences.size());
        return new Report(groundedness, groundedness < settings.minGroundedness(), checks);
    }

    static List<String> splitSentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String protectedText = ABBREVIATION.matcher(text.strip()).replaceAll(m -> m.group(1) + DOT);
        // "... approval. [5] Next ..." -> "... approval [5]. Next ...": a citation after the full stop
        // belongs to the sentence before it, not the one after
        protectedText = TRAILING_CITATION.matcher(protectedText).replaceAll(" $2$1");
        List<String> sentences = new ArrayList<>();
        for (String part : SENTENCE_END.split(protectedText)) {
            String s = part.replace(DOT, ".").strip();
            if (s.length() > 3) {
                sentences.add(s);
            }
        }
        return sentences;
    }

    private static Set<Integer> citations(String sentence, int sourceCount, List<Integer> invalid) {
        Set<Integer> valid = new LinkedHashSet<>();
        Matcher m = CITATION.matcher(sentence);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n < 1 || n > sourceCount) {
                invalid.add(n);
            } else {
                valid.add(n);
            }
        }
        return valid;
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
