package com.genersoft.iot.vmp.common.enums;

import org.dom4j.Element;
import org.springframework.util.ObjectUtils;

/**
 * 设备控制命令类型枚举
 * <p>
 * 包含GB/T 28181-2016原有命令以及GB/T 28181-2022新增命令。
 * </p>
 * <p>
 * <b>2016原有命令：</b><br>
 * PTZ(云台控制)、TeleBoot(远程启动)、Record(录像控制)、Guard(布防撤防)、
 * Alarm(告警控制)、IFame(强制关键帧)、DragZoomIn(拉框放大)、DragZoomOut(拉框缩小)、
 * HomePosition(看守位)。
 * </p>
 * <p>
 * <b>2022新增命令：</b><br>
 * PTZ精准控制、存储卡格式化、目标跟踪、设备软件升级。
 * </p>
 *
 * @author wvp-upgrade
 */
public enum DeviceControlType {

    // ====================================================================
    // === GB/T 28181-2016 原有命令 ===
    // ====================================================================

    /**
     * 云台控制（PTZCmd）
     */
    PTZ("PTZCmd", "云台控制"),

    /**
     * 远程启动（TeleBoot）
     */
    TELE_BOOT("TeleBoot", "远程启动"),

    /**
     * 录像控制（RecordCmd）
     */
    RECORD("RecordCmd", "录像控制"),

    /**
     * 布防撤防（GuardCmd）
     */
    GUARD("GuardCmd", "布防撤防"),

    /**
     * 告警控制（AlarmCmd）
     */
    ALARM("AlarmCmd", "告警控制"),

    /**
     * 强制关键帧（IFameCmd）
     */
    I_FRAME("IFameCmd", "强制关键帧"),

    /**
     * 拉框放大（DragZoomIn）
     */
    DRAG_ZOOM_IN("DragZoomIn", "拉框放大"),

    /**
     * 拉框缩小（DragZoomOut）
     */
    DRAG_ZOOM_OUT("DragZoomOut", "拉框缩小"),

    /**
     * 看守位（HomePosition）
     */
    HOME_POSITION("HomePosition", "看守位"),

    // ====================================================================
    // === GB/T 28181-2022 新增命令 ===
    // ====================================================================

    /**
     * PTZ精准控制（PTzPrecisectrl）
     * <p>
     * 改造项7：PTZ精准控制<br>
     * 来源：设计文档第10.1节（第898~923行），2022版A.2.3.1.11<br>
     * 规范要求：2022版新增PTZ精准控制命令，XML元素名为 PTzPrecisectrl（小写z，大写T）<br>
     * XML子元素：pan（小写）、Tilt（大写T）、zoom（小写）
     * </p>
     */
    PTZ_PRECISE("PTzPrecisectrl", "PTZ精准控制"),

    /**
     * 存储卡格式化（FormatsDcard）
     * <p>
     * 改造项8：存储卡格式化<br>
     * 来源：设计文档第10.1节（第898~923行），2022版A.2.3.1.13<br>
     * 规范要求：2022版新增存储卡格式化命令，XML元素名为 FormatsDcard
     * </p>
     */
    FORMAT_SDCARD("FormatsDcard", "存储卡格式化"),

    /**
     * 目标跟踪（TargetTrack）
     * <p>
     * 改造项9：目标跟踪<br>
     * 来源：设计文档第10.1节（第898~923行），2022版A.2.3.1.14<br>
     * 规范要求：2022版新增目标跟踪命令，XML元素名为 TargetTrack<br>
     * action 取值："Auto" 为自动跟踪，"Manual" 为手动跟踪
     * </p>
     */
    TARGET_TRACK("TargetTrack", "目标跟踪"),

    /**
     * 设备软件升级（Deviceupgrade）
     * <p>
     * 改造项10：设备软件升级<br>
     * 来源：设计文档第10.1节（第898~923行），2022版A.2.3.1.12<br>
     * 规范要求：2022版新增设备软件升级命令，XML元素名为 Deviceupgrade（小写u）<br>
     * XML字段：FirmWare、FileuRL、Manufacturer、sessionID
     * </p>
     */
    DEVICE_UPGRADE("Deviceupgrade", "设备软件升级");

    // ====================================================================
    // === 字段定义 ===
    // ====================================================================

    /**
     * XML 元素名
     */
    private final String val;

    /**
     * 中文描述
     */
    private final String desc;

    /**
     * 枚举构造方法
     *
     * @param val  XML 元素名
     * @param desc 中文描述
     */
    DeviceControlType(String val, String desc) {
        this.val = val;
        this.desc = desc;
    }

    /**
     * 获取 XML 元素名
     *
     * @return XML 元素名
     */
    public String getVal() {
        return val;
    }

    /**
     * 获取中文描述
     *
     * @return 中文描述
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 根据设备控制 XML 根节点解析命令类型
     * <p>
     * 遍历所有枚举值，逐个匹配 XML 节点，命中即返回对应枚举类型。
     * 该方法兼容 2016 与 2022 两种协议版本。
     * </p>
     *
     * @param rootElement XML 根节点
     * @return 命中的命令类型枚举；如果未命中返回 null
     */
    public static DeviceControlType typeOf(Element rootElement) {
        if (rootElement == null) {
            return null;
        }
        for (DeviceControlType item : DeviceControlType.values()) {
            // 同时支持单元素和多元素场景
            if (!ObjectUtils.isEmpty(rootElement.element(item.val))
                    || !ObjectUtils.isEmpty(rootElement.elements(item.val))) {
                return item;
            }
        }
        return null;
    }
}
