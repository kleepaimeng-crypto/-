# 原始 JSON 报文导出实施切片

## 1. 切片 1：查询与任务模型

- 复用数据管理记录查询的筛选、排序和数据权限规则。
- 新增导出任务 Service、Mapper 和 Controller，使用既有 `file_job` 的 `EXPORT` 类型。
- 创建任务时固化筛选快照，任务运行时分页查询 `data_record.raw_payload`。

验证：创建 CSV 任务；筛选范围与数据管理列表一致；无 JSON 记录时状态明确。

## 2. 切片 2：CSV 生成

- CSV 以 `raw_json` 作为唯一业务列，使用 UTF-8 with BOM 和标准 CSV 转义。
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
