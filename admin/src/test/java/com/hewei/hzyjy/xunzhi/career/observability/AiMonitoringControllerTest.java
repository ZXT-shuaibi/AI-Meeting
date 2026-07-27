package com.hewei.hzyjy.xunzhi.career.observability;

import cn.dev33.satoken.annotation.SaCheckRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class AiMonitoringControllerTest {

    @Test
    void monitoringEndpointsShouldNotRequireAdminRole() {
        assertNull(AiMonitoringController.class.getAnnotation(SaCheckRole.class));
    }
}
