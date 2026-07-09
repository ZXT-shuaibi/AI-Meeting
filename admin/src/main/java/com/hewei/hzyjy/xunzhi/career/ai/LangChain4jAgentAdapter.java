package com.hewei.hzyjy.xunzhi.career.ai;

import com.hewei.hzyjy.xunzhi.career.observability.AiTracePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LangChain4jAgentAdapter implements AgentRuntimeGateway {

    private final ApplicationContext applicationContext;
    private final ObjectProvider<AiTracePublisher> tracePublisherProvider;

    @Override
    public <T> T invoke(String agentName, String methodName, Map<String, Object> variables, Class<T> responseType) {
        String traceId = UUID.randomUUID().toString();
        AiTracePublisher tracePublisher = tracePublisherProvider.getIfAvailable();
        if (tracePublisher != null) {
            tracePublisher.started(traceId, agentName, null, "langchain4j", null, String.valueOf(variables));
        }
        long start = System.currentTimeMillis();
        try {
            Object agent = resolveAgent(agentName);
            Method method = resolveMethod(agent.getClass(), methodName, variables);
            Object result = method.invoke(agent, buildArguments(method, variables));
            if (tracePublisher != null) {
                tracePublisher.completed(traceId, agentName, null, "langchain4j", null, start, String.valueOf(result));
            }
            return responseType.cast(result);
        } catch (Exception ex) {
            if (tracePublisher != null) {
                tracePublisher.failed(traceId, agentName, null, "langchain4j", null, start, ex);
            }
            throw new IllegalStateException("LangChain4j agent is unavailable: " + agentName + "." + methodName, ex);
        }
    }

    private Object resolveAgent(String agentName) {
        String agenticBeanName = "Agentic" + agentName;
        if (applicationContext.containsBean(agenticBeanName)) {
            return applicationContext.getBean(agenticBeanName);
        }
        if (applicationContext.containsBean(agentName)) {
            return applicationContext.getBean(agentName);
        }
        return applicationContext.getBeansOfType(Object.class).values().stream()
                .filter(bean -> bean.getClass().getSimpleName().equals(agentName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No LangChain4j agent bean found: " + agentName));
    }

    private Method resolveMethod(Class<?> type, String methodName, Map<String, Object> variables) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (method.getParameterCount() == 0 || method.getParameterCount() == safeSize(variables)) {
                return method;
            }
        }
        throw new IllegalArgumentException("No method found: " + methodName);
    }

    private Object[] buildArguments(Method method, Map<String, Object> variables) {
        if (method.getParameterCount() == 0) {
            return new Object[0];
        }
        Object[] ordered = buildKnownArguments(method, variables);
        if (ordered != null) {
            return ordered;
        }
        Object[] args = new Object[method.getParameterCount()];
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            args[i] = resolveArgument(parameters[i], variables);
        }
        return args;
    }

    private Object[] buildKnownArguments(Method method, Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        List<String> keys = knownArgumentKeys(method.getName());
        if (keys.isEmpty() || keys.size() != method.getParameterCount()) {
            return null;
        }
        Object[] args = new Object[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            if (!variables.containsKey(keys.get(i))) {
                return null;
            }
            args[i] = variables.get(keys.get(i));
        }
        return args;
    }

    private List<String> knownArgumentKeys(String methodName) {
        return switch (methodName) {
            case "review" -> List.of("cv", "jobDescription", "referenceTemplates");
            case "tailor" -> List.of("cv", "review", "referenceTemplates");
            case "align" -> List.of("memoryId", "cv", "jobDescription");
            case "coordinate" -> List.of("memoryId", "alignment");
            case "plan" -> List.of("sessionId", "cv", "jobDescription", "alignment", "stages", "firstQuestion");
            case "reflect" -> List.of("memoryId", "currentQuestion", "userAnswer", "cv", "memoryView");
            default -> List.of();
        };
    }

    private Object resolveArgument(Parameter parameter, Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        if (variables.containsKey(parameter.getName())) {
            return variables.get(parameter.getName());
        }
        return variables.values().stream()
                .filter(value -> value == null || parameter.getType().isInstance(value) || isPrimitiveWrapper(parameter.getType(), value))
                .findFirst()
                .orElse(null);
    }

    private boolean isPrimitiveWrapper(Class<?> type, Object value) {
        if (value == null || !type.isPrimitive()) {
            return false;
        }
        return (type == int.class && value instanceof Integer)
                || (type == long.class && value instanceof Long)
                || (type == double.class && value instanceof Double)
                || (type == boolean.class && value instanceof Boolean)
                || (type == float.class && value instanceof Float);
    }

    private int safeSize(Map<String, Object> variables) {
        return variables == null ? 0 : variables.size();
    }
}
