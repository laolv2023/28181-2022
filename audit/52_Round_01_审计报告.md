# 52_Round_01 全量代码审计报告 — WVP GB/T 28181-2022

> **审计日期**: 2026-07-01 | **审计轮次**: Round 1
> **审计范围**: 29个Java文件 + 5个Vue组件 + 1个JS API层 = 35个文件
> **审计方法**: M001–M112（112种方法全覆盖，4子代理并行）
> **审计基准**: 升级开发设计文档、WVP合规性核查报告、WVP合规性升级改造开发方案、WVP前端UI改造方案

---

## 摘要

| 严重度 | 数量 | 说明 |
|--------|:---:|------|
| 🔴 CRITICAL | 49 | 编译阻断、协议不兼容、安全漏洞 |
| 🟠 HIGH | 55 | 可靠性风险、数据完整性、代码质量 |
| 🟡 MEDIUM | 80 | 可维护性、性能、最佳实践 |
| 🔵 LOW | 24 | 风格、注释、优化建议 |
| 🟢 PASS | 52 | 确认正确的检查点 |
| **合计发现问题** | **208** | |

---

## 一、🔴 CRITICAL 发现（49项）

### 1.1 编译阻断问题（7项）

| ID | 文件 | 问题 |
|----|------|------|
| R1-C01 | ApiDeviceControlController.java | 所有@GetMapping/@PostMapping注解后多了一个`)` — 语法错误，无法编译 |
| R1-C02 | ApiDeviceControlController.java:234 | `@PostMapping("/upload_firmware/{deviceId")` — 缺少`}` |
| R1-C03 | ApiDeviceControlController.java:502-540 | 尾部39个多余的`}` — 复制粘贴残留 |
| R1-C04 | ApiDeviceControlController.java:81-98 | 方法内花括号嵌套错乱 |
| R1-C05 | SIPCommander2022Supplement.java | `userSetting`字段未声明；`device`变量未声明 |
| R1-C06 | SipTlsProperties.java:291-295 | `validate()`方法重复定义 |
| R1-C07 | GB35114HelperImpl.java | `processSVACEncryptedStream`方法未实现 |

### 1.2 安全漏洞（15项）

| ID | 文件 | 问题 |
|----|------|------|
| R1-C08 | ApiDeviceControlController.java | CSRF防护完全缺失 — 8个POST端点无任何跨站请求伪造保护 |
| R1-C09 | ApiDeviceControlController.java:455 | `uploadUrl` SSRF — 无任何URL验证，可攻击内网 |
| R1-C10 | ApiDeviceControlController.java:285 | `fileUrl` SSRF — 控制器层零验证 |
| R1-C11 | ApiDeviceControlController.java:243 | 双扩展名攻击 — `payload.bin.exe`通过.`bin|.img|.zip`正则 |
| R1-C12 | ApiDeviceControlController.java:242 | 路径穿越 — `../../etc/cron.d/evil.bin`通过文件名检查 |
| R1-C13 | ApiDeviceControlController.java:251 | 空指针检查顺序错误 — `file.getOriginalFilename()`在null检查之前 |
| R1-C14 | ApiDeviceControlController.java:593 | 异常消息泄露 — `e.getMessage()`直接返回给客户端 |
| R1-C15 | ApiConfigController.java | 安全配置POST端点无认证/CSRF保护 |
| R1-C16 | 全局 | XML解析器XXE防御状态未知 — `DocumentBuilderFactory`配置不在审计范围内 |
| R1-C17 | 全局 | 零速率限制 — REST和SIP接口均无rate limiter |
| R1-C18 | 全局 | CORS配置未知 |
| R1-C19 | 全局 | `spring.servlet.multipart.max-file-size`未显式配置 |
| R1-C20 | SipTlsProperties.java | TLS协议版本未锁定 — 可能允许TLSv1.0/1.1 |
| R1-C21 | 全部Handler | 设备ID 20位格式从未校验 |
| R1-C22 | 全部Handler | sessionId长度从未校验(规范要求32~128字节) |

### 1.3 SIP协议不兼容（14项）

