package io.github.mahoretushar.docuverify;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/** Returns a scripted reply and remembers the last prompt it received. */
public class StubChatModel implements ChatModel {

    String reply = "";
    Prompt lastPrompt;
    int calls;

    @Override
    public ChatResponse call(Prompt prompt) {
        calls++;
        lastPrompt = prompt;
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
    }
}
