package com.genersoft.iot.vmp.gb28181.transmit.cmd;

import com.genersoft.iot.vmp.gb28181.bean.Device;
import org.springframework.web.multipart.MultipartFile;

/**
 * SIPCommander 补充方法骨架 —— GB/T 28181-2022 配套
 *
 * <p>依据: 《WVP前端UI改造方案》D4修复说明、《WVP合规性升级改造开发方案》改造项7~15</p>
 * <p>说明: 以下 10 个方法需要在 {@code ISIPCommander} 接口和 {@code SIPCommander} 实现类中新增。
 * 本文件仅为方法签名和骨架文档，具体实现需构造 GB/T 28181 XML 消息并通过 JAIN-SIP 下发到设备。</p>
 *
 * <p>实现要点:</p>
 * <ul>
 *   <li>每个方法内部构造对应的 XML 消息（Control/Query 容器格式）</li>
 *   <li>通过 {@code SipSender} 将 XML 作为 MESSAGE 体通过 SIP 发送到设备</li>
 *   <li>查询类命令（query*）通常发送后由设备异步 NOTIFY 返回结果</li>
 *   <li>控制类命令（*Cmd）部分需要等待设备 200 OK 确认</li>
 *   <li>XML 元素名大小写严格遵循 GB/T 28181-2022 规范原文</li>
 * </ul>
 *
 * <p><b>重要:</b> 本文件仅作为参考骨架，实际集成时应在 {@code SIPCommander.java} 中实现，
 * 或在 {@code ISIPCommander} 接口中添加方法签名后实现。</p>
 *
 * @author wvp-upgrade
 * @since 2026-06-29
 */
public interface SIPCommanderSupplement {

    // ========================================================================
    // 一、PTZ 精准控制（改造项7，A.2.3.1.11）
    // ========================================================================

    /**
     * PTZ 精准控制命令
     *
     * <p>来源: 后端改造项7, 设计文档第10.1节, 2022版A.2.3.1.11</p>
     * <p>XML 结构:</p>
     * <pre>
     * &lt;Control&gt;
     *   &lt;CmdType&gt;DeviceControl&lt;/CmdType&gt;
     *   &lt;SN&gt;...&lt;/SN&gt;
     *   &lt;DeviceID&gt;...&lt;/DeviceID&gt;
     *   &lt;PTzPrecisectrl&gt;
     *     &lt;pan&gt;水平角度&lt;/pan&gt;
     *     &lt;Tilt&gt;垂直角度&lt;/Tilt&gt;   (注意: T 大写)
     *     &lt;zoom&gt;变倍倍数&lt;/zoom&gt;
     *   &lt;/PTzPrecisectrl&gt;
     * &lt;/Control&gt;
     * </pre>
     *
     * @param device    目标设备
     * @param channelId 通道编码
     * @param pan       水平转角（度），范围 -180.00 ~ 180.00
     * @param tilt      垂直转角（度），范围 -90.00 ~ 90.00
     * @param zoom      变倍倍数，范围 0 ~ 20
     */
    void ptzPreciseCmd(Device device, String channelId, double pan, double tilt, double zoom);

    // ========================================================================
    // 二、存储卡格式化（改造项8，A.2.3.1.13）
    // ========================================================================

    /**
     * 存储卡格式化命令
     *
     * <p>来源: 后端改造项8, 设计文档第10.1节, 2022版A.2.3.1.13</p>
     * <p>XML 元素名: FormatSdcard，承载于 DeviceControl 容器中</p>
     *
     * @param device    目标设备
     * @param channelId 通道编码
     */
    void formatSdcardCmd(Device device, String channelId);

    // ========================================================================
    // 三、目标跟踪（改造项9，A.2.3.1.14）
    // ========================================================================

    /**
     * 目标跟踪命令
     *
     * <p>来源: 后端改造项9, 设计文档第10.1节, 2022版A.2.3.1.14</p>
     * <p>XML 元素名: TargetTrack，承载于 DeviceControl 容器中</p>
     * <p>action 取值: "Auto"（自动跟踪）/ "Manual"（手动跟踪）</p>
     *
     * @param device    目标设备
     * @param channelId 通道编码
     * @param action    跟踪模式: Auto / Manual
     */
    void targetTrackCmd(Device device, String channelId, String action);

    // ========================================================================
    // 四、设备软件升级（改造项10，A.2.3.1.12）
    // ========================================================================

