package com.genersoft.iot.vmp.gb28181.auth;

/**
 * 数据完整性保护接口
 * <p>
 * 改造项31：数据完整性保护接口——为 GB/T 28181-2022 高安全场景提供统一的完整性校验抽象，
 * 支持摘要校验与时间戳防重放两项核心能力。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第8.4节（数据完整性保护），2022版 8.4 数据完整性保护。<br>
 * 协议要求：2022 版要求信令与媒体数据需支持完整性校验，防止传输过程中被篡改；
 * 同时要求关键命令携带可信时间戳，防止重放攻击。
 * </p>
 * <p>
 * <b>实现要求：</b>
 * <ul>
 *     <li>摘要算法默认使用 SM3（国密），兼容 SHA-256（国际算法）</li>
 *     <li>时间戳校验窗口默认 ±300 秒，可由配置覆盖</li>
 *     <li>接口实现需线程安全，支持并发校验</li>
 * </ul>
 * </p>
 *
 * @author wvp-upgrade
 */
public interface DataIntegrityHelper {

    /**
     * 校验数据摘要
     * <p>
     * 改造项31：来源 设计文档第8.4节，2022版 8.4。<br>
     * 计算原始数据的摘要并与期望摘要对比，用于检测数据是否在传输过程中被篡改。
     * 默认采用 SM3 国密摘要算法，长度 256 位（32 字节，64 字符十六进制）。
     * </p>
     *
     * @param data       原始数据字节数组
     * @param expectHex  期望的摘要（十六进制字符串，不区分大小写）
     * @return true 表示摘要一致，数据完整
     * @throws DataIntegrityException 当输入参数非法或计算异常时抛出
     */
    boolean verifyDigest(byte[] data, String expectHex) throws DataIntegrityException;

    /**
     * 校验时间戳合法性
     * <p>
     * 改造项31：来源 设计文档第8.4节，2022版 8.4。<br>
     * 校验时间戳是否在允许的窗口内（默认 ±300 秒），防止重放攻击。
     * 时间戳格式：UTC 毫秒数（13 位数字字符串）或 ISO 8601 字符串。
     * </p>
     *
     * @param data      原始数据字节数组（用于与时间戳一起参与二次摘要，可选）
     * @param timestamp 时间戳字符串
     * @return true 表示时间戳合法且在允许窗口内
     * @throws DataIntegrityException 当时间戳格式非法或校验失败时抛出
     */
    boolean verifyTimestamp(byte[] data, String timestamp) throws DataIntegrityException;

    /**
     * 计算数据摘要
     * <p>
     * 改造项31：来源 设计文档第8.4节，2022版 8.4。<br>
     * 默认采用 SM3 国密摘要算法，返回 64 字符十六进制字符串。
     * </p>
     *
     * @param data 原始数据字节数组
     * @return 摘要的十六进制字符串（小写）
     * @throws DataIntegrityException 当计算异常时抛出
     */
    String computeDigest(byte[] data) throws DataIntegrityException;

    /**
     * 数据完整性校验异常
     * <p>
     * 封装完整性校验过程中的所有受检异常。
     * </p>
     */
    class DataIntegrityException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * 构造异常
         *
         * @param message 异常描述
         */
        public DataIntegrityException(String message) {
            super(message);
        }

        /**
         * 构造异常
         *
         * @param message 异常描述
         * @param cause   原始异常
         */
        public DataIntegrityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    // 审计修复P1-04: 以下为默认实现, 生产环境需提供具体实现类
}
