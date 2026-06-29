package com.genersoft.iot.vmp.gb28181.utils;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X-GB-ver协议版本标识测试
 * 测试用例: A2.1-01~07, B16-01~03
 * 来源: 设计文档第13.9节, 2022版附录I
 */
@DisplayName("X-GB-ver协议版本标识测试")
class GBProtocolVersionHelperTest {

    @Test
    @DisplayName("A2.1-01: 协议版本号正确性")
    void testGbProtocolVersion() {
        // 来源: 改造项1, 设计文档第13.9节, 2022版附录I
        // 规范原文: "版本号表示为m.n，例如X-GB-ver: 3.0"
        // 本方案采用"2.0"表示GB/T 28181—2022版本
        assertEquals("2.0", GBProtocolVersionHelper.GB_PROTOCOL_VERSION, "2022版协议版本号应为2.0");
    }

    @Test
    @DisplayName("A2.1-03: 2016设备版本判断")
    void testIsVersion2022() {
        assertTrue(GBProtocolVersionHelper.isVersion2022("2.0"), "版本2.0应识别为2022版");
        assertFalse(GBProtocolVersionHelper.isVersion2022("1.0"), "版本1.0应识别为2016版");
        assertFalse(GBProtocolVersionHelper.isVersion2022(null), "null应识别为2016版");
        assertFalse(GBProtocolVersionHelper.isVersion2022(""), "空字符串应识别为2016版");
    }

    @Test
    @DisplayName("A2.1-05: 版本协商降级")
    void testNegotiateVersion() {
        // 双方都为2.0才走2022流程
        assertEquals("2.0", GBProtocolVersionHelper.negotiateVersion("2.0"), "双方2.0应协商为2022版");
        // 一方为1.0应降级
        assertEquals("1.0", GBProtocolVersionHelper.negotiateVersion("1.0"), "对方1.0应降级为2016版");
        assertEquals("1.0", GBProtocolVersionHelper.negotiateVersion(null), "对方null应降级为2016版");
    }

    @Test
    @DisplayName("B16-01: Monitor-user-Identity头域(小写u)")
    void testMonitorUserIdentityHeader() {
        // 来源: 改造项27, 设计文档第8.3节, 2022版8.3
        // 2016版为Monitor-User-Identity(大写U), 2022版改为Monitor-user-Identity(小写u)
        assertEquals("Monitor-user-Identity", GBProtocolVersionHelper.HEADER_MONITOR_USER_IDENTITY,
            "2022版头域名应为Monitor-user-Identity(小写u)");
    }
}
