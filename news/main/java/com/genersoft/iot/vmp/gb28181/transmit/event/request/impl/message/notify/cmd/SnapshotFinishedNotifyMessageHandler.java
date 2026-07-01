package com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.notify.cmd;



import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.bean.Platform;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.SIPRequestProcessorParent;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.vmp.gb28181.transmit.event.request.impl.message.notify.NotifyMessageHandler;
import com.genersoft.iot.vmp.gb28181.utils.GBProtocolVersionHelper;
import com.genersoft.iot.vmp.gb28181.utils.XmlUtil;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.header.Header;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备抓图完成通知消息处理器
 * <p>
 * 改造项：抓图结果上报通道，配合 2022 版设备抓图控制流程使用。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：spec_2022.txt 第2516行，对应 2022版抓图完成后设备主动上报抓图结果通知。<br>
 * CmdType：uploadsnapshotFinished<br>
 * </p>
 * <p>
 * <b>XML 结构示例：</b>
 * <pre>
 * &lt;?xml version="1.0" encoding="GB18030"?&gt;
 * &lt;Notify&gt;
 *     &lt;CmdType&gt;uploadsnapshotFinished&lt;/CmdType&gt;
 *     &lt;SN&gt;1&lt;/SN&gt;
 *     &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
 *     &lt;sessionID&gt;12345678&lt;/sessionID&gt;
 *     &lt;snapshotList num="3"&gt;
 *         &lt;snapshotFileID&gt;file-001&lt;/snapshotFileID&gt;
 *         &lt;snapshotFileID&gt;file-002&lt;/snapshotFileID&gt;
 *         &lt;snapshotFileID&gt;file-003&lt;/snapshotFileID&gt;
 *     &lt;/snapshotList&gt;
 * &lt;/Notify&gt;
 * </pre>
 * </p>
 * <p>
 * 字段说明：
 * <ul>
 *     <li>sessionID：抓图会话标识，与下发抓图命令时一致</li>
 *     <li>snapshotList：抓图结果列表，num 属性指示文件数量，0~10 个</li>
 *     <li>snapshotFileID：单个抓图文件标识</li>
 * </ul>
 * </p>
 *
 * @author wvp-upgrade
 */
