# Patch 02: DigestServerAuthenticationHelper — SM3 摘要算法

> **目标文件**: `src/main/java/com/genersoft/iot/vmp/gb28181/auth/DigestServerAuthenticationHelper.java`
> **改造项**: 2（SIP 信令摘要算法从 MD5 更新为 SM3）
> **原始补丁**: `patches/02-DigestServerAuthenticationHelper.patch`（hunk 行号偏移，无法应用）
> **设计文档来源**: 第8.3节（第**742**行起），2022版8.3

---

## 修正说明

原始补丁注释引用的"第5.3节(第531~550行)"不准确。SM3 摘要算法相关内容在**第8.3节**（设计文档的第742行起）。以下使用修正后的行号。

---

## 变更 1: 新增常量

```java
// === 改造项2: SM3摘要算法支持 ===
// 来源: 设计文档第8.3节(第742行起), 2022版8.3

import cn.hutool.crypto.SmUtil;

/** 2022版默认摘要算法: SM3 */
public static final String DEFAULT_ALGORITHM_2022 = "SM3";

/** 2016版默认摘要算法: MD5 (兼容) */
public static final String DEFAULT_ALGORITHM_2016 = "MD5";

/** 当前默认算法(配置驱动, 默认SM3) */
public static String DEFAULT_ALGORITHM = DEFAULT_ALGORITHM_2022;
```

## 变更 2: 新增 sm3Digest 方法

```java
/**
 * SM3摘要计算
 * 来源: 设计文档第8.3节(第742行起), 2022版8.3
 * 依赖: hutool-all (WVP已有依赖)
 */
private String sm3Digest(String data) {
    return SmUtil.sm3(data);
}
```

## 变更 3: 新增 calculateDigest 方法

```java
/**
 * 根据算法名称计算摘要
 * 来源: 设计文档第8.3节(第742行起), 2022版8.3
 */
private String calculateDigest(String data, String algorithm) {
    if (DEFAULT_ALGORITHM_2022.equalsIgnoreCase(algorithm)) {
        return sm3Digest(data);
    } else {
        byte[] mdbytes = messageDigest.digest(data.getBytes());
        return toHexString(mdbytes);
    }
}
```

## 变更 4: 新增 selectAlgorithm 方法

```java
/**
 * 根据设备版本选择摘要算法
 * 来源: 设计文档第16.2节兼容建议
 */
private String selectAlgorithm(Device device) {
    if (device != null && device.isVersion2022()) {
        return DEFAULT_ALGORITHM_2022;
    }
    return DEFAULT_ALGORITHM_2016;
}
```

## 变更 5: 修改 doAuthenticatePlainTextPassword 方法

将原第203行 `messageDigest.digest(A1.getBytes())` 替换为：
```java
String algorithm = selectAlgorithm(device);
String ha1 = calculateDigest(A1, algorithm);
```
