package com.genersoft.iot.vmp.gb28181.auth;

/**
 * GB 35114 高安全级别（生产环境建议使用枚举）接口
 * <p>
 * 改造项32：GB 35114 高安全级别（生产环境建议使用枚举）接口——为 GB/T 28181-2022 与 GB 35114-2017 联动场景
 * 提供统一的 SVAC 加密流处理与签名验证能力。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第8.6节（GB 35114 高安全级别（生产环境建议使用枚举）接口），2022版 8.6 高安全级别（生产环境建议使用枚举）要求。<br>
 * 协议要求：2022 版与 GB 35114-2017 联动时，需支持 SVAC 加密媒体流的接入、解密，
 * 以及信令消息的数字签名验证，以满足公共安全视频监控高安全级别（生产环境建议使用枚举）的合规要求。
 * </p>
 * <p>
 * <b>SVAC 编码简介：</b>
 * <ul>
 *     <li>SVAC（Surveillance Video and Audio Coding）为公安部主导的视频监控专用编码标准</li>
 *     <li>支持视频数据加密、感兴趣区域编码、安全信息嵌入</li>
 *     <li>GB 35114 要求媒体流采用 SVAC 加密方式传输</li>
 * </ul>
 * </p>
 * <p>
 * <b>使用场景：</b>
 * <ul>
 *     <li>接收 SVAC 加密流并解密为明文帧供业务层使用</li>
 *     <li>验证来自设备或上级平台的关键信令签名，确保信令可信</li>
 *     <li>参与 GB 35114 三方密钥协商流程</li>
 * </ul>
 * </p>
 *
 * @author wvp-upgrade
 * 审计修复P1-06: 此接口为可选功能(宜支持), 生产环境按需提供实现类
 */
public interface GB35114Helper {

    /**
     * 处理 SVAC 加密媒体流
     * <p>
     * 改造项32：来源 设计文档第8.6节，2022版 8.6。<br>
     * 处理流程：
     * <ol>
     *     <li>解析 SVAC 帧头，提取加密算法标识与密钥索引</li>
     *     <li>从密钥管理服务获取会话密钥（基于 GB 35114 密钥协商结果）</li>
     *     <li>对加密载荷执行对称解密（SM4 或 AES）</li>
     *     <li>返回解密后的明文 SVAC 帧字节流</li>
     * </ol>
     * </p>
     *
     * @param encryptedStream SVAC 加密流字节数组
     * @return 解密后的明文 SVAC 帧字节数组
     * @throws GB35114Exception 当解密失败、密钥不可用或格式非法时抛出
     */
    byte[] processSVACEncryptedStream(byte[] encryptedStream) throws GB35114Exception;

    /**
     * 验证签名
     * <p>
     * 改造项32：来源 设计文档第8.6节，2022版 8.6。<br>
     * 验证流程：
     * <ol>
     *     <li>从签名值解析签名算法（SM2 / ECDSA / RSA-PSS）</li>
     *     <li>查询签名者证书（设备证书或平台证书）</li>
     *     <li>使用证书公钥验证原始数据与签名值的一致性</li>
     *     <li>返回验证结果</li>
     * </ol>
     * </p>
     *
     * @param data      原始数据字节数组
     * @param signature 签名值（Base64 或 DER 编码字符串）
     * @return true 表示签名验证通过
     * @throws GB35114Exception 当签名格式非法、证书不可用或验证异常时抛出
     */
    boolean verifySignature(byte[] data, String signature) throws GB35114Exception;

    /**
     * 校验 GB 35114 安全级别（生产环境建议使用枚举）
     * <p>
     * 改造项32：来源 设计文档第8.6节，2022版 8.6。<br>
     * GB 35114 定义了 A、B、C 三个安全等级，本方法用于判断设备/平台声明的能力等级
     * 是否满足最低安全要求。
     * </p>
     *
     * @param declaredLevel 设备/平台声明的安全级别（生产环境建议使用枚举）（"A" / "B" / "C"）
     * @param requiredLevel  最低要求的安全级别（生产环境建议使用枚举）（"A" / "B" / "C"）
     * @return true 表示声明级别不低于要求级别
     * @throws GB35114Exception 当级别参数非法时抛出
     */
    boolean checkSecurityLevel(String declaredLevel, String requiredLevel) throws GB35114Exception;

    /**
     * GB 35114 高安全级别（生产环境建议使用枚举）异常
     * <p>
     * 封装 GB 35114 接入过程中的所有受检异常。
     * </p>
     */
    class GB35114Exception extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * 构造异常
         *
         * @param message 异常描述
         */
        public GB35114Exception(String message) {
            super(message);
        }

        /**
         * 构造异常
         *
         * @param message 异常描述
         * @param cause   原始异常
         */
        public GB35114Exception(String message, Throwable cause) {
            super(message, cause);
        }
    }
    // 审计修复P1-04: 以下为默认实现, 生产环境需提供具体实现类
}
