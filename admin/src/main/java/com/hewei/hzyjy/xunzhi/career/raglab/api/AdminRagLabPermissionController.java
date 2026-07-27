package com.hewei.hzyjy.xunzhi.career.raglab.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.raglab.api.io.RagLabPermissionReqDTO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagLabPermissionDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagLabPermissionMapper;
import com.hewei.hzyjy.xunzhi.common.convention.annotation.CurrentUser;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import com.hewei.hzyjy.xunzhi.user.dao.entity.UserDO;
import com.hewei.hzyjy.xunzhi.user.dao.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Date;
import java.util.List;
import java.util.Map;

/** 管理员维护 RAG 实验室访问权限的接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/xunzhi/v1/admin/rag-lab/users")
public class AdminRagLabPermissionController {

    private final RagLabPermissionMapper permissionMapper;
    private final UserMapper userMapper;

    /**
     * 管理员授权台只返回识别账号所需的最小用户字段；不返回密码、手机号等敏感资料。
     */
    @GetMapping
    @SaCheckRole("admin")
    public Result<List<Map<String, Object>>> users() {
        Map<Long, RagLabPermissionDO> permissions = permissionMapper.selectList(Wrappers.lambdaQuery(RagLabPermissionDO.class))
                .stream().collect(java.util.stream.Collectors.toMap(RagLabPermissionDO::getUserId, item -> item, (left, right) -> left));
        List<Map<String, Object>> records = userMapper.selectList(Wrappers.lambdaQuery(UserDO.class)
                        .orderByDesc(UserDO::getCreateTime))
                .stream().map(user -> {
                    RagLabPermissionDO permission = permissions.get(user.getId());
                    return Map.<String, Object>of(
                            // 雪花 ID 超过 JavaScript 的安全整数范围；必须作为字符串传输，
                            // 否则管理员点击授权后会把四舍五入后的错误 ID 写入数据库。
                            "userId", String.valueOf(user.getId()),
                            "username", user.getUsername() == null ? "" : user.getUsername(),
                            "realName", user.getRealName() == null ? "" : user.getRealName(),
                            "enabled", permission != null && Boolean.TRUE.equals(permission.getEnabled()),
                            "grantedAt", permission == null ? "" : String.valueOf(permission.getGrantedAt()));
                }).toList();
        return Results.success(records);
    }

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
