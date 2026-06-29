# deviceList.vue 改造补丁说明

## 来源

- F6: 设备列表新增"协议版本"列（后端改造项1，2022版附录I X-GB-ver）

## 改造文件

`web/src/views/device/list.vue`

## 改造说明

在设备列表表格中新增一列"协议版本"，显示设备通过 X-GB-ver 协商的协议版本号。

---

## 1. 新增表格列

在 `<el-table>` 中已有的 `<el-table-column>` 之间插入：

```vue
<!-- ===== F6改造: 设备列表新增"协议版本"列 ===== -->
<!-- 来源: 后端改造项1, 设计文档第13.9节, 2022版附录I -->
<!-- 说明: 显示X-GB-ver版本协商结果，使用前缀匹配与后端 isVersion2022() 保持一致 -->
<el-table-column label="协议版本" min-width="100" align="center">
  <template slot-scope="scope">
    <el-tag
      :type="(scope.row.protocolVersion || '').startsWith('2.') ? 'success' : 'info'"
      size="small"
      effect="plain"
    >
      {{ (scope.row.protocolVersion || '').startsWith('2.') ? '2022' : '2016' }}
    </el-tag>
  </template>
</el-table-column>
```

## 2. 逻辑说明

- 使用 `startsWith('2.')` 前缀匹配（而非精确比较 `==='2.0'`），与后端 `Device.isVersion2022()` 方法保持一致
- 无 `protocolVersion` 字段或值为空的设备，视为 2016 版（显示灰色"2016"标签）
- 该列依赖后端在设备列表 API 响应中返回 `protocolVersion` 字段

## 3. 后端依赖

需要后端 `Device.java` 中的 `protocolVersion` 字段（来源：改造项1）：

```java
@Schema(description = "设备协议版本, 2022版X-GB-ver头域值, null为2016版设备")
private String protocolVersion;

public boolean isVersion2022() {
    return protocolVersion != null && protocolVersion.startsWith("2.");
}
```
