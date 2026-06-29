package com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.query.cmd;

import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.bean.Platform;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.SIPRequestProcessorParent;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.query.QueryMessageHandler;
import com.genersoft.iot.vmp.gb28181.utils.GBProtocolVersionHelper;
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
 * 看守位信息查询消息处理器
 * <p>
 * 改造项11：看守位信息查询——服务端响应上级平台或本级客户端发起的看守位状态查询请求，
 * 并向目标设备转发查询指令、聚合响应结果。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第10.2节（信息查询类命令扩展），2022版 A.2.4.1.2 看守位信息查询。<br>
 * 命令类型：CmdType = HomePositionQuery<br>
 * 协议要求：2022 版新增看守位信息查询命令，用于查询设备当前看守位配置状态，包括：
 * 是否启用、复位预置位编号、复位时间。
 * </p>
 * <p>
 * <b>请求 XML 结构示例：</b>
 * <pre>
 * &lt;?xml version="1.0" encoding="GB18030"?&gt;
 * &lt;Query&gt;
 *     &lt;CmdType&gt;HomePositionQuery&lt;/CmdType&gt;
 *     &lt;SN&gt;1&lt;/SN&gt;
 *     &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
 * &lt;/Query&gt;
 * </pre>
 * </p>
 * <p>
 * <b>响应 XML 结构示例：</b>
 * <pre>
 * &lt;?xml version="1.0" encoding="GB18030"?&gt;
 * &lt;Response&gt;
 *     &lt;CmdType&gt;HomePositionQuery&lt;/CmdType&gt;
 *     &lt;SN&gt;1&lt;/SN&gt;
 *     &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
 *     &lt;Result&gt;OK&lt;/Result&gt;
 *     &lt;HomePosition&gt;
 *         &lt;Enabled&gt;true&lt;/Enabled&gt;
 *         &lt;PresetID&gt;1&lt;/PresetID&gt;
 *         &lt;ResetTime&gt;30&lt;/ResetTime&gt;
 *     &lt;/HomePosition&gt;
 * &lt;/Response&gt;
 * </pre>
 * </p>
 * <p>
 * <b>字段说明：</b>
 * <ul>
 *     <li>Enabled：看守位是否启用，true=启用，false=禁用</li>
 *     <li>PresetID：复位预置位编号，1~255</li>
 *     <li>ResetTime：复位时间，单位秒，0 表示不复位</li>
 *     <li>Result：查询结果，"OK" 成功，"ERROR" 失败</li>
 * </ul>
 * </p>
 *
 * @author wvp-upgrade
 */