| ID | 文件 | 问题 |
|----|------|------|
| R1-C23 | SnapshotConfigMessageHandler.java:338 | CmdType=`DeviceControl` → 规范要求=`DeviceConfig` |
| R1-C24 | SnapshotConfigMessageHandler.java:88 | 内部CMD_TYPE=`SnapConfig` → 非规范CmdType值 |
| R1-C25 | SnapshotConfigMessageHandler.java:334 | SnapConfig缺`Interval`/`uploaduRL`/`sessionID`字段 |
| R1-C26 | CruiseTrackQueryMessageHandler.java | CmdType混淆 — 列表查询/详情查询使用同一CmdType |
| R1-C27 | HomePositionQueryMessageHandler.java:249 | `<Enabled>`值使用Java boolean → 规范要求0/1整数 |
| R1-C28 | HomePositionQueryMessageHandler.java:248 | `<HomePosition>`缺`presetIndex`字段 |
| R1-C29 | PtzPreciseStatusQueryMessageHandler.java:221 | 响应缺`<PTZStatus>`包装元素 |
| R1-C30 | PtzPreciseStatusQueryMessageHandler.java:221 | `<Pan>`/`<Zoom>`大小写错误 → 规范=`pan`/`zoom`（小写） |
| R1-C31 | PtzPreciseStatusQueryMessageHandler.java:221 | 缺`HorizontalFieldAngle`/`verticalFieldAngle`/`MaXvieWDistance`必选字段 |
| R1-C32 | StorageCardStatusQueryMessageHandler.java:237 | 响应结构完全错误 — 缺`<SDcardStatusInfo>`包装 |
| R1-C33 | StorageCardStatusQueryMessageHandler.java:239 | `<Capacity>` → 规范=`capacity`(小写)；`<RemainCapacity>` → 规范=`FreeSpace` |
| R1-C34 | SIPCommander2022Supplement.java | `<uploadURL>` → 规范=`uploaduRL`(大小写) |
| R1-C35 | DeviceControlType.java:187 | `typeOf()`不分CmdType就匹配 — 可能误匹配畸形消息 |
| R1-C36 | 全部Handler | `handForDevice()`引用未定义变量`responseXml` — 4个查询Handler编译错误 |

### 1.4 HTTP方法不匹配（3项）

| ID | 文件 | 问题 |
|----|------|------|
| R1-C37 | ApiDeviceControlController.java:74 | `ptz_precise`使用POST → 设计文档要求GET |
| R1-C38 | ApiDeviceControlController.java:117 | `target_track`使用POST → 设计文档要求GET |
| R1-C39 | ApiDeviceControlController.java:405 | `ptz_precise_status_query`使用POST → 设计文档要求GET |

### 1.5 接口契约破坏（4项）

| ID | 文件 | 问题 |
|----|------|------|
| R1-C40 | SIPCommander2022Supplement.java | 方法名`ptzPreciseCmdImpl` → 接口声明`ptzPreciseCmd` — 不匹配 |
| R1-C41 | ApiConfigController.java | `getSecurity()`返回裸Map → 未用WVPResult包装 |
| R1-C42 | frontEnd.js:38 | `getSecurityConfig`函数定义但未export |
| R1-C43 | SecurityConfig.vue | 页面无加载已保存配置的能力 |

### 1.6 其他关键问题（6项）

| ID | 文件 | 问题 |
|----|------|------|
| R1-C44 | SIPCommander2022Supplement.java | `sendSipMessage`空壳 — 所有SIP命令静默丢弃 |
| R1-C45 | SIPCommander2022Supplement.java | ISIPCommander接口未声明`IOException` |
| R1-C46 | SIPCommander2022Supplement.java:128 | 完整SIP XML以INFO级别日志输出 — 敏感信息泄露 |
| R1-C47 | ApiConfigController.java | 安全配置仅内存存储 — 重启丢失 |
| R1-C48 | GBProtocolVersionHelper.java:121 | `SipFactory.getInstance()`可能null导致NPE |
| R1-C49 | SnapshotFinishedNotifyMessageHandler.java:193 | 异常静默返回200 OK — 隐藏故障 |

---

