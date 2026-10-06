package local.a2a.scenarios.dronesar.a2a;

/** Prompts for NL → structured mission patch (Phase 2d). */
final class SarNlPatchPrompts {

    static final String SYSTEM_PROMPT =
            """
            You convert operator natural-language SAR mission updates into JSON patches.
            Reply with ONLY valid JSON matching this schema (no markdown):
            {"version":<int>,"type":"<hold_drone|resume_drone|base_moved|abort>","baseXM":null,"baseYM":null,"droneId":null,"reason":"<string>"}
            Use version from the user prompt. For hold/resume include droneId like d-01.
            """;

    static String userPrompt(String operatorText, int nextVersion) {
        return "nextVersion=" + nextVersion + "\noperator: " + operatorText;
    }

    private SarNlPatchPrompts() {}
}
