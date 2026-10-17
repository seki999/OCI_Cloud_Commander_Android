# Changelog

## 0.2.1 — 2026-10-17

- 复核并修复 LB egress 绕过、HTTPS 显式端口忽略、HTTP 80 不可达却算重定向成功。
- 修复 Phase 2 Compute 缺配置/空 Shape 的容量与成本漏洞；引擎保证 Bucket 默认私有和训练 DB 私网。
- 保留恢复库二次备份的真实数据恢复点；L21 增加生命周期阈值校验；事件与关卡 ID 使用固定 Locale 格式。
- 增加 12 项复核回归测试；L25 设备测试改为真实表单/终端操作，补充 UI test tags。
- 修正验收表述，明确设备测试来源、存档版本兼容边界和未实现阶段。



## 0.2.0 — 2026-10-17

- Phase 2：第 3–5 章 L13–L30 共 18 关（计算引擎、数据守护、业务门户），含 3 个多故障 Boss。
- 新增 10 种资源（Bastion/Session、Volume/Backup、Object、Database/Backup、Load Balancer、DNS Zone、Certificate）与派生状态规则；端点统一 VNIC 语义。
- 新增 `power`、只读观察 `ssh` / `dig` / `http` 动作；生命周期在时间推进时运行；教学成本模型参与星级。
- 终端新增 Bastion SSH、df、backend-health、dig/nslookup、域名 curl、advance；Console 运行状态面板与 START/STOP；地图按类别绘形并以虚线环+文字标示问题。
- 测试增至 234 项；Schema↔Predicate 注册表同步测试；设备测试新增 L25 全流程，修正旋转恢复断言。

## 0.1.0 — 2026-10-17

- 从空仓库建立 Kotlin/Compose 多模块工程、Gradle Wrapper、离线模拟器、Schema/内容/存档边界。
- 实现 L01–L12 IAM/网络真实操作任务、城市资源图、配置表单、白名单命令、任务/提示/评分和事件流。
- 加入 Room 重放恢复、DataStore 设置、撤销/重做、地图布局、可选离线 TTS。
- 加入 103 项 JVM 测试、Schema 校验、设备测试源码、GitHub Actions 和 Debug 构建。
- 明确记录 Phase 2–5 与其他未完成需求；不将当前增量标记为完整游戏。
