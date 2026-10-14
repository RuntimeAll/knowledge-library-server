# 知识库 HTML 交换协议 v1

HTTP 均使用若依 Bearer token；导入和资产结果在 `AjaxResult.data`，导出返回 ZIP 字节。

## 导入

`POST /knowledge/import/html`，`multipart/form-data`：

- `file`：一个 UTF-8 HTML，最大 2 MiB。
- `resources`：零个或多个原始题图文件，可重复此字段；允许 PNG/JPEG/GIF/WebP 及安全静态 SVG，按文件名映射 HTML 的相对 `img src`，同名拒绝。
- `idempotencyKey`：必填，1–128 个字符。相同账号+键+内容重复返回同一批次，键相同而内容不同报冲突。
- `sourceId`：可选，现有来源资料 ID。仅建立未核验来源关联，原始资料通过资产上传和来源 CRUD 独立登记。
- `X-KB-Test-Run`：仅服务端测试模式接受的安全 runId；本轮所有上传对象进入本轮测试前缀。

同一 HTML 最多 200 题。逐题原子事务，部分题失败不撤销其他成功题。响应 `{batchId,status,totalCount,successCount,failureCount,items:[{clientKey,intent,status,questionId,message}]}`；状态 `RECEIVING/PROCESSING/SUCCESS/PARTIAL/FAILED`，item 为 `SUCCESS/REUSED/FAILED`。`GET /knowledge/import/{batchId}` 重新读取结果。batchId/id 为数据库 Long。

```html
<!doctype html>
<html lang="zh-CN">
<head><meta charset="utf-8"><meta name="kb-schema" content="kb-question-html/v1"></head>
<body>
  <article data-client-key="source-book-001-question-003" data-intent="create">
    <script type="application/json" data-kb-metadata>{"title":"分数应用题","subject":"MATH","questionType":"APPLICATION","difficulty":"MEDIUM","kpIds":[],"sourceNote":"原书第12页第3题"}</script>
    <section data-part="stem"><p>计算 <span class="math" data-latex="\frac{1}{2}+\frac{1}{3}">\(\frac{1}{2}+\frac{1}{3}\)</span>。</p><img src="figure.png" alt="题图"><table><tr><td>表格保留</td></tr></table></section>
    <section data-part="answer"><p>\(\frac{5}{6}\)</p></section>
    <section data-part="analysis"><p>通分计算。</p></section>
  </article>
</body></html>
```

`data-client-key` 为必填稳定外部键，最多160字符；同一账号相同 create 键和相同内容复用已有题，不再重复创建，内容变化需显式 update 或 derive。

- 新建：`data-intent="create"`。
- 修改原题：`data-intent="update" data-question-id="123" data-row-version="2"`。rowVersion 不符拒绝覆盖。返回该题新版本；导出默认此意图。
- 新建变式：`data-intent="derive" data-parent-id="123"`；metadata 中 `auxiliaryParentIds` 为可选其它母题ID数组，`operation`、`changeNote`、`preservedNote` 描述改动及保留点。operation使用字典码NUMBER/CONDITION/QUESTION/OTHER。示例 `{"operation":"NUMBER","changeNote":"替换分子分母","preservedNote":"保留通分考点"}`。

修改与变式都必须在HTML头包含 `<meta name="kb-origin" content="当前实例UUID">`。通过 `GET /knowledge/import/schema` 获取 `{schemaVersion,instanceId}`；导出自动携带。来自旧数据库的ID不能直接更新当前实例。

metadata 脚本只提取 JSON 后立即剔除，永不渲染。普通脚本、事件属性、iframe、远程CSS移除。stem 区域必填；answer、analysis在更新时未出现则保留，出现空区域则清空。metadata 可编辑字段省略则更新保留；显式空字符串/空数组清空可选字段；标题为空则从题干补齐。新建默认 title 从题干截取、其余元数据可缺省。

图片允许：`src="相对文件名"` 对应 resources；或 `data-asset-id="123"` 引用已上传题图；或 `src="data:image/png;base64,..."` 内嵌题图。禁止服务器抓取任意 HTTP URL；`http:`/`https:`、绝对路径、`..` 无法映射时该题失败。标准HTML保留表格和 TeX，持久化图引用规范为 `data-asset-id` + `src="kb-asset:123"`，展示时通过资产预览接口解析。

## 文件资产与原件

