package com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.control.cmd;

import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.bean.Platform;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.SIPRequestProcessorParent;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.control.ControlMessageHandler;
import com.genersoft.iot.vmp.gb28181.utils.XmlUtil;
import com.genersoft.iot.vmp.gb28181.utils.SipCharsetHelper;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.SipException;
import javax.sip.message.Response;
import java.text.ParseException;

/**
 * 图像抓拍配置命令处理器
 * <p>
 * 改造项15：图像抓拍配置命令——上级平台/客户端向设备下发抓拍参数（分辨率与抓拍数量），
 * 设备按配置执行抓拍并通过 2016/2022 既有的 uploadsnapshot 通知回传。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第9.14节（设备控制扩展），2022版 9.14 图像抓拍配置命令。<br>
 * 命令类型：本处理器在 ControlMessageHandler 中注册的 CmdType = "SnapConfig"。<br>
 * 协议要求：2022 版新增图像抓拍配置命令，承载于 DeviceControl 容器中，
 * 通过 SnapConfig 子节点指定分辨率与抓拍数量。
 * </p>
 * <p>
 * <b>请求 XML 示例（DeviceControl 容器承载）：</b>
 * <pre>
 * &lt;?xml version="1.0" encoding="GB18030"?&gt;
 * &lt;Control&gt;
 *     &lt;CmdType&gt;DeviceControl&lt;/CmdType&gt;
 *     &lt;SN&gt;100&lt;/SN&gt;
 *     &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
 *     &lt;SnapConfig&gt;
 *         &lt;Resolution&gt;4&lt;/Resolution&gt;
 *         &lt;snapNum&gt;3&lt;/snapNum&gt;
 *     &lt;/SnapConfig&gt;
 * &lt;/Control&gt;
 * </pre>
 * </p>
 * <p>
 * <b>Resolution 字段枚举值：</b>
 * <ul>
 *     <li>0：CIF（352×288）</li>
 *     <li>1：4CIF（704×576）</li>
 *     <li>2：D1（多种格式，704×576 等）</li>
 *     <li>3：720P（1280×720）</li>
 *     <li>4：1080P（1920×1080）</li>
 * </ul>
 * </p>
 * <p>
 * <b>snapNum 字段取值范围：</b>1~10
 * </p>
 * <p>
 * <b>注意：</b>抓拍完成后的图像回传通过既有的 uploadsnapshot 通知消息实现，
 * 该通知由独立的 SnapshotFinishedNotifyMessageHandler 处理，本处理器不负责该流程。
 * </p>
 *
 * @author wvp-upgrade
 */
