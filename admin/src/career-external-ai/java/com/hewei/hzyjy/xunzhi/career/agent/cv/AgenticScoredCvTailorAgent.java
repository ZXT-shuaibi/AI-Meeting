package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import com.hewei.hzyjy.xunzhi.career.agent.support.CareerJsonOutputGuardrail;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.OutputGuardrails;

import java.util.List;

public interface AgenticScoredCvTailorAgent {

    @Agent(description = "resume tailoring agent that rewrites existing facts based on review feedback.", outputKey = "cv")
    @OutputGuardrails(value = CareerJsonOutputGuardrail.class, maxRetries = 0)
    @SystemMessage("""
            You are a resume tailoring agent.
            Rewrite wording only from existing facts. Do not invent work experience, metrics, companies, or skills.
            Return only a JSON object compatible with CvBO.
            """)
    @UserMessage("""
            CV:
            {{cv}}

            Review:
            {{cvReview}}

            Reference templates:
            {{referenceTemplates}}
            """)
    CvBO tailor(@V("cv") CvBO cv,
                @V("cvReview") CvReview cvReview,
                @V("referenceTemplates") List<String> referenceTemplates);
}
