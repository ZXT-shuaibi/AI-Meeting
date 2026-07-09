package com.hewei.hzyjy.xunzhi.career.agent.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

public class CareerJsonOutputGuardrail implements OutputGuardrail {

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        if (responseFromLLM == null || responseFromLLM.text() == null || responseFromLLM.text().isBlank()) {
            return success();
        }
        try {
            String cleaned = CareerJsonResponseCleaner.cleanJsonResponse(responseFromLLM.text());
            return successWith(cleaned);
        } catch (Exception ignored) {
            return success();
        }
    }
}
