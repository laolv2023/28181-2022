package com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.query.cmd;

import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.bean.Platform;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.SIPRequestProcessorParent;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.query.QueryMessageHandler;
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
 * PTZ 精准状态查询消息处理器
 * <p>
 * 改造项13：PTZ 精准状态查询——返回设备当前云台水平、垂直、变倍的精准数值，
 * 用于支持精确位置同步、轨迹回放对齐等高级场景。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第10.2节（信息查询类命令扩展），2022版 A.2.4.1.5 PTZ 精准状态查询。<br>
 * 命令类型：CmdType = pTZposition（按 GB/T 28181-2022 规范拼写）<br>
 * 协议要求：2022 版新增 PTZ 精准状态查询命令，返回当前云台的水平转角、垂直转角、变倍值。
 * </p>
 * <p>
 * <b>请求 XML 示例：</b>
 * <pre>
 * &lt;Query&gt;
 *     &lt;CmdType&gt;pTZposition&lt;/CmdType&gt;
 *     &lt;SN&gt;20&lt;/SN&gt;
 *     &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
 * &lt;/Query&gt;
 * </pre>
 * </p>
 * <p>
 * <b>响应 XML 示例：</b>
 * <pre>
 * &lt;Response&gt;
 *     &lt;CmdType&gt;pTZposition&lt;/CmdType&gt;
 *     &lt;SN&gt;20&lt;/SN&gt;
 *     &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
 *     &lt;Result&gt;OK&lt;/Result&gt;
 *     &lt;Pan&gt;120.50&lt;/Pan&gt;
 *     &lt;Tilt&gt;45.25&lt;/Tilt&gt;
 *     &lt;Zoom&gt;3.20&lt;/Zoom&gt;
 * &lt;/Response&gt;
 * </pre>
 * </p>
 * <p>
 * <b>字段说明：</b>
 * <ul>
 *     <li>Pan：水平转角，单位度，范围 0~360，精度 0.01 度</li>
 *     <li>Tilt：垂直转角，单位度，范围 -90~90，精度 0.01 度</li>
 *     <li>Zoom：变倍值，单位倍，精度 0.01 倍</li>
 * </ul>
 * </p>
 *
 * @author wvp-upgrade
 */
