package com.genersoft.iot.vmp.gb28181.auth.impl;

import com.genersoft.iot.vmp.gb28181.auth.GB35114Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * GB35114Helper 默认实现（桩实现）
 * 生产环境需替换为基于国密 SM2 签名算法的完整实现。
 */
@Component
public class GB35114HelperImpl implements GB35114Helper {
    private static final Logger log = LoggerFactory.getLogger(GB35114HelperImpl.class);

    @Override
    public boolean verifySignature(byte[] data, String signature) throws GB35114Exception {
        log.warn("[GB35114] verifySignature 桩实现, 返回 false");
        return false;
    }

    @Override
    public boolean checkSecurityLevel(String declaredLevel, String requiredLevel) throws GB35114Exception {
        if (declaredLevel == null || requiredLevel == null) {
            throw new GB35114Exception("安全级别参数不能为null");
        }
        // 安全级别比较: GB 35114 仅定义 A/B/C 三个安全等级
        // 审计修复 56_B-04: 移除不规范 D 级别
        int declaredRank = "ABC".indexOf(declaredLevel);
        int requiredRank = "ABC".indexOf(requiredLevel);
        if (declaredRank < 0) {
            log.warn("[GB35114] 设备声明了非标准安全级别: {}, 仅支持 A/B/C", declaredLevel);
        }
        if (requiredRank < 0) {
            log.warn("[GB35114] 要求的非标准安全级别: {}, 仅支持 A/B/C", requiredLevel);
        }
        return declaredRank >= 0 && declaredRank >= requiredRank;
    }
}
