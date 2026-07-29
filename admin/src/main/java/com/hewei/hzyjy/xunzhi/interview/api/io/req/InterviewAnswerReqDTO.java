package com.hewei.hzyjy.xunzhi.interview.api.io.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewAnswerReqDTO {

    @NotBlank(message = "题号不能为空")
    @Size(max = 32, message = "题号长度不能超过 32 个字符")
    private String questionNumber;

    @NotBlank(message = "回答内容不能为空")
    @Size(max = 5000, message = "回答内容长度不能超过 5000 个字符")
    private String answerContent;

    private String sessionId;

    @Size(max = 64, message = "请求幂等标识长度不能超过 64 个字符")
    private String requestId;
}
