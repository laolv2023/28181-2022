package com.genersoft.iot.vmp.common.enums;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 设备控制命令类型枚举测试
 * 测试用例: A4.1-01~05, B3-01~16
 * 来源: 改造项7-10, 设计文档第10.1节, 2022版A.2.3.1
 */
@DisplayName("设备控制命令类型枚举测试")
class DeviceControlTypeTest {

    @Test
    @DisplayName("A4.1-01: PTZ精准控制PTzPrecisectrl(小写z)")
    void testPtzPreciseElementName() {
        // 来源: 改造项7, 2022版A.2.3.1.11
        // XML元素名: PTzPrecisectrl (小写z, 大写T)
        assertEquals("PTzPrecisectrl", DeviceControlType.PTZ_PRECISE.getVal(),
            "PTZ精准控制的XML元素名应为PTzPrecisectrl(小写z)");
        assertNotEquals("PTZPrecisectrl", DeviceControlType.PTZ_PRECISE.getVal(),
            "不应为大写Z");
        assertNotEquals("ptzprecisectrl", DeviceControlType.PTZ_PRECISE.getVal(),
            "不应为全小写");
    }

    @Test
    @DisplayName("A4.1-02: 存储卡格式化FormatsDcard")
    void testFormatSdcardElementName() {
        // 来源: 改造项8, 2022版A.2.3.1.13
        assertEquals("FormatsDcard", DeviceControlType.FORMAT_SDCARD.getVal(),
            "存储卡格式化的XML元素名应为FormatsDcard");
    }

    @Test
    @DisplayName("A4.1-03: 目标跟踪TargetTrack")
    void testTargetTrackElementName() {
        // 来源: 改造项9, 2022版A.2.3.1.14
        assertEquals("TargetTrack", DeviceControlType.TARGET_TRACK.getVal(),
            "目标跟踪的XML元素名应为TargetTrack");
    }

    @Test
    @DisplayName("A4.1-04: 设备软件升级Deviceupgrade(小写u)")
    void testDeviceUpgradeElementName() {
        // 来源: 改造项10, 2022版A.2.3.1.12
        // XML元素名: Deviceupgrade (小写u)
        assertEquals("Deviceupgrade", DeviceControlType.DEVICE_UPGRADE.getVal(),
            "设备软件升级的XML元素名应为Deviceupgrade(小写u)");
        assertNotEquals("DeviceUpgrade", DeviceControlType.DEVICE_UPGRADE.getVal(),
            "不应为大写U");
    }

    @Test
    @DisplayName("2016原有命令保持不变")
    void testExistingTypes() {
        assertEquals("PTZCmd", DeviceControlType.PTZ.getVal());
        assertEquals("TeleBoot", DeviceControlType.TELE_BOOT.getVal());
        assertEquals("RecordCmd", DeviceControlType.RECORD.getVal());
        assertEquals("GuardCmd", DeviceControlType.GUARD.getVal());
        assertEquals("AlarmCmd", DeviceControlType.ALARM.getVal());
        assertEquals("IFrameCmd", DeviceControlType.I_FRAME.getVal());
        assertEquals("DragZoomIn", DeviceControlType.DRAG_ZOOM_IN.getVal());
        assertEquals("DragZoomOut", DeviceControlType.DRAG_ZOOM_OUT.getVal());
        assertEquals("HomePosition", DeviceControlType.HOME_POSITION.getVal());
    }

    @Test
    @DisplayName("typeOf识别2022新增控制命令")
    void testTypeOf_2022NewCommands() {
        // 测试PTzPrecisectrl识别
        Element root1 = DocumentHelper.createElement("Notify");
        root1.addElement("PTzPrecisectrl");
        assertEquals(DeviceControlType.PTZ_PRECISE, DeviceControlType.typeOf(root1),
            "应识别PTzPrecisectrl为PTZ_PRECISE");

        // 测试FormatsDcard识别
        Element root2 = DocumentHelper.createElement("Notify");
        root2.addElement("FormatsDcard");
        assertEquals(DeviceControlType.FORMAT_SDCARD, DeviceControlType.typeOf(root2),
            "应识别FormatsDcard为FORMAT_SDCARD");

        // 测试TargetTrack识别
        Element root3 = DocumentHelper.createElement("Notify");
        root3.addElement("TargetTrack");
        assertEquals(DeviceControlType.TARGET_TRACK, DeviceControlType.typeOf(root3),
            "应识别TargetTrack为TARGET_TRACK");

        // 测试Deviceupgrade识别
        Element root4 = DocumentHelper.createElement("Notify");
        root4.addElement("Deviceupgrade");
        assertEquals(DeviceControlType.DEVICE_UPGRADE, DeviceControlType.typeOf(root4),
            "应识别Deviceupgrade为DEVICE_UPGRADE");
    }

    @Test
    @DisplayName("typeOf识别2016原有命令")
    void testTypeOf_ExistingCommands() {
        Element root = DocumentHelper.createElement("Notify");
        root.addElement("PTZCmd");
        assertEquals(DeviceControlType.PTZ, DeviceControlType.typeOf(root));
    }

    @Test
    @DisplayName("typeOf未知类型返回null")
    void testTypeOf_Unknown() {
        Element root = DocumentHelper.createElement("Notify");
        root.addElement("UnknownCmd");
        assertNull(DeviceControlType.typeOf(root), "未知命令应返回null");
    }
}
