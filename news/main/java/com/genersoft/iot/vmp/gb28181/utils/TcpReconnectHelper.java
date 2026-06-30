package com.genersoft.iot.vmp.gb28181.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * TCP 媒体传输重连机制工具类
 * <p>
 * 改造项26：TCP 媒体传输断线重连机制
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第13节，2022版 9.2.1 媒体传输要求。<br>
 * 规范要求：TCP 传输方式下，当媒体通道断开时应支持自动重连，
 * 重连间隔不少于 1000ms，重连次数不少于 3 次。
 * </p>
 *
 * @author wvp-upgrade
 */
public final class TcpReconnectHelper {

    private static final Logger logger = LoggerFactory.getLogger(TcpReconnectHelper.class);

    /**
     * 默认最大重连次数
     * <p>改造项26：规范要求重连次数不少于 3 次</p>
     */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 默认重连间隔（毫秒）
     * <p>改造项26：规范要求重连间隔不少于 1000ms</p>
     */
    public static final long DEFAULT_INTERVAL_MS = 1000L;

    /**
     * 默认连接超时（毫秒）
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;

    /**
     * 默认构造方法私有化，禁止实例化
     */
    private TcpReconnectHelper() {
        throw new IllegalStateException("工具类禁止实例化");
    }

    /**
     * 执行 TCP 媒体通道重连
     * <p>
     * 改造项26：媒体通道断开后自动重连。
     * </p>
     * <p>
     * 实现要点：
     * <ul>
     *     <li>重连间隔强制不少于 1000ms（规范要求）</li>
     *     <li>最大重连次数强制不少于 3 次（规范要求）</li>
     *     <li>每次重连尝试创建 Socket 连接，成功立即返回</li>
     *     <li>失败时按间隔睡眠后重试</li>
     * </ul>
     * </p>
     *
     * @param ip         目标 IP
     * @param port       目标端口
     * @param maxRetries 最大重连次数（小于 3 自动修正为 3）
     * @param intervalMs 重连间隔毫秒（小于 1000 自动修正为 1000）
     * @return 重连成功返回 true；全部失败返回 false
     */
    // 审计修复P2-18: 此方法为同步阻塞调用, 生产环境应从工作线程调用, 避免阻塞SIP信令线程
    public static boolean reconnectTcpMedia(String ip, int port, int maxRetries, long intervalMs) {
        // 参数校验与规范强制对齐
        if (ObjectUtils.isEmpty(ip)) {
            logger.warn("[TCP重连] IP 为空，无法重连");
            return false;
        }
        if (port <= 0 || port > 65535) {
            logger.warn("[TCP重连] 端口非法: {}", port);
            return false;
        }
        // 改造项26：规范强制最小间隔 1000ms
        if (intervalMs < DEFAULT_INTERVAL_MS) {
            logger.info("[TCP重连] 重连间隔 {} 小于规范最小值 1000ms, 自动修正", intervalMs);
            intervalMs = DEFAULT_INTERVAL_MS;
        }
        // 改造项26：规范强制最大重试次数不少于 3 次
        if (maxRetries < DEFAULT_MAX_RETRIES) {
            logger.info("[TCP重连] 最大重试次数 {} 小于规范最小值 3, 自动修正", maxRetries);
            maxRetries = DEFAULT_MAX_RETRIES;
        }

        logger.info("[TCP重连] 开始重连, ip={}, port={}, maxRetries={}, intervalMs={}",
                ip, port, maxRetries, intervalMs);

        long startTime = System.currentTimeMillis();
        long totalTimeoutMs = (long) maxRetries * (intervalMs + DEFAULT_CONNECT_TIMEOUT_MS);
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (System.currentTimeMillis() - startTime > totalTimeoutMs) { logger.warn("[TCP重连] 总体超时, 终止重连"); return false; }
            boolean connected = isPortReachable(ip, port, DEFAULT_CONNECT_TIMEOUT_MS);
            if (connected) {
                logger.info("[TCP重连] 第 {} 次重连成功, ip={}, port={}", attempt, ip, port);
                return true;
            }
            logger.warn("[TCP重连] 第 {}/{} 次重连失败, ip={}, port={}",
                    attempt, maxRetries, ip, port);

            // 最后一次重试不再睡眠
            if (attempt < maxRetries) {
                sleepQuietly(intervalMs);
            }
        }
        logger.error("[TCP重连] 全部 {} 次重连均失败, ip={}, port={}", maxRetries, ip, port);
        return false;
    }

    /**
     * 执行 TCP 媒体通道重连（使用默认参数）
     *
     * @param ip   目标 IP
     * @param port 目标端口
     * @return 重连成功返回 true；失败返回 false
     */
    public static boolean reconnectTcpMedia(String ip, int port) {
        return reconnectTcpMedia(ip, port, DEFAULT_MAX_RETRIES, DEFAULT_INTERVAL_MS);
    }

    /**
     * 尝试建立 TCP 连接
     *
     * @param ip           目标 IP
     * @param port         目标端口
     * @param timeoutMs    连接超时毫秒
     * @return true 表示连接成功
     */
    public static boolean isPortReachable(String ip, int port, int timeoutMs) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        if (port <= 0 || port > 65535) {
            return false;
        }
        if (timeoutMs <= 0) {
            timeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
        }
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            // 连接成功，立即关闭，仅用于通道探活
            socket.close();
            return true;
        } catch (IOException e) {
            logger.debug("[TCP重连] 连接失败: ip={}, port={}, err={}", ip, port, e.getMessage());
            return false;
        } finally {
            // 兜底关闭
            if (!socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // 忽略关闭异常
                }
            }
        }
    }

    /**
     * 静默睡眠，避免 InterruptedException 中断流程
     *
     * @param ms 睡眠毫秒数
     */
    private static void sleepQuietly(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("[TCP重连] 睡眠被中断, 可能影响重连节奏");
        }
    }
}
