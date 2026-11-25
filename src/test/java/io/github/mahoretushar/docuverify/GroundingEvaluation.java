package io.github.mahoretushar.docuverify;

import io.github.mahoretushar.docuverify.verify.GroundingVerifier;
import io.github.mahoretushar.docuverify.verify.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Measures the verifier against real Ollama models on a labelled claim set.
 * Run: ./mvnw test -Dtest=GroundingEvaluation -Deval=true   (needs Ollama and PostgreSQL running)
 * Writes target/evaluation.md.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "eval", matches = "true")
class GroundingEvaluation {

    record Claim(String text, String label) {
    }

    record Dataset(List<String> sources, List<Claim> claims) {
    }

    @Autowired GroundingVerifier verifier;
    @Autowired JsonMapper json;
    @Value("${docuverify.verification.support-threshold}") double threshold;

    @Test
    void evaluate() throws Exception {
        Dataset data = json.readValue(new ClassPathResource("eval/dataset.json").getInputStream(), Dataset.class);
        List<Document> sources = new ArrayList<>();
        for (int i = 0; i < data.sources().size(); i++) {
            sources.add(Document.builder().id("eval-" + i).text(data.sources().get(i)).build());
        }

        StringBuilder md = new StringBuilder("# Verifier evaluation\n\n");
        md.append("Models: llama3.2 (3B) as NLI judge, nomic-embed-text embeddings, via Ollama. ")
          .append("Similarity threshold: ").append(threshold).append(". ")
          .append(data.claims().size()).append(" labelled claims over ").append(sources.size())
          .append(" policy documents; every claim is checked against all four documents.\n\n");

        List<String[]> rows = new ArrayList<>();
        for (boolean nli : new boolean[]{false, true}) {
            int tpBad = 0, fpBad = 0, fnBad = 0, tnBad = 0, contraCaught = 0, contraTotal = 0, unsupCaught = 0, unsupTotal = 0;
            long start = System.nanoTime();
            for (Claim c : data.claims()) {
                var check = verifier.verify(c.text(), sources, nli).sentences().getFirst();
                boolean flagged = check.verdict() != Verdict.SUPPORTED;
                boolean bad = !c.label().equals("SUPPORTED");
                if (bad && flagged) tpBad++;
                if (!bad && flagged) fpBad++;
                if (bad && !flagged) fnBad++;
                if (!bad && !flagged) tnBad++;
                if (c.label().equals("CONTRADICTED")) { contraTotal++; if (flagged) contraCaught++; }
                if (c.label().equals("UNSUPPORTED")) { unsupTotal++; if (flagged) unsupCaught++; }
                if (nli) {
                    rows.add(new String[]{c.label(), check.verdict().name(), String.valueOf(check.bestScore()), c.text()});
                }
            }
            double ms = (System.nanoTime() - start) / 1e6 / data.claims().size();
            double precision = tpBad + fpBad == 0 ? 0 : (double) tpBad / (tpBad + fpBad);
            double recall = (double) tpBad / (tpBad + fnBad);
            double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
            double accuracy = (double) (tpBad + tnBad) / data.claims().size();
            md.append("## ").append(nli ? "Similarity + NLI judge" : "Similarity only").append("\n\n")
              .append("| Metric | Value |\n|---|---|\n")
              .append("| Accuracy | ").append(pct(accuracy)).append(" |\n")
              .append("| Hallucination precision (flagged claims that were really wrong) | ").append(pct(precision)).append(" |\n")
              .append("| Hallucination recall (wrong claims that were flagged) | ").append(pct(recall)).append(" |\n")
              .append("| F1 | ").append(String.format("%.2f", f1)).append(" |\n")
              .append("| Contradictions caught | ").append(contraCaught).append(" / ").append(contraTotal).append(" |\n")
              .append("| Unsupported claims caught | ").append(unsupCaught).append(" / ").append(unsupTotal).append(" |\n")
              .append("| Correct claims wrongly flagged | ").append(fpBad).append(" / ").append(fpBad + tnBad).append(" |\n")
              .append("| Mean time per claim | ").append(String.format("%.0f ms", ms)).append(" |\n\n");
        }
        md.append("## Per-claim results (similarity + NLI)\n\n| Gold | Verdict | Similarity | Claim |\n|---|---|---|---|\n");
        for (String[] r : rows) {
            String mark = r[0].equals("SUPPORTED") == r[1].equals("SUPPORTED") ? "" : " **(miss)**";
            md.append("| ").append(r[0]).append(" | ").append(r[1]).append(mark).append(" | ").append(r[2])
              .append(" | ").append(r[3]).append(" |\n");
        }
        Files.writeString(Path.of("target/evaluation.md"), md.toString());
        System.out.println(md);
    }

    private static String pct(double v) {
        return String.format("%.1f%%", v * 100);
    }
}