`POST /knowledge/asset/upload`：multipart `file`、`purpose=SOURCE_ORIGINAL|PRODUCT_ORIGINAL|QUESTION_IMAGE`、可选 `idempotencyKey`。同一账户+幂等键+相同原件重复返回同一assetId；不同哈希/用途/文件名/测试批次拒绝复用。失败续跑先检查OSS对象哈希，避免重复上传。返回含 `assetId,originalName,contentType,fileSize,sha256,status` 的文件记录。来源与成品的文件引用只存 assetId。

`GET /knowledge/asset/{assetId}` 返回元信息。`GET /knowledge/asset/{assetId}/content` 使用 attachment 下载原始字节（必须带token），绝不消毒或重排原件。`GET /knowledge/asset/{assetId}/preview` 返回图片/PDF临时 URL `{url,expiresInSeconds,contentType}`；HTML原件下载后只能在隔离sandbox预览。`DELETE /knowledge/asset/{assetId}` 仅允许无业务引用，失败保留 DELETE_PENDING，可精确重试。

## 导出与回流

`POST /knowledge/export/questions`，JSON `{"questionIds":[123,456],"includeAnswers":true}` 或 `{"materialId":123,"includeAnswers":true}`，两者互斥。题目选择保留请求顺序；专项保留明细顺序并校验来源筛选有效。默认含答案。

ZIP 包含 `index.html`、`questions/<id>.html`、`manifest.json`、`assets/` 原始题图、`vendor/` 本地公式脚本字体。答案/解析红色，题干和表格线黑色。manifest记录 schemaVersion、题ID和rowVersion、图assetId/object hash与相对路径。

每题HTML保留 `data-intent="update"`、`data-question-id`、`data-row-version` 和稳定 clientKey。回流时上传所选题HTML及其相关assets；明确修改为 derive 并指定parent-id可建立变式。已有data-asset-id可直接复用，所以同系统回流不要求重新上传图片、不要求ZIP。

## 中断恢复

新增迁移为 `sql/knowledge-import-resume.sql`，须在旧资产DDL之后执行一次。批次先进入 `RECEIVING`：原始HTML和每个上传资源均按确定性内部幂等键存入OSS，`kb_import_resource`记录resourceKey、assetId、哈希和大小。全部输入接收完成才置 `inputReady=true` 并进入 `PROCESSING`。资源清单也属于资产引用，不能在处理中直接删除文件。

`GET /knowledge/import/{batchId}` 增加 `inputReady`、`leaseUntil`、`lastError`、`resources` 字段。批次按创建账号隔离。`POST /knowledge/import/{batchId}/resume` 无请求正文，沿用 `knowledge:import:execute` 权限；测试批次必须携带原 `X-KB-Test-Run`。恢复从OSS重读原HTML及原资源并校验SHA256和整个请求指纹，不要求客户端重新上传，只有没有逐题回执的索引会继续执行。成功、复用、失败回执均保留；修正失败项要显式提交新批次，不能借恢复覆盖原回执。完成批次再次resume仅返回原结果。

批次持有数据库时钟的90秒租约，每20秒续约。运行中的resume返回409，进程退出后最迟等待原租约过期再恢复。每个单题事务先锁定批次行并核对claim token，题目、关联、稳定外部键和成功回执同事务提交；即使旧执行者恢复活动，也不能越过新的claim写入。`claimToken`不向HTTP输出。

若中断发生在 `RECEIVING`，尚未传到服务器的文件无法从远端重建，应携原文件、resources、原sourceId和相同idempotencyKey再次POST `/html`，已接收资源会复用。输入资产处于未知的 `UPLOADING` 时需先核对其精确对象状态，不可用新文件覆盖未知结果。旧版本遗留且未记录资源映射的PROCESSING批次同样需原请求重新提交，不能宣称可无文件恢复。

CLI：`python scripts/knowledge-client.py resume BATCH_ID --receipt .runtime/resumed.json --run-id ORIGINAL_TEST_RUN`。凭据只取进程环境 `KB_USERNAME` / `KB_PASSWORD`；正式非测试批次省略run-id。保持同一创建账号。

测试清理先按已核对batchIds删除 `kb_import_resource`、逐题回执和批次，再处理精确资产ID及对象版本，不能仅凭路径前缀批量删除。已删除题目的稳定外部键保留删除语义，旧请求不会自动复活题目；确需重新新建时使用新clientKey。
