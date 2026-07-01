package com.genersoft.iot.vmp.gb28181.auth.impl;

import com.genersoft.iot.vmp.gb28181.auth.DataIntegrityHelper;
import com.genersoft.iot.vmp.gb28181.auth.SM3DigestHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DataIntegrityHelper 默认实现（基于 SM3 摘要）
 * 注意: 当前使用 SM3 摘要替代 HMAC-SM3, 无密钥保护。
 * 生产环境应引入密钥参数, 使用 HMAC-SM3 进行带密钥的消息认证。
 */
@Component
public class DataIntegrityHelperImpl implements DataIntegrityHelper {
    private static final Logger log = LoggerFactory.getLogger(DataIntegrityHelperImpl.class);

    @Override
    public boolean verifyDigest(byte[] data, String expectHex) throws DataIntegrityException {
        if (data == null) throw new DataIntegrityException("数据不能为null");
        if (expectHex == null || expectHex.isEmpty()) throw new DataIntegrityException("期望摘要不能为空");
        try {
            String actual = SM3DigestHelper.digest(data);
            if (actual == null || actual.isEmpty()) throw new DataIntegrityException("SM3摘要计算失败");
            String normActual = actual.toLowerCase(java.util.Locale.ROOT);
            String normExpect = expectHex.trim().toLowerCase(java.util.Locale.ROOT);
            return java.security.MessageDigest.isEqual(
                    normActual.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    normExpect.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new DataIntegrityException("摘要验证异常", e);
        }
    }

    @Override
    public boolean verifyTimestamp(byte[] data, String timestamp) throws DataIntegrityException {
        if (data == null || timestamp == null) throw new DataIntegrityException("参数不能为null");
        // 时间戳验证: 检查时间戳是否在合理范围内（±5分钟）
        try {
            long ts = Long.parseLong(timestamp.trim());
            // 兼容秒和毫秒时间戳
            if (ts < 1000000000000L) ts = ts * 1000;
            long now = System.currentTimeMillis();
            return Math.abs(now - ts) < 5 * 60 * 1000;
        } catch (NumberFormatException e) {
            throw new DataIntegrityException("时间戳格式错误", e);
        }
    }

    @Override
    public String computeDigest(byte[] data) throws DataIntegrityException {
        if (data == null) throw new DataIntegrityException("数据不能为null");
        try {
            String result = SM3DigestHelper.digest(data);
            if (result == null || result.isEmpty()) throw new DataIntegrityException("SM3摘要计算失败");
            return result;
        } catch (Exception e) {
            throw new DataIntegrityException("摘要计算异常", e);
        }
    }
}
