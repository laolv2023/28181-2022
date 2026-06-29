package com.genersoft.iot.vmp.gb28181.utils;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 编码规则测试
 * 测试用例: A9.2-01~03, B13-01~05
 * 来源: 改造项36-38, 设计文档第13.5节, 2022版附录E
 */
@DisplayName("编码规则测试")
class GbCode2022Test {

    @Test
    @DisplayName("A9.2-01: 行政区划码校验")
    void testIsValidAdminCode() {
        // 来源: 改造项36, 2022版附录E
        // 6位数字, 省份码11~65
        assertTrue(GbCode2022.isValidAdminCode("110000"), "北京行政区划码应有效");
        assertTrue(GbCode2022.isValidAdminCode("310000"), "上海行政区划码应有效");
        assertTrue(GbCode2022.isValidAdminCode("650000"), "新疆行政区划码应有效");
        assertFalse(GbCode2022.isValidAdminCode("100000"), "省份码10应无效(低于11)");
        assertFalse(GbCode2022.isValidAdminCode("660000"), "省份码66应无效(高于65)");
        assertFalse(GbCode2022.isValidAdminCode("12345"), "5位应无效");
        assertFalse(GbCode2022.isValidAdminCode("1234567"), "7位应无效");
        assertFalse(GbCode2022.isValidAdminCode(null), "null应无效");
        assertFalse(GbCode2022.isValidAdminCode("ABCDEF"), "非数字应无效");
    }

    @Test
    @DisplayName("A9.2-02: 设备类型码校验")
    void testIsNewTypeCode() {
        // 来源: 改造项37, 2022版附录E新增设备类型
        // 120~125, 140~143, 502, 503, 505
        assertTrue(GbCode2022.isNewTypeCode("120"), "120应为有效的2022新增类型码");
        assertTrue(GbCode2022.isNewTypeCode("125"), "125应为有效的2022新增类型码");
        assertTrue(GbCode2022.isNewTypeCode("140"), "140应为有效的2022新增类型码");
        assertTrue(GbCode2022.isNewTypeCode("502"), "502应为有效的2022新增类型码");
        assertTrue(GbCode2022.isNewTypeCode("505"), "505应为有效的2022新增类型码");
        assertFalse(GbCode2022.isNewTypeCode("119"), "119不在新增类型码范围内");
        assertFalse(GbCode2022.isNewTypeCode("126"), "126不在新增类型码范围内");
        assertFalse(GbCode2022.isNewTypeCode(null), "null应无效");
    }

    @Test
    @DisplayName("A9.2-03: 网络码校验")
    void testIsValidNetCode() {
        // 来源: 2022版附录E, 网络码0~9
        for (int i = 0; i <= 9; i++) {
            assertTrue(GbCode2022.isValidNetCode(String.valueOf(i)), "网络码" + i + "应有效");
        }
        assertFalse(GbCode2022.isValidNetCode("10"), "网络码10应无效");
        assertFalse(GbCode2022.isValidNetCode(null), "null应无效");
    }

    @Test
    @DisplayName("B13-05: 采集位置码校验")
    void testIsValidCapturePositionCode() {
        // 来源: 改造项38, 2022版附录O, 采集位置码1~10
        assertTrue(GbCode2022.isValidCapturePositionCode("1"), "位置码1应有效");
        assertTrue(GbCode2022.isValidCapturePositionCode("10"), "位置码10应有效");
        assertFalse(GbCode2022.isValidCapturePositionCode("0"), "位置码0应无效");
        assertFalse(GbCode2022.isValidCapturePositionCode("11"), "位置码11应无效");
        assertFalse(GbCode2022.isValidCapturePositionCode(null), "null应无效");
    }
}
