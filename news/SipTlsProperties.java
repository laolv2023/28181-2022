package com.genersoft.iot.vmp.conf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SIP TLS 加密传输配置
 * <p>
 * 改造项30：TLS 加密传输配置——支持 GB/T 28181-2022 强化的安全传输要求，
 * 通过应用层配置项启用 TLS、加载密钥库与信任库，实现 SIP 信令通道的端到端加密。
 * </p>
 * <p>
 * <b>规范依据：</b><br>
 * 来源：设计文档第8.2节（TLS 加密传输），2022版 8.2 信令安全传输。<br>
 * 协议要求：2022 版明确支持基于 TLS 的 SIP 信令加密传输，密钥与证书采用国密或国际算法，
 * 默认端口 5061（TLS over TCP）。
 * </p>
 * <p>
 * <b>application.yml 配置示例：</b>
 * <pre>
 * sip:
 *   tls:
 *     enabled: true
 *     key-store: classpath:sip/server.p12
 *     key-store-password: changeit
 *     key-store-type: PKCS12
 *     trust-store: classpath:sip/ca.p12
 *     trust-store-password: changeit
 *     trust-store-type: PKCS12
 *     server-port: 5061
 * </pre>
 * </p>
 *
 * @author wvp-upgrade
 */
@Configuration
@ConfigurationProperties(prefix = "sip.tls")
public class SipTlsProperties {

    /**
     * 是否启用 TLS 加密传输
     * <p>
     * 改造项30：来源 设计文档第8.2节，2022版 8.2。<br>
     * 默认 false，启用后 SIP 信令通过 TLS 通道传输。
     * </p>
     */
    private boolean enabled = false;

    /**
     * 服务端密钥库路径
     * <p>
     * 支持 classpath:、file: 前缀，或绝对路径。
     * </p>
     */
    private String keyStore;

    /**
     * 服务端密钥库密码
     */
    private String keyStorePassword;

    /**
     * 服务端密钥库类型
     * <p>
     * 默认 PKCS12，可选 JKS、PKCS12、PKCS11。
     * </p>
     */
    private String keyStoreType = "PKCS12";

    /**
     * 信任库路径
     * <p>
     * 用于校验客户端证书，支持 classpath:、file: 前缀，或绝对路径。
     * </p>
     */
    private String trustStore;

    /**
     * 信任库密码
     */
    private String trustStorePassword;

    /**
     * 信任库类型
     * <p>
     * 默认 PKCS12，可选 JKS、PKCS12、PKCS11。
     * </p>
     */
    private String trustStoreType = "PKCS12";

    /**
     * TLS 监听端口
     * <p>
     * 来源 2022版 8.2，默认端口 5061（TLS over TCP）。
     * </p>
     */
    private int serverPort = 5061;

    /**
     * 是否需要客户端证书认证（双向 TLS）
     * <p>
     * 默认 false，启用后客户端必须提供有效证书。
     * </p>
     */
    private boolean needClientAuth = false;

    /**
     * 默认构造方法
     */
    public SipTlsProperties() {
    }

    /**
     * 获取是否启用 TLS
     *
     * @return true 表示启用 TLS 加密传输
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用 TLS
     *
     * @param enabled 是否启用 TLS
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取服务端密钥库路径
     *
     * @return 密钥库路径
     */
    public String getKeyStore() {
        return keyStore;
    }

    /**
     * 设置服务端密钥库路径
     *
     * @param keyStore 密钥库路径
     */
    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    /**
     * 获取服务端密钥库密码
     *
     * @return 密钥库密码
     */
    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    /**
     * 设置服务端密钥库密码
     *
     * @param keyStorePassword 密钥库密码
     */
    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    /**
     * 获取服务端密钥库类型
     *
     * @return 密钥库类型
     */
    public String getKeyStoreType() {
        return keyStoreType;
    }

    /**
     * 设置服务端密钥库类型
     *
     * @param keyStoreType 密钥库类型
     */
    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    /**
     * 获取信任库路径
     *
     * @return 信任库路径
     */
    public String getTrustStore() {
        return trustStore;
    }

    /**
     * 设置信任库路径
     *
     * @param trustStore 信任库路径
     */
    public void setTrustStore(String trustStore) {
        this.trustStore = trustStore;
    }

    /**
     * 获取信任库密码
     *
     * @return 信任库密码
     */
    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    /**
     * 设置信任库密码
     *
     * @param trustStorePassword 信任库密码
     */
    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    /**
     * 获取信任库类型
     *
     * @return 信任库类型
     */
    public String getTrustStoreType() {
        return trustStoreType;
    }

    /**
     * 设置信任库类型
     *
     * @param trustStoreType 信任库类型
     */
    public void setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType;
    }

    /**
     * 获取 TLS 监听端口
     *
     * @return TLS 监听端口
     */
    public int getServerPort() {
        return serverPort;
    }

    /**
     * 设置 TLS 监听端口
     *
     * @param serverPort TLS 监听端口
     */
    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    /**
     * 获取是否需要客户端证书认证
     *
     * @return true 表示需要客户端证书
     */
    public boolean isNeedClientAuth() {
        return needClientAuth;
    }

    /**
     * 设置是否需要客户端证书认证
     *
     * @param needClientAuth 是否需要客户端证书
     */
    public void setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }

    /**
     * 校验配置是否完整可用
     * <p>
     * 改造项30：来源 设计文档第8.2节，2022版 8.2。<br>
     * 启用 TLS 时，必须提供密钥库与密钥库密码；启用客户端认证时还必须提供信任库。
     * </p>
     *
     * @return true 表示配置完整
     */
    public boolean isValid() {
        if (!enabled) {
            // 未启用 TLS，无需校验
            return true;
        }
        if (keyStore == null || keyStore.isEmpty()) {
            return false;
        }
        if (keyStorePassword == null || keyStorePassword.isEmpty()) {
            return false;
        }
        if (needClientAuth && (trustStore == null || trustStore.isEmpty())) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "SipTlsProperties{" +
                "enabled=" + enabled +
                ", keyStore='" + keyStore + '\'' +
                ", keyStoreType='" + keyStoreType + '\'' +
                ", trustStore='" + trustStore + '\'' +
                ", trustStoreType='" + trustStoreType + '\'' +
                ", serverPort=" + serverPort +
                ", needClientAuth=" + needClientAuth +
                '}';
    }
}