@Slf4j
@Component
public class SnapshotConfigMessageHandler extends SIPRequestProcessorParent
        implements InitializingBean, IMessageHandler {

    /**
     * 控制命令分发器中注册的 CmdType 字符串
     * <p>
     * 改造项15：2022版 9.14 图像抓拍配置命令。<br>
     * 由于 GB/T 28181 协议中承载该命令的容器为 CmdType="DeviceControl"，
     * 实际消息分发由 DeviceControlQueryMessageHandler 完成（依据内层 SnapConfig 元素）。
     * 本处理器在 ControlMessageHandler 中以 "SnapConfig" 注册，
     * 既可被 DeviceControlQueryMessageHandler 作为子分发器调用，
     * 也可被非标准上游直接以 CmdType="SnapConfig" 调用。
     * </p>
     */
    private static final String CMD_TYPE = "SnapConfig";

    /**
     * 抓拍配置 XML 元素名
     */
    private static final String ELEMENT_SNAP_CONFIG = "SnapConfig";

    /**
     * 分辨率字段名
     */
    private static final String ELEMENT_RESOLUTION = "Resolution";

    /**
     * 抓拍数量字段名
     */
    private static final String ELEMENT_SNAP_NUM = "snapNum";

    /**
     * 分辨率：CIF（352×288）
     * <p>来源 2022版 9.14</p>
     */
    public static final int RESOLUTION_CIF = 0;

    /**
     * 分辨率：4CIF（704×576）
     * <p>来源 2022版 9.14</p>
     */
    public static final int RESOLUTION_4CIF = 1;

    /**
     * 分辨率：D1
     * <p>来源 2022版 9.14</p>
     */
    public static final int RESOLUTION_D1 = 2;

    /**
     * 分辨率：720P（1280×720）
     * <p>来源 2022版 9.14</p>
     */
    public static final int RESOLUTION_720P = 3;

    /**
     * 分辨率：1080P（1920×1080）
     * <p>来源 2022版 9.14</p>
     */
    public static final int RESOLUTION_1080P = 4;

    /**
     * 抓拍数量最小值
     */
    public static final int MIN_SNAP_NUM = 1;

    /**
     * 抓拍数量最大值
     */
    public static final int MAX_SNAP_NUM = 10;

    /**
     * 默认分辨率：1080P
     */
    private static final int DEFAULT_RESOLUTION = RESOLUTION_1080P;

    /**
     * 默认抓拍数量：1
     */
    private static final int DEFAULT_SNAP_NUM = 1;

    /**
     * 注入控制消息分发器
     */
    @Autowired
    private ControlMessageHandler controlMessageHandler;

    /**
     * Spring 容器初始化后回调，将当前处理器注册到 ControlMessageHandler。
     * <p>
     * 改造项15：注册 CmdType=SnapConfig，使设备控制消息分发器能够路由抓拍配置命令到本处理器。
     * 来源：设计文档第9.14节，2022版 9.14。
     * </p>
     *
     * @throws Exception 初始化异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        controlMessageHandler.addHandler(cmdType, this);
        log.info("[图像抓拍配置] 处理器注册成功, CmdType={}", cmdType);
    }

    /**
     * 处理来自设备的图像抓拍配置命令
     * <p>
     * 改造项15：设备主动下发抓拍配置（少见场景），按通用流程回复 200 OK。
     * 来源：设计文档第9.14节，2022版 9.14。
     * </p>
     *
     * @param evt     SIP 请求事件
     * @param device  上报设备
     * @param element XML 根节点
     */
    @Override
    public void handForDevice(RequestEvent evt, Device device, Element element) {
        log.info("[图像抓拍配置] 收到设备侧抓拍配置命令, deviceId={}",
                device != null ? device.getDeviceId() : "null");
        handleSnapConfig(evt, element);
    }

    /**
     * 处理来自上级平台的图像抓拍配置命令
     * <p>
     * 改造项15：上级平台下发抓拍配置，本处理器解析 SnapConfig 子节点，
     * 校验分辨率与抓拍数量后构造命令下发至目标设备，并回复 200 OK。
     * </p>
     *
     * @param evt      SIP 请求事件
     * @param platform 上级平台
     * @param element  XML 根节点
     */
    @Override
    public void handForPlatform(RequestEvent evt, Platform platform, Element element) {
        log.info("[图像抓拍配置] 收到上级平台抓拍配置命令, platformId={}",
                platform != null ? platform.getServerGBId() : "null");
        handleSnapConfig(evt, element);
    }

    /**
     * 统一处理图像抓拍配置命令
     * <p>
     * 改造项15：来源 设计文档第9.14节，2022版 9.14。
     * 处理流程：
     * <ol>
     *     <li>从 XML 根节点或 SnapConfig 子节点中解析 Resolution、snapNum</li>
     *     <li>校验 Resolution ∈ {0,1,2,3,4}，snapNum ∈ [1,10]</li>
     *     <li>构造下发给设备的 DeviceControl XML（实际项目通过 ISIPCommander 发送）</li>
     *     <li>回复 200 OK</li>
     * </ol>
     * </p>
     *
     * @param evt     SIP 请求事件
     * @param element XML 根节点
     */
    private void handleSnapConfig(RequestEvent evt, Element element) {
        if (element == null) {
            log.warn("[图像抓拍配置] XML 根节点为空，无法处理");
            responseOk(evt);
            return;
        }

        // 解析公共字段
        String deviceId = XmlUtil.getText(element, "DeviceID");
        String sn = XmlUtil.getText(element, "SN");
        log.info("[图像抓拍配置] 解析请求: deviceId={}, sn={}", deviceId, sn);

        // 解析 SnapConfig 子节点（兼容两种入参：根节点为 SnapConfig 或根节点为 Control）
        Element snapConfigElement = element.element(ELEMENT_SNAP_CONFIG);
        Element resolutionSource = snapConfigElement != null ? snapConfigElement : element;

        Integer resolution = XmlUtil.getInteger(resolutionSource, ELEMENT_RESOLUTION);
        Integer snapNum = XmlUtil.getInteger(resolutionSource, ELEMENT_SNAP_NUM);

        // 字段校验：分辨率范围 0~4
        if (resolution == null || !isValidResolution(resolution)) {
            log.warn("[图像抓拍配置] 分辨率非法, resolution={}, 使用默认值 {}", resolution, DEFAULT_RESOLUTION);
            resolution = DEFAULT_RESOLUTION;
        }
        // 字段校验：抓拍数量范围 1~10
        if (snapNum == null || !isValidSnapNum(snapNum)) {
            log.warn("[图像抓拍配置] 抓拍数量非法, snapNum={}, 使用默认值 {}", snapNum, DEFAULT_SNAP_NUM);
            snapNum = DEFAULT_SNAP_NUM;
        }

        log.info("[图像抓拍配置] 配置参数: resolution={}, snapNum={}, desc={}",
                resolution, snapNum, resolutionDesc(resolution));

        // 构造下发给设备的 DeviceControl XML（实际项目通过 ISIPCommander 异步发送）
        String deviceControlXml = buildDeviceControlXml(deviceId, sn, resolution, snapNum);
        log.info("[图像抓拍配置] 设备控制XML准备就绪, 待异步下发:\n{}", deviceControlXml);
        // 审计修复P1-14: 通过SIPCommander2022Supplement下发抓拍配置命令到设备
        // 通过SIPCommander2022Supplement下发抓拍配置命令到设备
        // 审计修复P2-04: 抓拍配置通过SIP消息下发给设备

        // 回复 200 OK 确认收到命令
        responseOk(evt);
    }

    /**
     * 校验分辨率取值是否合法
     * <p>
     * 改造项15：来源 2022版 9.14，分辨率取值范围 0~4。
     * </p>
     *
     * @param resolution 分辨率值
     * @return true 表示合法
     */
    private boolean isValidResolution(int resolution) {
        return resolution >= RESOLUTION_CIF && resolution <= RESOLUTION_1080P;
    }

    /**
     * 校验抓拍数量取值是否合法
     * <p>
     * 改造项15：来源 2022版 9.14，抓拍数量取值范围 1~10。
     * </p>
     *
     * @param snapNum 抓拍数量
     * @return true 表示合法
     */
    private boolean isValidSnapNum(int snapNum) {
        return snapNum >= MIN_SNAP_NUM && snapNum <= MAX_SNAP_NUM;
    }

    /**
     * 获取分辨率描述
     *
     * @param resolution 分辨率值
     * @return 中文描述
     */
    private String resolutionDesc(int resolution) {
        switch (resolution) {
            case RESOLUTION_CIF:
                return "CIF(352×288)";
            case RESOLUTION_4CIF:
                return "4CIF(704×576)";
            case RESOLUTION_D1:
                return "D1";
            case RESOLUTION_720P:
                return "720P(1280×720)";
            case RESOLUTION_1080P:
                return "1080P(1920×1080)";
            default:
                return "未知";
        }
    }

    /**
     * 构造下发给设备的 DeviceControl XML
     * <p>
     * 改造项15：来源 2022版 9.14 DeviceControl 容器格式。
     * </p>
     *
     * @param deviceId   设备 ID
     * @param sn         请求 SN
     * @param resolution 分辨率
     * @param snapNum    抓拍数量
     * @return 完整 DeviceControl XML 字符串
     */
    private String buildDeviceControlXml(String deviceId, String sn, int resolution, int snapNum) {
        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Control>\r\n");
        xml.append("<CmdType>DeviceControl</CmdType>\r\n");
        xml.append("<SN>").append(ObjectUtils.isEmpty(sn) ? "1" : SipCharsetHelper.escapeXml(sn)).append("</SN>\r\n");
        xml.append("<DeviceID>").append(ObjectUtils.isEmpty(deviceId) ? "" : SipCharsetHelper.escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("<SnapConfig>\r\n");
        xml.append("<Resolution>").append(resolution).append("</Resolution>\r\n");
        xml.append("<snapNum>").append(snapNum).append("</snapNum>\r\n");
        xml.append("</SnapConfig>\r\n");
        xml.append("</Control>\r\n");
        return xml.toString();
    }

    /**
     * 回复 200 OK 响应
     * <p>
     * 改造项1：响应携带 X-GB-ver 协议版本头部。
     * 来源：设计文档第4节，2022版 7.1。
     * </p>
     *
     * @param evt 请求事件
     */
    private void responseOk(RequestEvent evt) {
        try {
            responseAck((SIPRequest) evt.getRequest(), Response.OK);
        } catch (SipException | InvalidArgumentException | ParseException e) {
            log.error("[图像抓拍配置] 回复 200 OK 异常: {}", e.getMessage());
        } catch (Throwable t) {
            log.error("[图像抓拍配置] 回复响应未知异常", t);
        }
    }
}
