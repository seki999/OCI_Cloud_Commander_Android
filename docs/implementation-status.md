# 实施状态与验收差异

开发顺序严格按 Phase 0 → 1 → 2 → 3 → 4 → 5。当前增量覆盖 Phase 0–2（L01–L30），不代表全部需求交付。

| 阶段 | 状态 | 可检验结果 / 剩余工作 |
|---|---|---|
| 0 技术地基 | 核心实现，设备验收进行中 | 多模块、Compose 导航/主题、内容加载、Schema、确定性事务/事件、Room 重放、DataStore、JVM 测试；设备结果见 test-results |
| 1 L01–L12 | 核心玩法实现，打磨待续 | 12 golden + negative、遗漏关键修复与 Boss 多故障通过；仍缺连接手势、更多专属复盘 |
| 2 L13–L30 | 核心玩法实现，打磨待续 | Compute 电源状态机、SSH 分层诊断、Bastion Session、Block Volume 挂载/容量、卷备份与隔离恢复、Object Storage 可见性/生命周期、训练模式 DB（Vault 引用、备份、恢复点、RPO）、LB 健康检查/容量/超时雪崩、DNS A/CNAME、TLS 证书有效期与主机名、端到端门户探测、教学成本模型；18 关 golden/negative/逐个遗漏修复/Boss 独立修复全部通过 |
| 3 L31–L42 | 未实现 | 独立 Notifications / Email Delivery，时序指标与日志证据，12 关 |
| 4 L43–L54 | 未实现 | OKE 调度、K8s RBAC 与 OCI IAM 分离、kubectl/HCL、Vault/Audit，12 关 |
| 5 L55–L60 | 未实现 | 成本/SLO/灾备、6 关综合事故、最终结局/沙盒、性能/辅助功能/平板打磨 |

未实现章节没有加入内容 index，也没有虚假 golden path。`verifyFullGame` 要求完整 60 关，因此当前仍返回失败；普通 CI 是 Phase 0–2 增量门禁。

## Phase 2 设计决定（与规格的差异）

- **不新增“备份/恢复”专用 Action**：备份 = 创建 `VOLUME_BACKUP` / `DB_BACKUP`，恢复 = 创建带 `sourceBackup` 引用的 `VOLUME` / `DATABASE`。checksum、数据版本、记录数、恢复点由引擎从来源复制，不能手填。这样 Console 通用表单即可完成全部操作，规则仍在 Simulator 中。
- **新增 Action**：`power`（START/STOP，需 `use` 权限）以及只读观察 `ssh`、`dig`、`http`。观察动作不直接写资源配置、不计入变更数；它们仍推进 tick，因此可触发已有 PROVISIONING 完成或证书/Session 到期。
- **时间**：任何被接受的动作推进 1 tick；Compute 2 tick、Database 5 tick 完成 PROVISIONING。对象生命周期是每日批处理，只在显式 `advance_tick` 时运行（1 tick = 1 模拟日）。
- **托管服务出站**：LB 必须满足自身 NSG egress 和后端 ingress；仅 Bastion 保留独立 allow-list/session 模型，不提供用户可配的 NSG 出站。
- **应用磁盘满**会让应用端口探测失败（`blockedAt=<id>.service`），但不影响 22 端口 SSH。
- **WorldState 新增 `requestRate`**（场景流量，只读）；`budgetCredits` 由 `Cost.total` 实时比较，不再只检查负数。
- **测试规则调整**：逐个遗漏检查跳过观察动作与 `advance_tick`（等待不是修复；任何动作都会推进时间）。其余每个 golden 动作仍必须是必要修复。
- 证书、备份、对象与 Bastion Session 创建后不可修改；恢复来源、会话目标等关联在创建后固定。

## 重要待改进

- 目标 SDK 35 是本机已安装且验证的兼容基线，后续需升级最新稳定 SDK/AGP 并重新验证。
- DNS 不模拟缓存与 TTL 过期；TLS 不做真实握手；LB 只有单 Backend Set、轮询均分、单健康路径；无会话保持与权重。
- Database 是训练模式：单端点、无 RAC/Data Guard/PITR 日志回放；恢复只能回到备份点。
- Block Volume 无 AD 约束、无性能层级与 iSCSI/半虚拟化区别；卷备份策略只记录名称，不模拟按日自动生成。
- Object Storage 只有 STANDARD/ARCHIVE 两层，无 Infrequent Access、版本控制、PAR、对象级 IAM 条件。
- 城市地图为原创基础几何符号（矩形网络、菱形入口、圆角存储、圆形计算），状态以虚线环 + 文字双重提示；无故障脉冲动画、等角插画、端点连接手势。
- UI 中文 key 覆盖关卡内容；应用界面部分中文直接写在 Compose，尚未全量资源化。en/ja 只预留回退。
- 设备规模/字体 200%/TalkBack/分屏等未完成全部验收；屏幕常亮默认关闭，仅关卡前台生效。
- 进度保存最高星，无全局 XP 累加/成就库。通关复盘为通用导师说明加真实事件记录，尚无“症状→证据→根因→修复→验证”自动叙事。
- Room 数据库尚无未来版本迁移；当前格式损坏/不支持时拒绝恢复并允许重新选关。旧存档会严格执行重放一致性检查；本次内容与派生规则修正后，部分 v0.2.0 快照可能不再匹配。系统会明确提示，可从章节重新开始该关，最高星级进度保留；尚未实现跨内容版本存档迁移。

下一阶段（Phase 3）首先实现 MetricPoint 时序与 Alarm pending/FIRING/OK 状态机、Notifications Topic→Subscription→Inbox 路径，再实现与之完全独立的 Email Delivery（Approved Sender / SMTP / SPF / DKIM / Accepted≠Delivered / Bounce / Suppression）与 L31–L42。不得用可编辑 delivered、FIRING、health 字段替代规则。

## v0.2.1 复核修正

LB 双向 NSG、显式 URL 端口、真实 HTTP 80 重定向可达性、Phase 2 Compute 必填配置、引擎默认私有 Bucket、私网 DB、备份恢复点来源、L21 生命周期阈值以及跨 Locale 确定性均增加回归测试。L25 设备测试改为真实 Console 表单和 Terminal 输入，只有前置解锁/选关使用测试准备。详细证据见 phase2-review.md。
