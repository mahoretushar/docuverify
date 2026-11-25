package io.github.mahoretushar.docuverify.verify;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Uses the chat model as an NLI classifier with a strict one-word answer format. */
@Component
public class LlmNliJudge implements NliJudge {

    private static final String SYSTEM = """
            You are a strict fact checker. Compare the CLAIM with the EVIDENCE.
            Reply with exactly one word:
            ENTAILMENT if the evidence states the claim (paraphrases count),
            CONTRADICTION if the evidence states something incompatible with the claim
            (a different number, date, amount, condition, or a negation),
            NEUTRAL if the evidence does not mention it.""";

    private final ChatClient chat;

    public LlmNliJudge(ChatClient chat) {
        this.chat = chat;
    }

    @Override
    public Label judge(String evidence, String claim) {
        String reply = chat.prompt()
                .system(SYSTEM)
                .user(u -> u.text("EVIDENCE:\n{evidence}\n\nCLAIM:\n{claim}\n\nAnswer:")
                        .param("evidence", evidence)
                        .param("claim", claim))
                .call()
                .content();
        return parse(reply);
    }

    static Label parse(String reply) {
        String r = reply == null ? "" : reply.toUpperCase(Locale.ROOT);
        if (r.contains("CONTRADICT")) {
            return Label.CONTRADICTION;
        }
        if (r.contains("ENTAIL")) {
            return Label.ENTAILMENT;
        }
        return Label.NEUTRAL;   // unclear answers are treated as "not supported", never as support
    }
}
