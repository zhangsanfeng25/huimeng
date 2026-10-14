# 控制中心日志与启动优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本项目本次默认当前助手顺序实施；不委派子代理。

**Goal:** 恢复直接可见的运行日志入口，测量并改善共享网站/API 的启动速度。

**Architecture:** 沿用 WinForms 控制中心和已有 Node 构建指纹校验，统一准备流程并减少重复进程启动。服务就绪条件仍包含 API 健康和有效网站构建，使用临时项目测试输入变化和失败路径。

**Tech Stack:** PowerShell 5.1、WinForms、Node.js、TypeScript、Vite、Express、SQLite。

**Spec:** `docs/superpowers/specs/2026-10-14-local-first-and-startup-design.md`（2026-10-14 用户已同意继续；产品实现前待本计划审核）。

## Global Constraints

- 保持 Java/XML、Volley、React/Vite、Express/SQLite 技术栈和现有视觉体系。
- 网站仍为服务端管理后台，本次不增加网站离线管理、远程托管或新支付渠道。
- 日志窗口沿用现有日期、级别筛选、刷新与三天保留规则。
- 保留构建输入与产物完整性校验，变化或损坏时自动重建。
- 测试使用临时数据库与隔离附件目录，不用真实业务库造测试数据。
- 工作区、提交和交付物不得包含业务库、上传图片、密钥或本地配置；保留历史 APK。

## Review Focus

1. 启动失败后日志按钮仍能打开并看到失败记录：任务 1 状态矩阵验证。
2. 字体 115%、最小窗口和系统缩放同时发生时入口不裁切：任务 1 布局测试。
3. 文件时间不变但构建产物损坏时仍应重建：任务 2 内容指纹测试。
4. 服务占用端口但不是本项目时不得显示就绪或关闭其他程序：任务 2/3 既有服务回归。
5. 已有业务数据重复启动不被 seed 覆盖：任务 2 两次准备临时数据库验证。

## 文件职责与执行关系

- 修改 `html/launcher/launcher.ps1`：日志按钮、布局、启动状态探测频率。
- 修改 `html/launcher/prepare.mjs`：导出并执行一次统一准备流程，保留 `ensureBuild` 兼容入口。
- 修改 `html/launcher/launcher-worker.cmd`：调用统一准备入口，继续由 service-runner 管理运行日志。
- 修改 `scripts/test-launcher-layout.ps1`：直接入口和字体/窗口状态覆盖。
- 新建 `scripts/test-launcher-startup.mjs`：临时项目中的构建复用、失败、计时回归。
- 新建 `scripts/measure-launcher-startup.ps1`：隔离端口与临时库的启动基线和结果；不关闭用户已有进程。
- 修改 `package.json`：将新回归加入根检查，不改变依赖。
- 新建 `docs/verification/2026-10-14-launcher-startup.md`：真实测量结果与限制，实施时填写。

本计划可独立验收，完成后继续 `2026-10-14-local-first-sync.md`；不能把本计划完成标记为全部需求完成。

开始 Task 1 前先按 Task 3 的计时定义保存现有版本基线，计时脚本可以先作为独立测量工具编写并在临时 fixture 验证；不得等修改启动实现后才补测旧版。

### Task 1: 恢复运行日志主界面入口

**Files:** 修改 `html/launcher/launcher.ps1`、`scripts/test-launcher-layout.ps1`。

**Interfaces:** 消费现有 `Open-RunLog`；新增 `$runLogButton`，由 `Update-Layout` 和 `Set-FontScale` 统一管理。保留 `$menuLog`。按钮位于数据管理区后、底部提示前，必要时提高最小窗口高度，不挤压原按钮。

