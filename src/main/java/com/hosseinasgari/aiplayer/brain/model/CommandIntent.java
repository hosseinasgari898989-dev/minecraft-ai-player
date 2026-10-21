package com.hosseinasgari.aiplayer.brain.model;

import java.util.Objects;

public record CommandIntent(
        IntentType intent,
        String language,
        String normalizedInput,
        String resource,
        Integer quantity,
        String structureStyle,
        String targetPlayer,
        Double distance,
        boolean pvpExplicitlyAuthorized,
        boolean ambiguous,
        String clarificationPrompt,
        String unsupportedReason
) {
    public CommandIntent {
        Objects.requireNonNull(intent, "intent");
        language = (language == null || language.isBlank()) ? "en" : language;
        normalizedInput = normalizedInput == null ? "" : normalizedInput;
    }

    public static CommandIntent simple(IntentType intent, String language, String normalizedInput) {
        return new CommandIntent(
                intent, language, normalizedInput,
                null, null, null, null, null,
                false, false, null, null
        );
    }

    public static CommandIntent clarify(String language, String normalizedInput, String question) {
        return new CommandIntent(
                IntentType.CLARIFY, language, normalizedInput,
                null, null, null, null, null,
                false, true, question, null
        );
    }

    public static CommandIntent unsupported(String language, String normalizedInput, String reason) {
        return new CommandIntent(
                IntentType.UNSUPPORTED, language, normalizedInput,
                null, null, null, null, null,
                false, false, null, reason
        );
    }
}
