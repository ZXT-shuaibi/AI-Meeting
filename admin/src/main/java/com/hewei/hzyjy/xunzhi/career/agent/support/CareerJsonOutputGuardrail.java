package com.hewei.hzyjy.xunzhi.career.agent.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

public class CareerJsonOutputGuardrail implements OutputGuardrail {

    @Override
    public OutputGuardrailResult validate(AiMessage response) {
        if (response == null || response.text() == null || response.text().isBlank()) {
            return OutputGuardrailResult.success();
        }
        String cleaned = CareerJsonResponseCleaner.cleanJsonResponse(response.text());
        if (cleaned.equals(response.text())) {
            return OutputGuardrailResult.success();
        }
        return OutputGuardrailResult.builder()
                .result(GuardrailResult.Result.SUCCESS_WITH_RESULT)
                .successfulText(cleaned)
                .build();
    }
}