@Component
public class SnapshotFinishedNotifyMessageHandler extends SIPRequestProcessorParent
        implements InitializingBean, IMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotFinishedNotifyMessageHandler.class);

    /**
     * 命令类型字符串
     * <p>来源：spec_2022.txt 第2516行</p>
     */
    private static final String CMD_TYPE = "uploadsnapshotFinished";

    /**
     * 抓图列表节点名
     */
    private static final String SNAPSHOT_LIST = "snapshotList";

    /**
     * 抓图文件 ID 节点名
     */
    private static final String SNAPSHOT_FILE_ID = "snapshotFileID";

    /**
     * 单次抓图最大文件数
     * 来源：spec_2022.txt 第2516行说明，0~10 个
     */
    private static final int MAX_SNAPSHOT_COUNT = 10;

    @Autowired
    private NotifyMessageHandler notifyMessageHandler;



    /**
     * Spring 容器初始化后回调，将当前处理器注册到 NotifyMessageHandler 中。
     * <p>
     * 注册 CmdType=uploadsnapshotFinished，使 NotifyMessageHandler
     * 收到该类型 Notify 时路由到本处理器。
     * </p>
     *
     * @throws Exception 初始化异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        notifyMessageHandler.addHandler(CMD_TYPE, this);
        logger.info("[抓图完成通知] 处理器注册成功，CmdType={}", CMD_TYPE);
    }

    /**
     * 获取当前处理器对应的命令类型
     *
     * @return 命令类型字符串
     */
    public String getCmdType() {
        return CMD_TYPE;
    }

    /**
     * 处理上级平台转发的抓图完成通知
     *
     * @param evt           SIP 请求事件
     * @param parentPlatform 上级平台
     */
    @Override
    public void handForPlatform(RequestEvent evt, Platform parentPlatform, Element element) {
        handleMessage(evt, element);
    }

    /**
     * 处理设备直接上报的抓图完成通知
     *
     * @param evt    SIP 请求事件
     * @param device 上报设备
     * @param element XML 根元素
     */
    @Override
    public void handForDevice(RequestEvent evt, Device device, Element element) {
        handleMessage(evt, element);
    }

    /**
     * 统一消息处理入口
     * <p>
     * 处理流程：
     * <ol>
     *     <li>解析 Notify 根节点，提取设备 ID 与会话 ID</li>
     *     <li>从 snapshotList 节点下读取所有 snapshotFileID（0~10 个）</li>
     *     <li>记录抓图结果列表，业务侧可基于会话 ID 进行回调通知</li>
     *     <li>回复 200 OK</li>
     * </ol>
     * </p>
     *
     * @param evt SIP 请求事件
     */
    private void handleMessage(RequestEvent evt, Element rootElement) {
        try {
            if (rootElement == null) {
                logger.warn("[抓图完成通知] XML 根节点为空，无法处理");
                // 验证deviceId合法性
        respondAck(evt, Response.BAD_REQUEST);
                return;
            }

            // 提取设备 ID
            String deviceId = XmlUtil.getText(rootElement, "DeviceID");
            // 提取会话 ID
            String sessionId = XmlUtil.getText(rootElement, "sessionID");
            // 提取 SN 序号
            String sn = XmlUtil.getText(rootElement, "SN");

            // 解析抓图文件列表（0~10 个）
            List<String> snapshotFileIds = parseSnapshotList(rootElement);

            logger.info("[抓图完成通知] 收到通知: deviceId={}, sessionId={}, sn={}, snapshotCount={}",
                    deviceId, sessionId, sn, snapshotFileIds.size());

            // 业务侧扩展：可基于 sessionId 将抓图结果回调给调用方
            // 例如：notifyCallbackService.onSnapshotFinished(sessionId, deviceId, snapshotFileIds);
            handleSnapshotResult(deviceId, sessionId, snapshotFileIds);

            // 注意: 当前仅记录日志, 业务处理需根据业务需求实现
        // 回复 200 OK
            respondAck(evt, Response.OK);

        } catch (Exception e) {
            logger.error("[抓图完成通知] 处理异常: {}", e.getMessage(), e);
            // 处理异常不应回复 200 OK, 回复 500 让设备知晓服务端处理失败
            respondAck(evt, Response.SERVER_INTERNAL_ERROR);
        }
    }

    /**
     * 解析抓图文件列表
     * <p>
     * 规范要求：snapshotList 下包含 0~10 个 snapshotFileID 节点，
     * 超过 10 个时仅取前 10 个，避免恶意构造导致解析过载。
     * </p>
     *
     * @param rootElement XML 根节点
     * @return 抓图文件 ID 列表，永远不会返回 null
     */
    @SuppressWarnings("unchecked")
    private List<String> parseSnapshotList(Element rootElement) {
        List<String> result = new ArrayList<>();
        Element snapshotListElement = rootElement.element(SNAPSHOT_LIST);
        if (snapshotListElement == null) {
            return result;
        }
        List<Element> fileElements = snapshotListElement.elements(SNAPSHOT_FILE_ID);
        if (fileElements == null || fileElements.isEmpty()) {
            return result;
        }
        // 限制最大数量，超过 MAX_SNAPSHOT_COUNT 的部分丢弃
        int limit = Math.min(fileElements.size(), MAX_SNAPSHOT_COUNT);
        for (int i = 0; i < limit; i++) {
            String fileId = fileElements.get(i).getTextTrim();
            if (!ObjectUtils.isEmpty(fileId)) {
                result.add(fileId);
            }
        }
        return result;
    }

    /**
     * 处理抓图结果业务
     * <p>
     * 默认仅打印日志，实际项目可对接业务回调、推送消息队列、写入存储等。
     * </p>
     *
     * @param deviceId        设备 ID
     * @param sessionId       会话 ID
     * @param snapshotFileIds 抓图文件 ID 列表
     */
    private void handleSnapshotResult(String deviceId, String sessionId, List<String> snapshotFileIds) {
        if (ObjectUtils.isEmpty(snapshotFileIds)) {
            logger.info("[抓图完成通知] 设备 {} 会话 {} 无抓图文件返回", deviceId, sessionId);
            return;
        }
        for (int i = 0; i < snapshotFileIds.size(); i++) {
            logger.info("[抓图完成通知] 设备 {} 会话 {} 第 {} 张抓图文件 ID: {}",
                    deviceId, sessionId, i + 1, snapshotFileIds.get(i));
        }
    }

    /**
     * 回复 SIP 响应
     * <p>
     * 响应头携带 X-GB-ver 头部，符合改造项1协议版本协商要求。
     * </p>
     *
     * @param evt  请求事件
     * @param code 响应码
     */
    private void respondAck(RequestEvent evt, int code) {
        try {
            Request request = evt.getRequest();
            ServerTransaction serverTransaction = getServerTransaction(evt);
            Response response = getMessageFactory().createResponse(code, request);

            // 改造项1：响应消息携带 X-GB-ver 头部
            // 来源：设计文档第4节，2022版 7.1 协议版本协商
            Header gbVerHeader = getHeaderFactory().createHeader(
                    "X-GB-ver", GBProtocolVersionHelper.GB_PROTOCOL_VERSION);
            response.addHeader(gbVerHeader);

            serverTransaction.sendResponse(response);
        } catch (ParseException | SipException | InvalidArgumentException e) {
            logger.error("[抓图完成通知] 回复 {} 响应异常", code, e);
        } catch (Exception t) {
            logger.error("[抓图完成通知] 回复响应未知异常", t);
        }
    }
}
