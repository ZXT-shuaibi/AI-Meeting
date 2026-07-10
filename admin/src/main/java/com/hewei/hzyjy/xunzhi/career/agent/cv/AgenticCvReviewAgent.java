package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.agent.support.CareerJsonOutputGuardrail;
import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.OutputGuardrails;

import java.util.List;

public interface AgenticCvReviewAgent {

    @Agent(description = "Resume review agent that scores JD fit and produces optimization feedback.", outputKey = "cvReview")
    @OutputGuardrails(value = CareerJsonOutputGuardrail.class, maxRetries = 0)
    @SystemMessage(fromResource = CvPromptTemplates.REVIEWER_SYSTEM_PROMPT_RESOURCE)
    @UserMessage(fromResource = CvPromptTemplates.REVIEWER_USER_PROMPT_RESOURCE)
    CvReview review(@V("cv") CvBO cv,
                    @V("jobDescription") String jobDescription,
                    @V("referenceTemplates") List<String> referenceTemplates);
}
