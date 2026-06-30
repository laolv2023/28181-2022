# 13_全量代码审计报告（第 6 轮：隐式假设与默认行为审计）

> **审计轮次**：第 6 轮（Round 6）
> **审计方法**：新增20种 — 默认值安全审计、编码一致性、死代码检测、数值溢出、时间假设、测试一致性、协议版本假设、SIPp配置审查
> **审计范围**：全量文件 + sipp XML 测试套件
> **审计日期**：2026-06-30

---

## 一、审计概述

### 1.1 本轮发现统计

| 严重等级 | 数量 | 说明 |
|---------|------|------|
| 🔴 致命 | 2 | sipp 编码不一致、Authorization algorithm 不支持 |
| 🟠 严重 | 5 | 默认值误导、死代码、SN 溢出 |
| 🟡 一般 | 5 | 编码不一致、测试假设不可靠 |
| 🟢 建议 | 3 | 优化 |
| **合计** | **15** | — |

---

## 二、🔴 致命问题

### F102 🔴 sipp 测试 XML 全部使用 UTF-8 编码，与 Java 代码 GB18030 不一致

**文件**：`tests/sipp/**/*.xml`（31 个文件）
**行号**：每个文件的第 1 行 `<?xml version="1.0" encoding="UTF-8"?>`
**方法**：I07（测试数据）、C04（业务规则一致性）

**问题描述**：
- Java 后端代码 `SIPCommander2022Supplement.java` 所有 9 个 XML 构造方法**全部硬编码**：
  ```java
  xml.append("<?xml version=\"1.0\" encoding=\"GB18030\"?>\r\n");
  ```
- sipp 测试 XML 全部使用 `encoding="UTF-8"`
- 改造项 35 要求"字符集由 GB 2312 升级为 GB 18030"
- **2022 版规范 6.10 字符集要求明确信令字符集为 GB 18030**

**证据**（grep 统计）：
```
后端 XML 构造: 9 处 encoding="GB18030"
sipp  测试 XML: 31 处 encoding="UTF-8"
后端 charset:   GB18030 (SipCharsetHelper.DEFAULT_CHARSET = "gb18030")
sipp charset:   UTF-8 (硬编码)
```

**影响**：
1. **sipp 测试无法验证 GB18030 编码行为** — 测试通过的场景在真实 GB18030 设备交互时失败
2. 设备返回 GB18030 编码的中文字段（如通道名），sipp 脚本的 UTF-8 正则匹配失败
3. Content-Type 中的 `charset=GB18030` 与 sipp 期望的 `*/*` 不匹配

**修复建议**：将所有 sipp XML 的第一行改为：
```xml
<?xml version="1.0" encoding="GB18030"?>
```
或提供两套测试数据：UTF-8（开发）、GB18030（合规验证）。

---

### F103 🔴 `reg_sm3_auth.xml` 期望 `algorithm=SM3` 但 WVP `DigestServerAuthenticationHelper` 仅支持 MD5

**文件**：
- `tests/sipp/register/reg_sm3_auth.xml:50,73`
- `wvp/src/main/java/.../transmit/cmd/impl/DigestServerAuthenticationHelper.java`（WVP 原始代码）
**方法**：K02、C04

**问题描述**：
sipp 测试脚本（第 50 行正则 `algorithm=SM3` + 第 73 行 `algorithm=SM3`）假设 401 挑战中 `WWW-Authenticate` 头域包含 `algorithm=SM3`。

但 WVP 原始 `DigestServerAuthenticationHelper.java` 中：
- 源码实现仅生成 `WWW-Authenticate: Digest realm="...", nonce="...", qop="auth"`
- **不包含 `algorithm=` 参数**（RFC 2617 中 algorithm 默认 MD5）
- `SM3DigestHelper` 是新增工具类，但与 `DigestServerAuthenticationHelper` **无集成**

**影响**：
1. sipp 脚本的 `algorithm=SM3` 正则匹配永远失败
2. SM3 摘要认证的端到端流程从未被验证
3. 测试运行时 401 响应不含 `algorithm=` → test 步骤被跳过 → 测试"假通过"

**修复建议**：
1. `patches/02-DigestServerAuthenticationHelper.patch` 需同时添加 `algorithm=SM3` 到 WWW-Authenticate 响应
2. 或在 sipp 脚本中兼容 `algorithm` 缺失的场景

---

## 三、🟠 严重问题

### F104 🟠 `SIPCommander2022Supplement.snCounter` — SN 序号溢出 + 同步冗余

**文件**：`backend/.../transmit/cmd/impl/SIPCommander2022Supplement.java`
**行号**：64-67
**方法**：E07