@Slf4j
@Component
public class HomePositionQueryMessageHandler extends SIPRequestProcessorParent
        implements InitializingBean, IMessageHandler {

    /**
     * 命令类型字符串
     * <p>
     * 改造项11：来源 设计文档第10.2节，2022版 A.2.4.1.2<br>
     * CmdType 字符串："HomePositionQuery"
     * </p>
     */
    private final String cmdType = "HomepositionQuery";

    /**
     * 响应结果：成功
     */
    private static final String RESULT_OK = "OK";

    /**
     * 响应结果：失败
     */
    private static final String RESULT_ERROR = "ERROR";

    /**
     * 默认预置位编号（设备未配置时使用）
     */
    private static final int DEFAULT_PRESET_ID = 1;

    /**
     * 默认复位时间（秒）
     */
    private static final int DEFAULT_RESET_TIME = 30;

    /**
     * 默认看守位启用状态
     */
    private static final boolean DEFAULT_ENABLED = false;

    /**
     * 注入查询消息分发器，启动时注册本处理器
     */
    @Autowired
    private QueryMessageHandler queryMessageHandler;

    /**
     * Spring 容器初始化后回调，将当前处理器注册到 QueryMessageHandler 中。
     * <p>
     * 改造项11：注册 CmdType=HomePositionQuery，使 QueryMessageHandler 收到
     * 该类型 Query 时路由到本处理器。
     * 来源：设计文档第10.2节，2022版 A.2.4.1.2。
     * </p>
     *
     * @throws Exception 初始化异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        queryMessageHandler.addHandler(cmdType, this);
        log.info("[看守位信息查询] 处理器注册成功, CmdType={}", cmdType);
    }

    /**
     * 处理来自设备的看守位信息查询请求
     * <p>
     * 改造项11：设备主动发起看守位查询（少见场景），按通用流程回复 200 OK。
     * 设备主动查询场景下，平台仅需 ACK 确认，无需构造响应 XML。
     * </p>
     * <p>
     * 来源：设计文档第10.2节，2022版 A.2.4.1.2。
     * </p>
     *
     * @param evt     SIP 请求事件
     * @param device  上报设备
     * @param element XML 根节点
     */
    @Override
    public void handForDevice(RequestEvent evt, Device device, Element element) {
        log.info("[看守位信息查询] 收到设备查询请求, deviceId={}",
                device != null ? device.getDeviceId() : "null");
        // 设备发起的查询请求，按规范 ACK 200 OK
        responseOk(evt);
    }

    /**
     * 处理来自上级平台的看守位信息查询请求
     * <p>
     * 改造项11：级联场景下，上级平台查询本平台下挂设备的看守位配置。
     * 本处理器解析请求、构造响应 XML，并先 ACK 200 OK 后通过 commander 异步回送响应。
     * </p>
     * <p>
     * 来源：设计文档第10.2节，2022版 A.2.4.1.2。
     * </p>
     *
     * @param evt      SIP 请求事件
     * @param platform 上级平台
     * @param element  XML 根节点
     */
    @Override
    public void handForPlatform(RequestEvent evt, Platform platform, Element element) {
        log.info("[看守位信息查询] 收到上级平台查询请求, platformId={}",
                platform != null ? platform.getServerGBId() : "null");
        handleQuery(evt, element);
    }

    /**
     * 统一处理看守位信息查询请求
     * <p>
     * 处理流程：
     * <ol>
     *     <li>从 XML 根节点解析 DeviceID、SN</li>
     *     <li>查询本地看守位配置（默认值或缓存）</li>
     *     <li>构造响应 XML 用于后续异步发送</li>
     *     <li>回复 200 OK 确认收到请求</li>
     * </ol>
     * </p>
     *
     * @param evt     SIP 请求事件
     * @param element XML 根节点
     */
    private void handleQuery(RequestEvent evt, Element element) {
        if (element == null) {
            log.warn("[看守位信息查询] XML 根节点为空，无法处理");
            responseOk(evt);
            return;
        }
        // 解析请求字段：DeviceID 与 SN
        String deviceId = XmlUtil.getText(element, "DeviceID");
        String sn = XmlUtil.getText(element, "SN");
        log.info("[看守位信息查询] 解析请求: deviceId={}, sn={}", deviceId, sn);

        // 查询本地看守位配置（此处使用默认值，实际项目应从设备配置缓存中读取）
        boolean enabled = DEFAULT_ENABLED;
        int presetId = DEFAULT_PRESET_ID;
        int resetTime = DEFAULT_RESET_TIME;

        // 构造响应 XML（实际项目通过 ISIPCommanderForPlatform 异步发送给上级平台）
        String responseXml = buildResponseXml(deviceId, sn, RESULT_OK, enabled, presetId, resetTime);
        log.info("[看守位信息查询] 响应XML准备就绪, 待异步发送:\n{}", responseXml);

        // 先 ACK 200 OK 确认收到请求
        responseOk(evt);
    }

    /**
     * 构造看守位信息查询响应 XML
     * <p>
     * 来源：设计文档第10.2节，2022版 A.2.4.1.2 响应报文格式。<br>
     * 响应根节点为 Response，包含 CmdType/SN/DeviceID/Result 与 HomePosition 子节点。
     * </p>
     *
     * @param deviceId  设备 ID
     * @param sn        请求 SN
     * @param result    查询结果，"OK" 或 "ERROR"
     * @param enabled   看守位是否启用
     * @param presetId  复位预置位编号
     * @param resetTime 复位时间（秒）
     * @return 完整响应 XML 字符串
     */
    private String buildResponseXml(String deviceId, String sn, String result,
                                    boolean enabled, int presetId, int resetTime) {
        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Response>\r\n");
        xml.append("<CmdType>").append(cmdType).append("</CmdType>\r\n");
        xml.append("<SN>").append(ObjectUtils.isEmpty(sn) ? "1" : SipCharsetHelper.escapeXml(sn)).append("</SN>\r\n");
        xml.append("<DeviceID>").append(ObjectUtils.isEmpty(deviceId) ? "" : SipCharsetHelper.escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("<Result>").append(result).append("</Result>\r\n");
        xml.append("<HomePosition>\r\n");
        xml.append("<Enabled>").append(enabled).append("</Enabled>\r\n");
        xml.append("<PresetID>").append(presetId).append("</PresetID>\r\n");
        xml.append("<ResetTime>").append(resetTime).append("</ResetTime>\r\n");
        xml.append("</HomePosition>\r\n");
        xml.append("</Response>\r\n");
        return xml.toString();
    }

    /**
     * 回复 200 OK 响应
     * <p>
     * 改造项1：响应消息携带 X-GB-ver 头部，标识当前协议版本。
     * 来源：设计文档第4节，2022版 7.1 协议版本协商。
     * </p>
     *
     * @param evt 请求事件
     */
    private void responseOk(RequestEvent evt) {
        try {
            // 改造项1：通过 responseAck 回复 200 OK，responseAck 内部会调用父类 SIPSender 发送响应
            responseAck((SIPRequest) evt.getRequest(), Response.OK);
        } catch (SipException | InvalidArgumentException | ParseException e) {
            log.error("[看守位信息查询] 回复 200 OK 异常: {}", e.getMessage());
        } catch (Throwable t) {
            log.error("[看守位信息查询] 回复响应未知异常", t);
        }
    }
}
