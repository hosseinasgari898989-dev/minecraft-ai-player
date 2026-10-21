package com.hosseinasgari.aiplayer;

import com.hosseinasgari.aiplayer.brain.model.CommandIntent;
import com.hosseinasgari.aiplayer.brain.nlp.RuleBasedCommandInterpreter;
import com.hosseinasgari.aiplayer.entity.RobotCommandPlanner;

import java.util.Optional;

/** Local deterministic command interpreter facade. Replaces external HTTP LLM calls completely. */
public final class AiIntentClient {
    public record Decision(String reply, CommandIntent intent, RobotCommandPlanner.Plan plan) {}
    private volatile AiConfig config;

    public AiIntentClient(AiConfig config) { this.config = config; }
    public void reload(AiConfig config) { this.config = config; }
    public boolean isConfigured() { return config.isEnabled(); }
    public String model() { return config.model(); }

    public Optional<Decision> interpretOffline(String instruction) {
        if (!config.isEnabled() || instruction == null || instruction.isBlank()) return Optional.empty();
        CommandIntent intent = RuleBasedCommandInterpreter.interpret(instruction);
        if (intent.ambiguous()) return Optional.of(new Decision(intent.clarificationPrompt(), intent, null));
        if (intent.unsupportedReason() != null) return Optional.of(new Decision(intent.unsupportedReason(), intent, null));
        RobotCommandPlanner.Plan plan = RobotCommandPlanner.fromIntent(intent);
        if (plan != null && !config.isActionEnabled(plan.mode().name())) {
            String disabledMsg = intent.language().equals("fa")
                    ? "این مهارت در تنظیمات غیرفعال شده است: " + plan.mode().name()
                    : "This action is currently disabled in config: " + plan.mode().name();
            return Optional.of(new Decision(disabledMsg, intent, null));
        }
        String ack = intent.language().equals("fa")
                ? "دریافت شد: " + (plan != null ? plan.description() : intent.intent().name())
                : "Accepted: " + (plan != null ? plan.description() : intent.intent().name());
        return Optional.of(new Decision(ack, intent, plan));
    }
}