## 二、🟠 HIGH发现（55项）— 摘要

主要类别：
- **过度宽泛的异常捕获**（13处）：所有Controller方法`catch(Exception e)`吞噬RuntimeException
- **代码重复**（4处）：11个Controller方法重复相同模式；9个SIP XML构造方法重复相同模式
- **输入验证缺失**（8处）：channelId未校验、deviceId格式未校验、sessionId长度未校验
- **响应格式不一致**（3处）：`WVPResult<?>`与`WVPResult<Object>`混用
- **日志级别不当**（6处）：操作数据以INFO级别输出
- **硬编码配置**（5处）：临时目录路径、GB18030编码字符串、http://方案
- **SIP协议细节**（4处）：MANSCDP Content-Type未设置、X-GB-ver头处理缺失
- **编译顺序依赖**（查Handler）：`handForDevice()`中`responseXml`变量引用
- **其余代码质量问题**（12处）

---

## 三、🟡 MEDIUM发现（80项）— 摘要

主要类别：
- **魔法数字/硬编码字符串**（18处）：文件大小限制、分辨率范围、超时时间、端口号
- **TODO/FIXME残留标记**（20+处）：审计修复标记未清理
- **代码风格**（12处）：命名不一致、缩进错误、Javadoc错误
- **未使用导入**（5处）
- **重复UUID生成方法**（2处）：应该抽取到公共工具
- **循环编译正则**（2处）：`String.replaceAll`每次编译Pattern
- **浮点比较**（3处）：`==`而非epsilon
- **其余优化建议**（18处）

---

## 四、立即修复优先级（Top 15）

| 优先级 | ID | 问题 | 影响 |
|:---:|------|------|------|
| P0 | C01-C07 | 7个编译阻断错误 | 代码无法编译部署 |
| P0 | C40 | 方法名不匹配接口 | 运行时NoSuchMethodError |
| P0 | C44 | sendSipMessage空壳 | 所有SIP命令无效 |
| P0 | C23-C24 | SnapshotConfig CmdType错误 | 设备不识别命令 |
| P0 | C08 | CSRF完全缺失 | 格式化存储卡/升级设备可被CSRF |
| P1 | C09-C10 | SSRF双向量 | 可攻击内网服务 |
| P1 | C11-C12 | 文件上传安全 | 双扩展名+路径穿越 |
| P1 | C37-C39 | HTTP方法错误(3处) | 前端GET请求被后端POST拒绝 |
| P1 | C27-C33 | 6个Handler XML结构错误 | 设备无法解析响应 |
| P1 | C36 | 4个Handler编译错误 | responseXml变量未定义 |
| P1 | C20 | TLS协议版本未锁定 | 降级攻击风险 |
| P2 | C41-C43 | 安全配置接口缺陷 | 无法读取已有配置 |
| P2 | C16 | XXE防御未知 | XML注入风险 |
| P2 | C17 | 零速率限制 | DoS风险 |
| P2 | C22 | sessionId长度未校验 | 可能导致SIP消息被拒 |

---

## 五、审计方法覆盖统计

| 维度 | 方法数 | 发现问题数 | 最密集维度 |
|------|:---:|:---:|------|
| A. 设计文档符合性 | M001-M025 | 22 | M007(CmdType大小写) |
| B. SIP协议合规 | M026-M045 | 31 | M038(响应XML结构) |
| C. 安全审计 | M046-M067 | 35 | M054(文件上传安全) |
| D. 可靠性健壮性 | M068-M087 | 28 | M068(NPE路径) |
| E. 代码质量 | M088-M100 | 45 | M090(魔法数字) |
| F. 前后端契约 | M101-M106 | 12 | M106(缺失端点) |
| F2. 前端质量 | M107-M112 | 35 | M111(console.log残留) |

---

## 六、下一轮审计计划

Round 2 将聚焦：
- M001-M010：重新验证设计文档符合性（使用不同视角）
- M046-M055：深度安全渗透测试视角
- M088-M100：使用不同代码质量工具视角（SonarQube规则、PMD规则、Checkstyle规则）

> **报告结束 | Round 1 完成 | 208项发现 | 待续Round 2**