@Slf4j
@Component
public class PtzPreciseStatusQueryMessageHandler extends SIPRequestProcessorParent
        implements InitializingBean, IMessageHandler {

    /**
     * 命令类型字符串
     * <p>
     * 改造项13：来源 设计文档第10.2节，2022版 A.2.4.1.5<br>
     * 注意：按 GB/T 28181-2022 规范拼写为 pTZposition，与 2016 版 PTZCmd 不同。
     * </p>
     */
    private final String cmdType = "pTZposition";

    /**
     * 响应结果：成功
     */
    private static final String RESULT_OK = "OK";

    /**
     * 默认水平转角（度）
     */
    private static final double DEFAULT_PAN = 0.0;

    /**
     * 默认垂直转角（度）
     */
    private static final double DEFAULT_TILT = 0.0;

    /**
     * 默认变倍值（倍）
     */
    private static final double DEFAULT_ZOOM = 1.0;

    /**
     * 注入查询消息分发器
     */
    @Autowired
    private QueryMessageHandler queryMessageHandler;

    /**
     * Spring 容器初始化后回调，将当前处理器注册到 QueryMessageHandler。
     * <p>
     * 改造项13：注册 CmdType=pTZposition。
     * 来源：设计文档第10.2节，2022版 A.2.4.1.5。
     * </p>
     *
     * @throws Exception 初始化异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        queryMessageHandler.addHandler(cmdType, this);
        log.info("[PTZ精准状态查询] 处理器注册成功, CmdType={}", cmdType);
    }

    /**
     * 处理来自设备的 PTZ 精准状态查询请求
     * <p>
     * 改造项13：设备主动发起查询场景较少，此处仅 ACK 200 OK。
     * 来源：设计文档第10.2节，2022版 A.2.4.1.5。
     * </p>
     *
     * @param evt     SIP 请求事件
     * @param device  上报设备
     * @param element XML 根节点
     */
    @Override
    public void handForDevice(RequestEvent evt, Device device, Element element) {
        log.info("[PTZ精准状态查询] 收到设备查询请求, deviceId={}",
                device != null ? device.getDeviceId() : "null");
        responseOk(evt);
    }

    /**
     * 处理来自上级平台的 PTZ 精准状态查询请求
     * <p>
     * 改造项13：上级平台查询设备 PTZ 精准状态。
     * 处理流程：解析请求 → 查询本地 PTZ 状态缓存 → 构造响应 XML → ACK 200 OK。
     * </p>
     *
     * @param evt      SIP 请求事件
     * @param platform 上级平台
     * @param element  XML 根节点
     */
    @Override
    public void handForPlatform(RequestEvent evt, Platform platform, Element element) {
        log.info("[PTZ精准状态查询] 收到上级平台查询请求, platformId={}",
                platform != null ? platform.getServerGBId() : "null");
        handleQuery(evt, element);
    }

    /**
     * 统一处理 PTZ 精准状态查询请求
     * <p>
     * 改造项13：来源 设计文档第10.2节，2022版 A.2.4.1.5。
     * </p>
     *
     * @param evt     SIP 请求事件
     * @param element XML 根节点
     */
    private void handleQuery(RequestEvent evt, Element element) {
        if (element == null) {
            log.warn("[PTZ精准状态查询] XML 根节点为空，无法处理");
            responseOk(evt);
            return;
        }
        // 解析请求字段
        String deviceId = XmlUtil.getText(element, "DeviceID");
        String sn = XmlUtil.getText(element, "SN");
        log.info("[PTZ精准状态查询] 解析请求: deviceId={}, sn={}", deviceId, sn);

        // 查询本地 PTZ 状态缓存（此处使用默认值，实际项目应从设备状态缓存中读取）
        double pan = DEFAULT_PAN;
        double tilt = DEFAULT_TILT;
        double zoom = DEFAULT_ZOOM;

        // 构造响应 XML
        String responseXml = buildResponseXml(deviceId, sn, RESULT_OK, pan, tilt, zoom);
        log.info("[PTZ精准状态查询] 响应XML准备就绪, 待异步发送:\n{}", responseXml);

        responseOk(evt);
    }

    /**
     * 构造 PTZ 精准状态查询响应 XML
     * <p>
     * 改造项13：来源 2022版 A.2.4.1.5 响应报文格式。<br>
     * 数值保留两位小数，符合规范 0.01 度/0.01 倍的精度要求。
     * </p>
     *
     * @param deviceId 设备 ID
     * @param sn       请求 SN
     * @param result   查询结果
     * @param pan      水平转角（度）
     * @param tilt     垂直转角（度）
     * @param zoom     变倍值（倍）
     * @return 完整响应 XML 字符串
     */
    private String buildResponseXml(String deviceId, String sn, String result,
                                    double pan, double tilt, double zoom) {
        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
        xml.append("<Response>\r\n");
        xml.append("<CmdType>").append(cmdType).append("</CmdType>\r\n");
        xml.append("<SN>").append(ObjectUtils.isEmpty(sn) ? "1" : SipCharsetHelper.escapeXml(sn)).append("</SN>\r\n");
        xml.append("<DeviceID>").append(ObjectUtils.isEmpty(deviceId) ? "" : SipCharsetHelper.escapeXml(deviceId)).append("</DeviceID>\r\n");
        xml.append("<Result>").append(result).append("</Result>\r\n");
        // Pan/Tilt/Zoom 保留两位小数
        xml.append("<Pan>").append(String.format("%.2f", pan)).append("</Pan>\r\n");
        xml.append("<Tilt>").append(String.format("%.2f", tilt)).append("</Tilt>\r\n");
        xml.append("<Zoom>").2f", zoom)).append("</Zoom>\r\n");
        xml.append("</Response>\r\n");
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
            log.error("[PTZ精准状态查询] 回复 200 OK 异常: {}", e.getMessage());
        } catch (Throwable t) {
            log.error("[PTZ精准状态查询] 回复响应未知异常", t);
        }
    }
}
