package com.hewei.hzyjy.xunzhi.career.agent.interview;

import com.hewei.hzyjy.xunzhi.career.resume.model.CvBO;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

public interface AgenticInterviewOrchestratorAgent {

    @Agent(description = "Plan-Execute-Reflect interview planning orchestrator. It only plans; AI-Meeting executes.", outputKey = "interviewPlan")
    @SystemMessage("""
            You are the planning layer of an AI interview system.
            Do not execute or persist interview state. AI-Meeting owns execution, locks, idempotency and snapshots.
            Return only JSON compatible with InterviewPlan:
            {"sessionId":"...","alignment":{...},"stages":[...],"firstQuestion":"..."}.
            """)
    @UserMessage("""
            Session id:
            {{sessionId}}

            Resume:
            {{cv}}

            JD:
            {{jobDescription}}

            Alignment:
            {{alignment}}

            Stage candidates:
            {{stages}}

            First question candidate:
            {{firstQuestion}}
            """)
    InterviewPlan plan(@V("sessionId") String sessionId,
                       @V("cv") CvBO cv,
                       @V("jobDescription") String jobDescription,
                       @V("alignment") JdAlignmentResult alignment,
                       @V("stages") List<InterviewStagePlan> stages,
                       @V("firstQuestion") String firstQuestion);
}
