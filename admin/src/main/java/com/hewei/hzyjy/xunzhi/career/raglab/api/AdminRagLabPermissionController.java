package com.hewei.hzyjy.xunzhi.career.raglab.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.RagLabPermissionReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagLabPermissionDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagLabPermissionMapper;
import com.hewei.hzyjy.xunzhi.common.convention.annotation.CurrentUser;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

/** 管理员维护 RAG 实验室访问权限的接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/xunzhi/v1/admin/rag-lab/users")
public class AdminRagLabPermissionController {

    private final RagLabPermissionMapper permissionMapper;

    @PutMapping("/{userId}/permission")
    @SaCheckRole("admin")
    public Result<Void> updatePermission(
            @PathVariable Long userId,
            @Valid @RequestBody RagLabPermissionReqDTO request,
            @CurrentUser Long adminUserId) {
        RagLabPermissionDO record = permissionMapper.selectOne(Wrappers.lambdaQuery(RagLabPermissionDO.class)
                .eq(RagLabPermissionDO::getUserId, userId).last("LIMIT 1"));
        if (record == null) {
            record = new RagLabPermissionDO();
            record.setUserId(userId);
            record.setEnabled(request.getEnabled());
            record.setGrantedByUserId(adminUserId);
            record.setGrantedAt(new Date());
            permissionMapper.insert(record);
        } else {
            record.setEnabled(request.getEnabled());
            record.setGrantedByUserId(adminUserId);
            record.setGrantedAt(new Date());
            permissionMapper.updateById(record);
        }
        return Results.success();
    }
}
