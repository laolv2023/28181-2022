# 52_Round_02 深度逻辑审计报告 — WVP GB/T 28181-2022

> **审计日期**: 2026-07-01 | **审计轮次**: Round 2
> **审计焦点**: 业务逻辑正确性、算法验证、状态一致性、生产就绪性
> **审计方法**: M001-M112中与逻辑深度验证相关的方法子集

---

## 摘要

| 严重度 | Round 1 | Round 2 新增 | 累计 |
|--------|:---:|:---:|:---:|
| 🔴 CRITICAL | 49 | 23 | 72 |
| 🟠 HIGH | 55 | 18 | 73 |
| 🟡 MEDIUM | 80 | 14 | 94 |
| 🔵 LOW | 24 | 4 | 28 |
| **合计** | **208** | **59** | **267** |

---

## 一、🔴 CRITICAL 新增发现（23项）

### 1.1 算法/数据流错误（6项）

| ID | 文件:行 | 问题 | 影响 |
|----|---------|------|------|
| R2-C01 | SIPCommander2022Supplement:108-110 | **NaN/Infinity绕过数值校验**: `pan < -180 || pan > 180` 对 NaN 返回 false（所有NaN比较均为假），NaN值静默通过 | 恶意或意外传入NaN→设备收到`<pan>NaN</pan>` |
| R2-C02 | SIPCommander2022Supplement:121-123 | **NaN→"NaN"字符串写入XML**: `String.format("%.2f", NaN)` 输出字面量 "NaN", Inf输出"Infinity" | 发送非法XML数值到设备 |
| R2-C03 | SIPCommander2022Supplement:284 | **固件下载URL指向设备自身**: `"http://" + device.getIp()` 使用设备IP而非服务器IP；设备无法从自身下载固件 | 设备升级永远失败 — 服务器地址不可达 |
| R2-C04 | SM3DigestHelper:73,141-154 | **SM3摘要静默降级为SHA-256**: 静态块中ACTUAL_ALGORITHM在SM3不可用时设为"SHA-256"，digest()无告警返回SHA-256 | 与GB/T 28181-2022要求"SM3国密"不符 |
| R2-C05 | SM3DigestHelper:246-258 | **digestWithFallback逻辑死锁**: 先抛异常再检查可用性，MD5回退代码永远不可达 | 方法名暗示回退但实际永不会回退 |
| R2-C06 | GbCode2022:29-34 vs 286-287 | **采集位置码长度定义不一致**: 注释1字符，代码取2字符(substring(13,15)) | 行业类型/设备序号解析位置整体偏移 |

### 1.2 数据安全风险（3项）

| ID | 文件:行 | 问题 | 影响 |
|----|---------|------|------|
| R2-C07 | ApiDeviceControlController:160-181 | **无确认即格式化存储卡**: formatSdcard() 单次POST无确认参数，直接执行破坏性操作 | 生产级数据丢失风险 — 一次误调用清空SD卡 |
| R2-C08 | ApiDeviceControlController:290-318 | **无设备升级进行中检查**: deviceUpgrade() 不查询设备状态即发送升级命令 | 并发升级→设备收到冲突命令→变砖风险 |
| R2-C09 | ApiConfigController:166-168 | **安全配置持久化静默失败**: 写入文件失败仅log.warn，API调用方收到success(200) | 配置重启后丢失，用户得到虚假成功确认 |

### 1.3 配置持久化缺陷（3项）

| ID | 文件:行 | 问题 | 影响 |
|----|---------|------|------|
| R2-C10 | ApiConfigController:94 | **setSm3Digest: 持久化switched sm3DigestEnabled+sm3DigestAlgorithm，读取时仅读sm3DigestEnabled** | 3个config key写入文件，2个被写入，仅1个能在重启后读取 |
| R2-C11 | ApiConfigController:134 | **setGb18030Encoding: 持久化gb18030Encoding，读取时仅读gb18030Encoding** | 重启后其他2个设置丢失 |
| R2-C12 | ApiConfigController:163 | **setSecurity: 持久化tlsEnabled但读取时读securityEnabled** | Key名不匹配，重启后无法恢复 |

### 1.4 缺失关键实现（5项）

