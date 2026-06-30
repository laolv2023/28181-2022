package com.genersoft.iot.vmp.gb28181.auth.impl;

import com.genersoft.iot.vmp.gb28181.auth.DataIntegrityHelper;
import com.genersoft.iot.vmp.gb28181.auth.SM3DigestHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DataIntegrityHelper 默认实现（基于 SM3-HMAC）
 */
@Component
public class DataIntegrityHelperImpl implements DataIntegrityHelper {
    // 注意: 当前实现使用 SM3 摘要替代 HMAC-SM3, 无密钥保护。
    // 生产环境应引入密钥参数, 使用 HMAC-SM3 (HmacSM3) 进行带密钥的消息认证。
    private static final Logger logger = LoggerFactory.getLogger(DataIntegrityHelperImpl.class);

    @Override
    public String computeHmac(byte[] data) {
        if (data == null) return "";
        String digest = SM3DigestHelper.digest(data);
        return digest != null ? digest : "";
    }

    @Override
    public boolean verifyHmac(byte[] data, String expectHex) {
        if (data == null || expectHex == null || expectHex.isEmpty()) return false;
        String actual = SM3DigestHelper.digest(data);
        if (actual == null || actual.isEmpty()) return false;
        return java.security.MessageDigest.isEqual(
                actual.toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expectHex.trim().toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