    /**
     * 设备软件升级命令
     *
     * <p>来源: 后端改造项10, 设计文档第10.1节, 2022版A.2.3.1.12</p>
     * <p>XML 元素名: DeviceUpgrade，核心字段:</p>
     * <ul>
     *   <li>FirmWare: 固件文件名</li>
     *   <li>FileURL: 固件文件下载地址</li>
     *   <li>manufacturer: 设备制造商</li>
     *   <li>sessionID: 会话标识（32~128字节）</li>
     * </ul>
     *
     * @param device       目标设备
     * @param channelId    通道编码
     * @param firmware     固件文件名
     * @param fileUrl      固件文件下载 URL
     * @param manufacturer 设备制造商
     * @param sessionId    会话 ID
     */
    void deviceUpgradeCmd(Device device, String channelId, String firmware,
            String fileUrl, String manufacturer, String sessionId);

    /**
     * 固件文件上传处理
     *
     * <p>将前端上传的固件文件保存到服务器可访问的目录，返回文件访问 URL</p>
     *
     * @param deviceId 设备编码
     * @param file     固件文件（MultipartFile）
     * @return 文件在服务器上的可访问 URL
     */
    String uploadFirmwareFile(String deviceId, MultipartFile file);

    // ========================================================================
    // 五、2022版新增查询（改造项11~13）
    // ========================================================================

    /**
     * 看守位信息查询
     *
     * <p>来源: 后端改造项11, 设计文档第11.1节, 2022版A.2.4.10</p>
     * <p>CmdType: HomepositionQuery，Response 返回 HomePosition 节点</p>
     * <p>响应字段: Enabled(true/false), PresetID, ResetTime</p>
     *
     * @param device    目标设备
     * @param channelId 通道编码
     */
    void homePositionQueryCmd(Device device, String channelId);

    /**
     * 巡航轨迹查询
     *
     * <p>来源: 后端改造项12, 设计文档第11.1节, 2022版A.2.4.11</p>
     * <p>CmdType: cruiseTrackQuery，请求可带 CruiseTrackListID 或 CruiseTrackID</p>
     *
     * @param device      目标设备
     * @param channelId   通道编码
     * @param trackListId 轨迹列表 ID（可选，null 表示查询所有）
     */
    void cruiseTrackQueryCmd(Device device, String channelId, Integer trackListId);

    /**
     * PTZ 精准状态查询
     *
     * <p>来源: 后端改造项13, 设计文档第11.1节, 2022版A.2.4.13</p>
     * <p>CmdType: pTZposition，Response 返回 Pan/Tilt/Zoom 精准数值</p>
     *
     * @param device    目标设备
     * @param channelId 通道编码
     */
    void ptzPreciseStatusQueryCmd(Device device, String channelId);

    /**
     * 存储卡状态查询
     *
     * <p>来源: 后端改造项14, 设计文档第11.1节, 2022版A.2.4.14</p>
     * <p>CmdType: SDcardStatus，Response 返回 Status/Capacity/RemainCapacity</p>
     *
     * @param device    目标设备
     * @param channelId 通道编码
     */
    void storageCardStatusQueryCmd(Device device, String channelId);

    // ========================================================================
    // 六、图像抓拍配置（改造项15，2022版9.14）
    // ========================================================================

    /**
     * 图像抓拍配置命令
     *
     * <p>来源: 后端改造项15, 设计文档第12.2节, 2022版9.14</p>
     * <p>XML 结构:</p>
     * <pre>
     * &lt;Control&gt;
     *   &lt;CmdType&gt;DeviceControl&lt;/CmdType&gt;
     *   &lt;SN&gt;...&lt;/SN&gt;
     *   &lt;DeviceID&gt;...&lt;/DeviceID&gt;
     *   &lt;SnapConfig&gt;
     *     &lt;snapNum&gt;连拍张数&lt;/snapNum&gt;
     *     &lt;Interval&gt;抓拍间隔(秒)&lt;/Interval&gt;
     *     &lt;uploaduRL&gt;上传路径&lt;/uploaduRL&gt;
     *     &lt;sessionID&gt;会话ID&lt;/sessionID&gt;
     *   &lt;/SnapConfig&gt;
     * &lt;/Control&gt;
     * </pre>
     * <p>注: Resolution 字段为后端扩展字段（由前端传入），非 GB/T 28181-2022 规范原字段</p>
     *
     * @param device     目标设备
     * @param channelId  通道编码
     * @param resolution 分辨率枚举值（0=CIF, 1=4CIF, 2=D1, 3=720P, 4=1080P）
     * @param snapNum    连拍张数（1~10）
     * @param interval   抓拍间隔（秒）
     * @param uploadUrl  抓拍图像上传路径
     * @param sessionId  会话 ID
     */
    void snapshotConfigCmd(Device device, String channelId, int resolution,
            int snapNum, int interval, String uploadUrl, String sessionId);
}
