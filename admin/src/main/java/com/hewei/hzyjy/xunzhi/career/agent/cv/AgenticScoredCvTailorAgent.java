package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.agent.support.CareerJsonOutputGuardrail;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.OutputGuardrails;

import java.util.List;

public interface AgenticScoredCvTailorAgent {

    @Agent(description = "resume tailoring agent that rewrites existing facts based on review feedback.", outputKey = "cv")
    @OutputGuardrails(value = CareerJsonOutputGuardrail.class, maxRetries = 0)
    @SystemMessage(fromResource = CvPromptTemplates.TAILOR_SYSTEM_PROMPT_RESOURCE)
    @UserMessage(fromResource = CvPromptTemplates.TAILOR_USER_PROMPT_RESOURCE)
    CvBO tailor(@V("cv") CvBO cv,
                @V("cvReview") CvReview cvReview,
                @V("referenceTemplates") List<String> referenceTemplates);
}
