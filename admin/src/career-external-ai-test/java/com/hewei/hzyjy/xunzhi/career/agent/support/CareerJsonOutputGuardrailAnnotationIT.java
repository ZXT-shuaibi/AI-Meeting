package com.hewei.hzyjy.xunzhi.career.agent.support;

import com.hewei.hzyjy.xunzhi.career.agent.cv.AgenticCvReviewAgent;
import com.hewei.hzyjy.xunzhi.career.agent.cv.AgenticScoredCvTailorAgent;
import com.hewei.hzyjy.xunzhi.career.agent.interview.AgenticInterviewCoordinatorAgent;
import com.hewei.hzyjy.xunzhi.career.agent.interview.AgenticInterviewOrchestratorAgent;
import com.hewei.hzyjy.xunzhi.career.agent.interview.AgenticInterviewReflectorAgent;
import com.hewei.hzyjy.xunzhi.career.agent.interview.AgenticJdAlignmentAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class CareerJsonOutputGuardrailAnnotationIT {

    @Test
    void structuredAgenticMethodsUseCareerJsonOutputGuardrail() throws Exception {
        assertGuarded(AgenticCvReviewAgent.class, "review");
        assertGuarded(AgenticScoredCvTailorAgent.class, "tailor");
        assertGuarded(AgenticJdAlignmentAgent.class, "align");
        assertGuarded(AgenticInterviewCoordinatorAgent.class, "coordinate");
        assertGuarded(AgenticInterviewOrchestratorAgent.class, "plan");
        assertGuarded(AgenticInterviewReflectorAgent.class, "reflect");
    }

    @Test
    void guardrailRewritesMarkdownWrappedJsonToCleanJson() {
        CareerJsonOutputGuardrail guardrail = new CareerJsonOutputGuardrail();

        OutputGuardrailResult result = guardrail.validate(AiMessage.from("""
                结构化结果如下：
                ```json
                {"score":0.87,"feedback":"保留真实经历并增强量化表达"}
                ```
                字段说明：{score, feedback}
                """));

        assertThat(result.result()).isEqualTo(dev.langchain4j.guardrail.GuardrailResult.Result.SUCCESS_WITH_RESULT);
        assertThat(result.successfulText()).isEqualTo("{\"score\":0.87,\"feedback\":\"保留真实经历并增强量化表达\"}");
    }

    private void assertGuarded(Class<?> agentType, String methodName) throws Exception {
        Method method = findMethod(agentType, methodName);
        OutputGuardrails guardrails = method.getAnnotation(OutputGuardrails.class);
        assertThat(guardrails)
                .as(agentType.getSimpleName() + "#" + methodName + " should declare output guardrails")
                .isNotNull();
        assertThat(guardrails.value()).contains(CareerJsonOutputGuardrail.class);
        assertThat(guardrails.maxRetries()).isZero();
    }

    private Method findMethod(Class<?> agentType, String methodName) throws NoSuchMethodException {
        for (Method method : agentType.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new NoSuchMethodException(agentType.getName() + "#" + methodName);
    }
}
