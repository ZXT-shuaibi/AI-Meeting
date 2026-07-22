package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagLabPermissionDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagLabPermissionMapper;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import com.hewei.hzyjy.xunzhi.user.service.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** RAG 实验室的服务端访问资格与所属用户范围校验。 */
@Service
@RequiredArgsConstructor
public class RagLabAccessService {

    private final RagLabPermissionMapper permissionMapper;
    private final AdminPermissionService adminPermissionService;

    public boolean isAdministrator(String username) {
        return Boolean.TRUE.equals(adminPermissionService.isAdmin(username));
    }

    public boolean canAccess(Long userId, String username) {
        return isAdministrator(username) || Boolean.TRUE.equals(permissionMapper.selectOne(
                Wrappers.lambdaQuery(RagLabPermissionDO.class)
                        .eq(RagLabPermissionDO::getUserId, userId)
                        .eq(RagLabPermissionDO::getEnabled, true)
                        .last("LIMIT 1")) != null);
    }

    public void requireLabAccess(Long userId, String username) {
        if (!canAccess(userId, username)) {
            throw new ClientException("当前账号未获 RAG 实验室访问权限");
        }
    }

    public void requireOwnerScope(Long requesterUserId, String username, Long ownerUserId) {
        requireLabAccess(requesterUserId, username);
        if (!isAdministrator(username) && !Objects.equals(requesterUserId, ownerUserId)) {
            throw new ClientException("无权访问其他用户的 RAG 实验数据");
        }
    }
}
