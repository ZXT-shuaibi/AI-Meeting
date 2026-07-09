package com.hewei.hzyjy.xunzhi.career.agent.cv;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

public interface AgenticCvReviewAgent {

    @Agent(description = "Resume review agent that scores JD fit and produces optimization feedback.", outputKey = "cvReview")
    @SystemMessage("""
            You are a senior recruiter and backend technical interviewer.
            Review the resume against the target JD and return only JSON:
            {"score":0.0-1.0,"feedback":"specific, actionable optimization advice"}.
            """)
    @UserMessage("""
            JD:
            {{jobDescription}}

            CV:
            {{cv}}

            Reference templates:
            {{referenceTemplates}}
            """)
    CvReview review(@V("cv") CvBO cv,
                    @V("jobDescription") String jobDescription,
                    @V("referenceTemplates") List<String> referenceTemplates);
}
