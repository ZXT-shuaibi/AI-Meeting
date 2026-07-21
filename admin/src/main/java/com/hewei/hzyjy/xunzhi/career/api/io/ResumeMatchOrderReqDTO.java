package com.hewei.hzyjy.xunzhi.career.api.io;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 候选简历展示顺序保存请求。
 *
 * <p>列表必须包含当前用户可参与岗位匹配的简历编号，服务层会进一步校验编号唯一性和归属，
 * 防止前端缓存过期或跨账号数据误写。</p>
 */
@Data
public class ResumeMatchOrderReqDTO {

    @NotEmpty
    private List<@NotNull Long> resumeIds;
}
