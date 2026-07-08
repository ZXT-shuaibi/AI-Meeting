package com.hewei.hzyjy.xunzhi.career.memory;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class InterviewRuleBasedScorer implements ImportanceScorer {

    private static final Pattern DECISION_PATTERN = Pattern.compile(
            "(?i).*(score|decision|conclusion|recommend|PROBE|NEXT|STAGE_FINISH|FINISH|JD_ALIGNMENT|reflection|matchScore|interviewPlan|passed|follow-up|probe|stage).*",
            Pattern.DOTALL
    );

    private static final Pattern STRUCTURED_RESULT_PATTERN = Pattern.compile(
            "(?i).*(\\{.*\\\"(matchScore|stages|questionContent|score|decision)\\\".*}).*",
            Pattern.DOTALL
    );

    @Override
    public MemoryImportance score(MemoryMessage message, List<MemoryMessage> context) {
        if (message == null) {
            return MemoryImportance.LOW;
        }
        if (message.role() == MemoryRole.SYSTEM || message.role() == MemoryRole.TOOL) {
            return MemoryImportance.HIGH;
        }
        String content = message.content();
        if (content == null || content.isBlank()) {
            return MemoryImportance.LOW;
        }
        if (DECISION_PATTERN.matcher(content).matches() || STRUCTURED_RESULT_PATTERN.matcher(content).matches()) {
            return MemoryImportance.HIGH;
        }
        if (message.role() == MemoryRole.ASSISTANT && content.length() > 200) {
            return MemoryImportance.MEDIUM;
        }
        if (message.role() == MemoryRole.USER && content.length() > 150) {
            return MemoryImportance.MEDIUM;
        }
        if (content.length() < 30) {
            return MemoryImportance.LOW;
        }
        return MemoryImportance.MEDIUM;
    }
}
