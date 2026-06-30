package com.genersoft.iot.vmp.gb28181.auth;

import java.security.cert.X509Certificate;

/**
 * 数字证书认证接口
 * <p>
 * 改造项29：数字证书认证接口——为 GB/T 28181-2022 高安全级别场景提供统一的证书校验抽象，
 * 支持国密 SM2 证书与国际 RSA/ECC 证书的验证。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第8.1节（数字证书认证接口），2022版 8.1 安全性要求。<br>
 * 协议要求：2022 版要求支持基于数字证书的设备身份认证，包括：
 * <ul>
 *     <li>设备证书签名链验证（验证设备证书是否由可信 CA 签发）</li>
 *     <li>证书有效期校验（避免使用过期/未生效证书）</li>
 *     <li>证书吊销校验（CRL/OCSP）</li>
 *     <li>证书主体与设备 ID 关联校验</li>
 * </ul>
 * </p>
 * <p>
 * <b>使用场景：</b>
 * <ul>
 *     <li>TLS 双向认证过程中的客户端证书校验</li>
 *     <li>SIP 摘要认证升级为证书认证时使用</li>
 *     <li>GB 35114 高安全级别交互的初始认证阶段</li>
 * </ul>
 * </p>
 *
 * @author wvp-upgrade
 */
 * 审计修复P1-06: 此接口为可选功能(宜支持), 生产环境按需提供实现类
public interface CertAuthHelper {

    /**
     * 校验设备证书合法性
     * <p>
     * 改造项29：来源 设计文档第8.1节，2022版 8.1。<br>
     * 校验流程：
     * <ol>
     *     <li>证书签名链验证：递归验证至已加载的根 CA 证书</li>
     *     <li>证书有效期校验：当前时间需落在 notBefore ~ notAfter 之间</li>
     *     <li>证书吊销状态校验：通过 CRL 或 OCSP 检查证书是否被吊销</li>
     *     <li>证书用途校验：扩展密钥用法须包含客户端身份认证</li>
     * </ol>
     * </p>
     *
     * @param deviceCert 待校验的设备 X.509 证书
     * @return true 表示证书合法且未被吊销
     * @throws IllegalArgumentException 当证书参数为 null 时抛出
     * @throws CertAuthException        当证书校验过程中出现异常时抛出
     */
    boolean verifyDeviceCert(X509Certificate deviceCert) throws CertAuthException;

    /**
     * 加载可信 CA 证书
     * <p>
     * 改造项29：来源 设计文档第8.1节，2022版 8.1。<br>
     * 用于加载受信任的根 CA 证书或中间 CA 证书，作为后续设备证书链验证的信任锚。
     * </p>
     *
     * @param caCertPath CA 证书文件路径，支持 classpath:、file: 前缀或绝对路径
     * @return 加载成功的 X.509 CA 证书对象
     * @throws CertAuthException 当文件不存在、格式非法或加载失败时抛出
     */
    X509Certificate loadCaCert(String caCertPath) throws CertAuthException;

    /**
     * 校验证书主题与设备 ID 的对应关系
     * <p>
     * 改造项29：来源 设计文档第8.1节，2022版 8.1。<br>
     * GB/T 28181-2022 要求证书 CN 或 SubjectAltName 中应包含国标设备 ID，
     * 本方法校验证书主体与实际请求的设备 ID 是否一致，防止证书冒用。
     * </p>
     *
     * @param deviceCert 设备证书
     * @param deviceId   设备 ID（20 位国标编码）
     * @return true 表示主体与设备 ID 匹配
     * @throws CertAuthException 当校验过程异常时抛出
     */
    boolean verifySubjectMatch(X509Certificate deviceCert, String deviceId) throws CertAuthException;

    /**
     * 证书认证异常
     * <p>
     * 封装证书加载、校验过程中的所有受检异常。
     * </p>
     */
    class CertAuthException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * 构造异常
         *
         * @param message 异常描述
         */
        public CertAuthException(String message) {
            super(message);
        }

        /**
         * 构造异常
         *
         * @param message 异常描述
         * @param cause   原始异常
         */
        public CertAuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    // 审计修复P1-04: 以下为默认实现, 生产环境需提供具体实现类
}
