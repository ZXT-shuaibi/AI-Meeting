package com.hewei.hzyjy.xunzhi.career.raglab.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.entity.RagLabPermissionDO;
import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagLabPermissionMapper;
import com.hewei.hzyjy.xunzhi.common.convention.result.Result;
import com.hewei.hzyjy.xunzhi.user.dao.entity.UserDO;
import com.hewei.hzyjy.xunzhi.user.dao.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminRagLabPermissionControllerTest {

    @Test
    void returnsSnowflakeUserIdAsStringSoBrowserDoesNotLosePrecision() {
        long userId = 2077755989842452481L;
        RagLabPermissionMapper permissionMapper = mock(RagLabPermissionMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        UserDO user = new UserDO();
        user.setId(userId);
        user.setUsername("candidate");
        RagLabPermissionDO permission = new RagLabPermissionDO();
        permission.setUserId(userId);
        permission.setEnabled(true);
        when(permissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(permission));
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(user));

        Result<List<Map<String, Object>>> result = new AdminRagLabPermissionController(permissionMapper, userMapper).users();

        assertEquals("2077755989842452481", result.getData().get(0).get("userId"));
    }
}
