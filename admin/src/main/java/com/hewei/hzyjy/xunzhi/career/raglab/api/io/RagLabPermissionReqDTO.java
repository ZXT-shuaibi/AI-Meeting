package com.hewei.hzyjy.xunzhi.career.raglab.api.io;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 管理员授予或撤销用户 RAG 实验室访问资格的请求。 */
@Data
public class RagLabPermissionReqDTO {
    @NotNull(message = "授权状态不能为空")
    private Boolean enabled;
}
