package com.hewei.hzyjy.xunzhi.career.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LangChain4jHybridMemoryAdapter {

    private static final String CHAT_MEMORY_CLASS = "dev.langchain4j.memory.ChatMemory";
    private static final String CHAT_MEMORY_STORE_CLASS = "dev.langchain4j.store.memory.chat.ChatMemoryStore";
    private static final String SYSTEM_MESSAGE_CLASS = "dev.langchain4j.data.message.SystemMessage";
    private static final String USER_MESSAGE_CLASS = "dev.langchain4j.data.message.UserMessage";
    private static final String AI_MESSAGE_CLASS = "dev.langchain4j.data.message.AiMessage";
    private static final String TOOL_MESSAGE_CLASS = "dev.langchain4j.data.message.ToolExecutionResultMessage";

    private final HybridCompactingChatMemory hybridMemory;

    public Object chatMemory(Object memoryId) {
        return proxy(CHAT_MEMORY_CLASS, new ChatMemoryInvocationHandler(String.valueOf(memoryId)));
    }

    public Object chatMemoryStore() {
        return proxy(CHAT_MEMORY_STORE_CLASS, new ChatMemoryStoreInvocationHandler());
    }

    public boolean isLangChain4jMemoryAvailable() {
        return classExists(CHAT_MEMORY_CLASS) && classExists(CHAT_MEMORY_STORE_CLASS);
    }

    private Object proxy(String interfaceClassName, InvocationHandler handler) {
        try {
            Class<?> type = Class.forName(interfaceClassName);
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("LangChain4j memory classes are unavailable. Enable the career-external-ai profile.", ex);
        }
    }

    private boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private List<Object> toLangChainMessages(Object memoryId) {
        CompactedMemoryView view = hybridMemory.view(String.valueOf(memoryId), 8);
        List<Object> result = new ArrayList<>();
        if (view.decisionContext() != null && !view.decisionContext().isBlank()) {
            result.add(newMessage(SYSTEM_MESSAGE_CLASS, view.decisionContext()));
        }
        for (MemoryMessage message : view.messages()) {
            result.add(toLangChainMessage(message));
        }
        return List.copyOf(result);
    }

    private Object toLangChainMessage(MemoryMessage message) {
        MemoryRole role = message == null || message.role() == null ? MemoryRole.SYSTEM : message.role();
        String content = message == null || message.content() == null ? "" : message.content();
        return switch (role) {
            case SYSTEM -> newMessage(SYSTEM_MESSAGE_CLASS, content);
            case USER -> newMessage(USER_MESSAGE_CLASS, content);
            case ASSISTANT -> newMessage(AI_MESSAGE_CLASS, content);
            case TOOL -> toolMessage(content);
        };
    }

    private MemoryMessage fromLangChainMessage(Object message) {
        return MemoryMessage.builder()
                .role(roleOf(message))
                .content(textOf(message))
                .build();
    }

    private MemoryRole roleOf(Object message) {
        String type = String.valueOf(invoke(message, "type"));
        if (type.endsWith("USER")) {
            return MemoryRole.USER;
        }
        if (type.endsWith("AI")) {
            return MemoryRole.ASSISTANT;
        }
        if (type.endsWith("TOOL_EXECUTION_RESULT")) {
            return MemoryRole.TOOL;
        }
        return MemoryRole.SYSTEM;
    }

    private String textOf(Object message) {
        if (message == null) {
            return "";
        }
        for (String methodName : List.of("text", "singleText")) {
            try {
                Object value = invoke(message, methodName);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (IllegalStateException ignored) {
                // Try the next LangChain4j text accessor.
            }
        }
        return String.valueOf(message);
    }

    private Object newMessage(String className, String content) {
        try {
            Class<?> type = Class.forName(className);
            return type.getMethod("from", String.class).invoke(null, content == null ? "" : content);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create LangChain4j message: " + className, ex);
        }
    }

    private Object toolMessage(String content) {
        try {
            Class<?> type = Class.forName(TOOL_MESSAGE_CLASS);
            return type.getMethod("from", String.class, String.class, String.class)
                    .invoke(null, "xunzhi-tool", "career-memory", content == null ? "" : content);
        } catch (Exception ex) {
            return newMessage(SYSTEM_MESSAGE_CLASS, content);
        }
    }

    private Object invoke(Object target, String methodName, Object... args) {
        try {
            Class<?>[] argTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                argTypes[i] = args[i] == null ? Object.class : args[i].getClass();
            }
            Method method = findMethod(target.getClass(), methodName, args.length);
            return method.invoke(target, args);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to invoke LangChain4j method: " + methodName, ex);
        }
    }

    private Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new IllegalStateException("No LangChain4j method found: " + type.getName() + "." + name);
    }

    private final class ChatMemoryInvocationHandler implements InvocationHandler {
        private final String memoryId;

        private ChatMemoryInvocationHandler(String memoryId) {
            this.memoryId = memoryId;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "id" -> memoryId;
                case "messages" -> toLangChainMessages(memoryId);
                case "add" -> {
                    addMessages(memoryId, args);
                    yield null;
                }
                case "clear" -> {
                    hybridMemory.clear(memoryId);
                    yield null;
                }
                case "toString" -> "LangChain4jHybridChatMemory[" + memoryId + "]";
                case "hashCode" -> memoryId.hashCode();
                case "equals" -> proxy == (args == null ? null : args[0]);
                default -> throw new UnsupportedOperationException("Unsupported ChatMemory method: " + method.getName());
            };
        }
    }

    private final class ChatMemoryStoreInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            Object memoryId = args == null || args.length == 0 ? "" : args[0];
            return switch (method.getName()) {
                case "getMessages" -> toLangChainMessages(memoryId);
                case "updateMessages" -> {
                    hybridMemory.clear(String.valueOf(memoryId));
                    if (args != null && args.length > 1 && args[1] instanceof Iterable<?> messages) {
                        for (Object message : messages) {
                            hybridMemory.add(String.valueOf(memoryId), fromLangChainMessage(message));
                        }
                    }
                    yield null;
                }
                case "deleteMessages" -> {
                    hybridMemory.clear(String.valueOf(memoryId));
                    yield null;
                }
                case "toString" -> "LangChain4jHybridChatMemoryStore";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == (args == null ? null : args[0]);
                default -> throw new UnsupportedOperationException("Unsupported ChatMemoryStore method: " + method.getName());
            };
        }
    }

    private void addMessages(String memoryId, Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        if (args.length == 1 && args[0] instanceof Iterable<?> messages) {
            for (Object message : messages) {
                hybridMemory.add(memoryId, fromLangChainMessage(message));
            }
            return;
        }
        for (Object message : args) {
            hybridMemory.add(memoryId, fromLangChainMessage(message));
        }
    }
}
