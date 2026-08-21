package com.example.a2a.kafka.countdown;

import com.example.a2a.kafka.model.Message;
import com.example.a2a.kafka.model.MessageSendParams;
import com.example.a2a.kafka.model.Part;
import com.example.a2a.kafka.model.TextPart;

/** Parse countdown duration from user text (bridge / Clockwork parity). */
public final class CountdownParser {

    private CountdownParser() {}

    public static String extractUserText(MessageSendParams params) {
        if (params == null || params.getMessage() == null || params.getMessage().getParts() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Part part : params.getMessage().getParts()) {
            if (part instanceof TextPart textPart && textPart.getText() != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(textPart.getText());
            }
        }
        return sb.toString();
    }

    public static int parseCountdownSeconds(String text) {
        String normalized = text == null ? "" : text.toLowerCase();
        String[] tokens = normalized.replaceAll("[^a-z0-9 ]", " ").split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].matches("\\d+")) {
                int value = Integer.parseInt(tokens[i]);
                if (i + 1 < tokens.length && tokens[i + 1].startsWith("min")) {
                    return value * 60;
                }
                return value;
            }
        }
        return 0;
    }

    public static Message agentMessage(String text) {
        Message message = new Message();
        message.setRole("agent");
        message.setParts(java.util.List.of(new TextPart(text)));
        return message;
    }
}