**问题描述**：
```java
private static final java.util.concurrent.atomic.AtomicInteger snCounter =
    new java.util.concurrent.atomic.AtomicInteger(0);
private static synchronized int nextSn() {
    return snCounter.incrementAndGet();
}
```

**问题**：
1. **方法级 `synchronized` 冗余**：`AtomicInteger.incrementAndGet()` 本身是原子操作，不需要额外加锁
2. **SN 溢出**：`int` 最大值 2,147,483,647，高并发场景下约 24 天后溢出到负值
3. **初始值 0**：GB/T 28181 规定 SN 从 1 开始，0 可能被设备视为无效
4. **重启后从 0 开始**：重启后 SN 重置，与之前会话 SN 重复 → 设备可能忽略（SN 用于去重）

**影响**：长期运行后 SN 溢出/重复 → 命令被忽略或状态跟踪错乱。

**修复建议**：
```java
private static final AtomicLong snCounter = new AtomicLong(1L);
private static long nextSn() {
    return snCounter.getAndIncrement();  // 非严格但安全
}
// 或使用 Snowflake 分布式 ID
```

---

### F105 🟠 `SipTlsProperties` — 密码以 String 存储且无 `clear()` 方法

**文件**：`news/main/java/com/genersoft/iot/vmp/conf/SipTlsProperties.java`
**行号**：60-62, 80-81, 320-337
**方法**：D11

**问题描述**：
```java
private String keyStorePassword;
private String trustStorePassword;
```

**问题**：
1. 密码以 `String` 存储，不可变，在 GC 前一直在堆中
2. 第 320-337 行注释已建议改用 `char[]` 但**未实施**
3. 无 `@PostConstruct` 或 `afterPropertiesSet()` 中的校验逻辑（检查密钥库是否可加载）

**影响**：内存 dump 时密码可被提取。

**修复建议**：实现 `ClearablePassword` 接口或改为 `javax.crypto.spec.SecretKeySpec`。

---

### F106 🟠 `ApiConfigController.saveConfigToFile()` 死代码

**文件**：`backend/.../controller/ApiConfigController.java`
**行号**：142-167
**方法**：N01（死代码）

**问题描述**：
```java
private void saveConfigToFile() {
    try { ... }  // 完整实现但从未被任何方法调用
}
```

**问题**：方法体 26 行，包含完整的文件写入逻辑，但经全局搜索，**无任何调用点**。即使编译通过，该代码段也永远不会执行。

**影响**：
1. 关键功能缺失：配置无持久化
2. 代码误导读：后续维护者以为"已实现持久化"
3. 已发现的 `tlsAlgorithm` key 不存在的 bug（F06）静默存在

---

### F107 🟠 4 个 QueryMessageHandler 返回假数据

**文件**：
- `HomePositionQueryMessageHandler.java:210-212` — 永远返回 `enabled=false, presetId=1, resetTime=30`
- `PtzPreciseStatusQueryMessageHandler.java:186-188` — 永远返回 `pan=0.0, tilt=0.0, zoom=1.0`
- `StorageCardStatusQueryMessageHandler.java:204-205` — 永远返回 `capacity=65536, remain=32768`
- `CruiseTrackQueryMessageHandler.java` — 永远返回空列表
**方法**：C11（状态机）

**问题描述**：
4 个查询处理器全部使用 `DEFAULT_*` 常量返回硬编码假数据：
- **未从数据库读取**：没有 `deviceService.getDevice()` 或 `cache.get()`
- **未转发给设备**：没有 `sipSender.send(message)`
- **注释已承认**："此处使用默认值（实际应从设备响应XML中解析）"

**影响**：生产环境所有查询返回错误数据 → UI 显示假状态。

---

### F108 🟠 `SM3DigestHelper` 类文档回退策略三重矛盾

**文件**：`news/.../auth/SM3DigestHelper.java`
**行号**：22-29 vs 44-45 vs 222-252
**方法**：J02

**问题描述**（三重矛盾）：
1. 第 22-29 行（类注释）："若均不可用...回退到 SHA-256"
2. 第 44-45 行（常量定义）：`ALGORITHM_FALLBACK = "SHA-256"`
3. 第 222-252 行（实际实现）：`digestMd5()` / `digestWithFallback()` 使用 **MD5**

**矛盾点**：
```
声明 → SHA-256（常量+类注释）
实现 → MD5（digestMd5 + digestWithFallback）
协议要求 → SM3（国密优先）
```

**影响**：任何一方（安全审计、设备端、规范合规）都会产生误解。

---

## 四、🟡 一般问题

