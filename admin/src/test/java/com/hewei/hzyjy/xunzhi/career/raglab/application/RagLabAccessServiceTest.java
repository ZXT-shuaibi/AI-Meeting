package com.hewei.hzyjy.xunzhi.career.raglab.application;

import com.hewei.hzyjy.xunzhi.career.raglab.dao.mapper.RagLabPermissionMapper;
import com.hewei.hzyjy.xunzhi.user.service.AdminPermissionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RagLabAccessServiceTest {

    @Test
    void treatsAnonymousCallerAsNotAllowedWithoutCallingPermissionStores() {
        RagLabPermissionMapper permissionMapper = mock(RagLabPermissionMapper.class);
        AdminPermissionService adminPermissionService = mock(AdminPermissionService.class);
        RagLabAccessService service = new RagLabAccessService(permissionMapper, adminPermissionService);

        assertFalse(service.isAdministrator(null));
        assertFalse(service.canAccess(null, null));
        verifyNoInteractions(permissionMapper, adminPermissionService);
    }
}
