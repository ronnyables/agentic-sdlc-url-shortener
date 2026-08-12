package com.schwab.orchestrator.guardrails;

import com.schwab.orchestrator.core.*;

import java.util.regex.Pattern;

/** Basic secret-scanning policy: blocks a stage from succeeding if its output artifacts look like they contain credentials. */
public final class SecurityGuardrail implements Guardrail {

    private static final Pattern[] SECRET_PATTERNS = {
            Pattern.compile("(?i)password\\s*=\\s*['\"]?\\S+"),
            Pattern.compile("(?i)api[_-]?key\\s*=\\s*['\"]?\\S+"),
            Pattern.compile("(?i)secret\\s*=\\s*['\"]?\\S+"),
            Pattern.compile("-----BEGIN (RSA|EC|OPENSSH) PRIVATE KEY-----")
    };

    @Override
    public String name() { return "security"; }

    @Override
    public GuardrailResult check(WorkflowContext context, StageDefinition stage, StageResult result) {
        for (Object value : result.getArtifacts().values()) {
            if (!(value instanceof String)) continue;
            String s = (String) value;
            for (Pattern p : SECRET_PATTERNS) {
                if (p.matcher(s).find()) {
                    return GuardrailResult.block(name(), "possible hardcoded secret detected in stage output (pattern: " + p.pattern() + ")");
                }
            }
        }
        return GuardrailResult.pass(name());
    }
}
