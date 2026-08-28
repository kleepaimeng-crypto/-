# 原始 JSON 报文导出实施切片

## 1. 切片 1：查询与任务模型

- 数据类型筛选改为多选；左侧记录选择跨页保留。
- 导出时按已勾选记录的 `data_type_code` 分组。
- 新增导出任务 Service、Mapper 和 Controller，使用既有 `file_job` 的 `EXPORT` 类型。
- 创建任务时固化记录 ID 快照，并按 ID 查询 `data_record.raw_payload`。

验证：勾选多种类型记录时创建多个 CSV 任务；未勾选记录时不创建任务。

## 2. 切片 2：CSV 生成

- CSV 使用 UTF-8 with BOM 和标准 CSV 转义；JSON 键展开为表头列，顶层 `items` 数组按元素展开为多行。
- 使用流式文件写入；不得将全部导出结果或 Base64 报文一次性载入内存。
- `raw_payload` 为空的记录计入跳过数量，任务结果为 `PARTIAL` 或 `FAILED`。

验证：中文、引号、逗号、换行、长 JSON、Base64 和空结果。

## 3. 切片 3：历史与下载

- 实现导出历史、任务详情和受控文件下载接口。
- 前端接通现有数据管理页的创建任务、历史刷新和下载操作。
- 明确显示排队、生成中、成功、部分成功和失败状态。

验证：任务状态刷新、非创建人访问、未完成下载和缺失文件。

## 4. 切片 4：回归与验收

- 核对 CSV 各行 JSON 可重新解析，字段和值与 `raw_payload` 一致。
- 验证导出不影响 UDP 接收、数据管理查询、乘客实时、轨迹、舷窗和 IFE。

```powershell
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd verify

Set-Location ..\frontend
npm run typecheck
npm run test
npm run build
```
