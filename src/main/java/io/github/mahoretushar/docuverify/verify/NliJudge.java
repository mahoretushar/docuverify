package io.github.mahoretushar.docuverify.verify;

/** Natural-language inference: does the evidence entail, contradict, or not address the claim? */
public interface NliJudge {

    enum Label { ENTAILMENT, CONTRADICTION, NEUTRAL }

    Label judge(String evidence, String claim);
}
