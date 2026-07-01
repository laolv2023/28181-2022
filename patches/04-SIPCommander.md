# Patch 04: SIPCommander — SDP 字段拼写修正

> **目标文件**: `src/main/java/com/genersoft/iot/vmp/gb28181/transmit/cmd/impl/SIPCommander.java`
> **改造项**: 4（s字段 Download→DoWnload）、改造项5（删除Talk）、改造项6（downloadspeed→doWnloadspeed）
> **原始补丁**: `patches/04-SIPCommander.patch`（hunk 行号偏移，无法应用）
> **设计文档来源**: 第7节（第624~625行），2022版附录G

---

## 变更 1: s 字段 Download → DoWnload（约第413行）

```java
// === 改造项4: s字段Download→DoWnload ===
// 来源: 设计文档第7节(第624~625行), 2022版附录G s字段规范原文

// 原代码: content.append("s=Download\r\n");
// 改为:
content.append("s=DoWnload\r\n");  // 注意: W为大写, 规范特殊拼写
```

## 变更 2: s 字段删除 Talk（约第525行）

```java
// === 改造项5: s字段删除Talk ===
// 来源: 设计文档第7节(第624~625行), 2022版附录G

// 原代码: content.append("s=Talk\r\n");
// 改为:
if (device != null && device.isVersion2022()) {
    content.append("s=Play\r\n");
} else {
    content.append("s=Talk\r\n");  // 2016版设备兼容
}
```

## 变更 3: a 字段 downloadspeed → doWnloadspeed（约第554行）

```java
// === 改造项6: a字段downloadspeed→doWnloadspeed ===
// 来源: 设计文档第7节, 2022版附录G a字段规范原文

// 原代码: content.append("a=downloadspeed:");
// 改为:
content.append("a=doWnloadspeed:");  // 注意: W为大写
```

> **注意**: 2022版规范中 `doWnloadspeed` 的 `W` 为大写，与 `DoWnload` 的大小写模式一致，均为 GB/T 28181 规范的特殊拼写。