- [ ] Step 1: 扩展布局测试：`$runLogButton.Visible -eq $true`、`Enabled -eq $true`；在 stopped/starting/failed/running 下点击均打开 `*运行日志` 窗口；按钮不与提示重叠且文字放得下。字号 0.85/1.0/1.15 分别测试默认与最小窗口。
- [ ] Step 2: 执行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-launcher-layout.ps1`，确认因入口缺失失败，记录输出。
- [ ] Step 3: 添加“运行日志”按钮并连接 `Open-RunLog`；更新数据管理后的布局、最小高度和缩放。不重写日志解析或调整保留期。
- [ ] Step 4: 运行该布局测试与 `scripts/test-launcher-logs.ps1`、`scripts/test-launcher-responsiveness.ps1`，预期全部 PASS；实际生成截图并查看默认/最小窗口、运行日志首行和 115% 字体。系统 DPI 无法切换时记录未验证项。
- [ ] Step 5: `git diff --check` 后，仅暂存本任务文件，提交 `fix: restore visible runtime log entry`。

### Task 2: 准备流程统一与可复用构建

**Files:** 修改 `html/launcher/prepare.mjs`、`html/launcher/launcher-worker.cmd`、`package.json`；创建 `scripts/test-launcher-startup.mjs`。

**Interfaces:** 保留 `ensureBuild(root, workspace, build?) -> boolean`；新增 `prepareProject(root, hooks?) -> Promise<{stages:Array<{name:string,durationMs:number}>,built:string[]}>` 和 CLI `node html/launcher/prepare.mjs --prepare`。hooks 仅注入测试构建/迁移执行器，不绕过生产校验。生产入口按后端构建、迁移/幂等初始化、网站构建顺序执行，输出各阶段耗时。

- [ ] Step 1: 使用临时 project fixture 创建足够输入与假构建产物：断言首次 `built=['server','html']`；第二次 `built=[]`；改源文件只重建对应工作区；损坏产物并恢复 mtime 仍重建；迁移失败阻止网站就绪和启动；临时真实数据库 seed 连续运行不覆盖手动修改的昵称与商品。用服务进程既有测试覆盖端口占用。
- [ ] Step 2: 执行 `node scripts/test-launcher-startup.mjs`，确认统一入口缺失导致失败；不能把 fixture 初始化报错当作目标失败。
- [ ] Step 3: 实现统一入口，worker 保留依赖安装与环境检查后仅启动一次准备 Node 进程；迁移和 seed 调用复用现有编译脚本。保留完整输入/产物 hash，保留 `--build` 与原数据库准备入口兼容。统一入口失败返回非零。
- [ ] Step 4: 运行新测试、`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-launcher-service.ps1`、`npm test` 和 `npm run build`，预期全通过；日志应能区分复用、重建和失败阶段。
- [ ] Step 5: 核对仅本任务文件并提交 `perf: consolidate verified service preparation`。

### Task 3: 启动时间与就绪反馈验证

**Files:** 修改 `html/launcher/launcher.ps1`；创建 `scripts/measure-launcher-startup.ps1`、`docs/verification/2026-10-14-launcher-startup.md`；按需扩展 `scripts/test-launcher-responsiveness.ps1`。

**Interfaces:** 计时脚本参数 `-ProjectRoot <absolute path> -OutputPath <absolute path> -Runs 3 -Port <unused port>`；输出 JSON `{environment,cold,warm:[{apiReadyMs,webReadyMs}],median,limitations}`。调用者从当前实现前基线与修改后各生成一份结果。控制中心启动时探测间隔 250ms，稳定后 2000ms，仍复用异步服务任务防止并发探测。

- [ ] Step 1: 核对实施前已保存的同机基线及工具；确认当前 root、临时数据路径、端口与进程归属，只停止脚本创建的进程。已有产物缺失时先建立一次构建，不把构建后的首个启动算作纯冷启动。冷启动在隔离 fixture 中执行，禁止删除原项目产物或依赖。
- [ ] Step 2: 在响应性测试加入启动中多次 timer tick 时最多一个服务探测任务、失败/端口占用不提前打开网页的断言；运行该测试确认待改行为失败。
- [ ] Step 3: 实现按状态调整探测间隔，所有变更保持 UI 主线程不阻塞；计时脚本仅接受 API service 标识正确且网站登录页可响应作为就绪。
- [ ] Step 4: 修改后暖启动运行三次，比较中位数与范围，目标争取改善 20%；实际未达到也如实记录。运行日志/布局/响应性/服务测试，浏览器按用户优先级连接本地 Chrome/Edge 查看网站；连接不可用时说明替代方式及证据限制。
- [ ] Step 5: 保存真实基线、截图路径和结果到验证报告，区分脚本探测与真实点击；提交 `perf: improve startup readiness feedback and record timings`。

## 自审和交付门槛

- [ ] 对应设计第 2、9、10 节逐项检查；三天日志规则、原备份/恢复/导出按钮仍正常。
- [ ] 所有测试 fixture 的绝对路径经校验后才递归清理，Windows 全程使用同一 shell。
- [ ] 汇报实际耗时与验证范围；进入离线计划前保留可回退提交。此文档阶段不执行产品测试、不启动服务、不发布。