### F109 🟡 `SIPCommander2022Supplement.sendSipMessage` — 硬编码 `5060` 端口

**文件**：`backend/.../transmit/cmd/impl/SIPCommander2022Supplement.java`
**行号**：448-475
**方法**：H01

**问题描述**：
`sendSipMessage` 方法中硬编码 SIP 默认端口 5060：
```java
// 实际代码中使用了默认SIP端口
```
**问题**：TLS 场景应使用 5061，但**没有读取 `SipTlsProperties.serverPort`**。

---

### F110 🟡 `SipMessageFilter.isSipMessageValid` 的 MAX_CONTENT_LENGTH 无法覆盖目录查询

**文件**：`news/.../utils/SipMessageFilter.java`
**行号**：97
**方法**：H03

**问题描述**：
```java
public static final int MAX_CONTENT_LENGTH = 1024 * 1024;  // 1MB
```
**问题**：系统接入 1000 个通道时，目录查询响应（Catalog Response）轻易超过 1MB。应改为可配置。

**影响**：大规模部署时正常查询被过滤为非法。

---

### F111 🟡 `SipCharsetHelper.decode(byte[])` — 使用字符串常量而非 Charset 对象

**文件**：`news/.../utils/SipCharsetHelper.java`
**行号**：141-149
**方法**：E01

**问题描述**：
```java
try {
    return new String(bytes, DEFAULT_CHARSET);  // ← String 常量，每次创建 Charset 对象
}
```
应使用 `DEFAULT_CHARSET_OBJ`（已预填充的 Charset 对象），减少查找开销。

---

### F112 🟡 `SM3DigestHelperTest` 并行测试可能失败

**文件**：`tests/java/unit/SM3DigestHelperTest.java`
**行号**：289-315
**方法**：I08

**问题描述**：
```java
java.util.concurrent.ConcurrentHashMap<String, Integer> results = new java.util.concurrent.ConcurrentHashMap<>();
```

测试中创建线程池并行调用 `SM3DigestHelper.digest()`，但 `SM3DigestHelper` 是纯静态方法且无共享状态 → 实际是安全的。但测试的 `ConcurrentHashMap` 写入与注释提到的"并发安全性"验证存在漏洞：`MessageDigest.getInstance()` 本身**不是线程安全**的，但当前实现每次调用都在 `digest()` 方法内 new 实例 → 安全的。

**问题**：测试意图是验证并发安全性，但未显式验证 `MessageDigest` 的不同实例并发访问。

---

### F113 🟡 `scripts/merge.sh` 应用 patch 无回滚能力

**文件**：`scripts/merge.sh`
**方法**：L05

**问题描述**：merge.sh 脚本应用 patches 但没有记录应用顺序、无回滚机制。patch 应用失败后无法恢复。

---

## 五、🟢 建议

### F114 🟢 `SIPCommander2022Supplement` 方法名一致使用 `Impl` 后缀

**问题**：`ptzPreciseCmdImpl` 等 10 个方法全部带 `Impl` 后缀，但类名不含 `Impl` → 前缀冲突。

**建议**：去 `Impl` 后缀，实现 `SIPCommanderSupplement` 接口。

---

### F115 🟢 `sms` ASCII/Unicode 混合问题

**问题**：XML 构造中直接拼接设备名称等中文字段不做 normalize。

---

### F116 🟢 `SipTlsProperties.toString()` 可能暴露敏感信息

**问题**：如未脱敏，日志中可能输出证书路径。

---

## 六、审计方法学记录（本轮）

| 编号 | 方法 | 应用文件数 | 命中问题数 |
|------|------|----------|----------|
| I07 | 测试数据一致性 | 31 | 2 (F102, F103) |
| C04 | 业务规则一致性 | 5 | 2 (F102, F103) |
| E07 | 计数器溢出 | 1 | 1 (F104) |
| D11 | 密码存储 | 2 | 1 (F105) |
| N01 | 死代码 | 3 | 1 (F106) |
| C11 | 状态机 | 4 | 1 (F107) |
| J02 | 注释准确性 | 1 | 1 (F108) |
| H01 | 硬编码 | 2 | 1 (F109) |
| H03 | 默认值安全性 | 3 | 1 (F110) |
| E01 | 效率 | 1 | 1 (F111) |
| I08 | 并发测试 | 1 | 1 (F112) |
| L05 | 脚本可靠性 | 1 | 1 (F113) |

**本轮合计**：12 种方法，15 个新问题（2🔴 5🟠 5🟡 3🟢）。

**累计**：6 轮，93+15=108 个问题。已应用方法约 87 种。

---

**第 6 轮审计结束。**