| ID | 文件:行 | 问题 | 影响 |
|----|---------|------|------|
| R2-C13 | CertAuthHelperImpl:全文 | **证书验证全返回false/null**: CRL/OCSP吊销检查桩实现，生产环境所有证书认证失败 | 接口声明证书认证功能 → 实际不可用 |
| R2-C14 | GB35114HelperImpl:全文 | **缺失processSVACEncryptedStream方法**: 接口3个方法，实现类仅2个@Overrride | 编译错误或运行时AbstractMethodError |
| R2-C15 | DataIntegrityHelperImpl:全文 | **摘要算法不可配置**: 硬编码SM3DigestHelper，但SM3降级SHA-256后调用方无感知 | No SM3/MD5 per-device configuration |
| R2-C16 | ExtensionApplicationHandler:30-37 | **扩展应用处理器为空操作**: handleExtensionApplication永远返回true | 预留接口无实际功能 |
| R2-C17 | MansrtspHelper:全文 | **MANSRTSP辅助仅处理Scale**: 缺少PLAY/PAUSE/TEARDOWN/SETUP等核心命令 | 2022版A.2.4要求完整MANSRTSP控制 |

### 1.6 生产安全（3项）

| ID | 文件:行 | 问题 | 影响 |
|----|---------|------|------|
| R2-C18 | RegisterRedirectHelper:全文 | **无重定向环路检测**: A→B→A 无限重定向，无深度上限/visited集合 | 资源消耗+注册流程挂死 |
| R2-C19 | GB35114HelperImpl:29-31 | **空字符串被当作A级安全**: `"ABCD".indexOf("")` 返回0，declaredLevel=""→rank=0→A级 | 空声明绕过安全检查 |
| R2-C20 | GbCode2022:212-224 | **isValidCapturePositionCode接受任意长度**: parseInt不校验位数，"001"→1→通过 | 跨字段误读风险 |

### 1.7 关键新增：Round 1遗漏（3项）

| ID | 文件:行 | 问题 | 影响 |
|----|---------|------|------|
| R2-C21 | ApiDeviceControlController:242 | **NPE顺序错误**: file.getOriginalFilename() 在 null检查(file==null) 之前 | 空文件上传直接NPE崩溃 |
| R2-C22 | ApiConfigController:147-149 | **getSecurity()返回类型错误**: 声明WVPResult但返回裸Map | 前端JSON解析崩溃 |
| R2-C23 | SdpFieldHelper:74-93 | **buildFField接受null字段**: 所有参数空值→`f=v////` 无意义SDP | 无效SDP发给设备 |

---

## 二、🟠 HIGH 新增发现（18项）

主要类别：
- **控制器验证缺失** (4项): interval/sessionId/firmware参数未校验
- **数值/类型缺陷** (3项): 下载倍速负值、Double恒等比较
- **线程安全** (2项): 并发重定向Device修改、SM3手动锁
- **未XML转义** (2项): CruiseTrackListID未转义、存储卡序列号未转义
- **错误恢复** (2项): 查询响应XML构建后未发送(R2发现所有4个QueryHandler)、SIP静默失败
- **其他** (5项): 异常吞噬错误原因、异步无关联ID、空方法返回

---

## 三、🟡 MEDIUM 新增发现（14项）

- 魔数残留：4处handler中硬编码默认值
- 重复代码：4个QueryHandler中identical的Gson实例化
- 逻辑冗余：DeviceUpgradeResult中恒等式校验
- 桩代码标注缺失：CertAuthHelperImpl/GB35114HelperImpl无"生产勿用"标记
- 枚举值硬编码
- 异常捕获过宽

---

## 四、关键问题演化追踪

| Round 1 问题 | Round 2 深化发现 |
|-------------|-----------------|
| R1: sendSipMessage空壳→所有SIP无效 | R2: 确认静默失败层次+固件URL错误→双重失败 |
| R1: 4个Handler编译错误(responseXml) | R2: 确认scope问题+查询响应XML从未发送 |
| R1: XML元素拼写错误 | R2: 发现NaN/Infinity绕过数值校验 |
| R1: 安全缺陷(TLS/SSRF/CSRF) | R2: 发现SM3降级+证书全失败+空字符串安全绕过 |
| R1: 配置持久化1个key不匹配 | R2: 发现3个config key都不正确 |

---

## 五、Round 3 审计计划

- 聚焦：设计文档逐条逐行验证（M001-M025全部25种方法）
- 特殊视角：反向验证——从设计文档出发确认每个需求是否有代码实现
- 跨文件一致性：SIP实体命名与文档附录的精确匹配
- 前端深度审查：Vue组件逻辑完整性

> **报告结束 | Round 2 完成 | 59项新增发现 | 累计 267项 | 进入 Round 3**
