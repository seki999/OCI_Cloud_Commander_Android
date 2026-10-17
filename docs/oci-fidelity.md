# OCI 教学精度说明

核验日期：2026-10-17。游戏不是完整 OCI API、也不使用真实 Oracle SDK。标注：**一致** = 概念规则与 OCI 一致的教学子集；**简化** = 有意省略细节；**未覆盖** = 不存在于本游戏。

## 网络与 IAM（Phase 1）

- **一致**：IPv4 CIDR 包含/冲突、最长前缀 Route、私有 Subnet 禁 Public IP、IGW/NAT/Service Gateway 的职责区分、NSG 默认拒绝与 stateful/stateless 回程概念、IAM 默认无权限与 inspect<read<use<manage。
- **简化**：Subnet 通过直接字段关联端点，省略独立 VNIC；仅单 VCN 内互通；NSG 单端口、固定 49152 回程。教学地址 192.0.2.0/24、198.51.100.0/24、203.0.113.0/24 均为文档保留地址。Dynamic Group 为精确实例 ID + Compartment 的 AND。commander 为教学配置角色。
- **未覆盖**：identity domains 全功能、标签条件、Compartment 继承、Security List、Peering、IPv6。

## Compute / Bastion / Block Volume（Phase 2）

- **一致**：实例生命周期 PROVISIONING→RUNNING↔STOPPED→（删除）TERMINATED；STOPPED 不计算力成本；SSH 只用公钥（authorized_keys 不匹配 = Permission denied (publickey)）；Bastion 有客户端允许 CIDR、有时限 Session、目标无需公网 IP；Block Volume 需 attach 且挂载到目录才承载写入；卷备份可恢复为新卷，恢复卷容量不小于备份。
- **简化**：Shape 只有 4 个教学型号与固定吞吐；Image 2 个；“sshd 已启动”以 listenPorts 含 22 表示；Bastion 仅 managed SSH 会话；根卷固定 50GB、系统占 10GB；>90% 使用率视为日志写入失败；备份策略只记录名称。
- **未覆盖**：AD/FD 放置、实例池/自动扩缩、Cloud-init、启动卷替换、卷性能单位、跨区域卷复制。

## Object Storage / Database（Phase 2）

- **一致**：Bucket 默认私有；匿名访问与 IAM 授权是两条独立判断；读取对象内容需 read（inspect 不够）；生命周期按前缀与对象年龄执行归档/删除；Database 管理员凭证应引用 Vault Secret；恢复到新库后再切换应用；RPO 以恢复点与事故时间差计算。
- **简化**：仅 STANDARD/ARCHIVE；生命周期每模拟日运行；“训练模式数据库”抽象 DB System/Autonomous Database，只模拟私网端点、端口、Secret 引用、自动备份开关、保留天数、按需备份与 checksum/记录数。本游戏拒绝任何明文密码输入。
- **未覆盖**：对象版本、复制、PAR、加密密钥管理、Autonomous 自动扩展、Data Guard、PITR 日志回放。

## Load Balancer / DNS / TLS（Phase 2）

- **一致**：Listener + Backend Set + Health Check 结构；只有健康后端接收流量；健康检查依赖网络可达、端口监听、HTTP 路径 200、超时；DNS A/CNAME 解析与权威 Zone；TLS 检查证书有效期与主机名（通配符只匹配一级）；HTTP→HTTPS 301 重定向。
- **简化**：单 Backend Set、均分负载；容量 = 健康后端 Shape 吞吐之和，延迟 = 40ms/(1−利用率)；延迟超过健康检查超时则全部后端被摘除（模拟过载雪崩）；DNS 不缓存、TTL 只显示；TLS 不生成密钥、不执行加密，UI 与事件均注明 SIMULATED。
- **未覆盖**：Network Load Balancer、WAF、路径路由、会话保持、证书自动续期、DNSSEC、Traffic Management 策略。

## 成本

所有价格为游戏固定 Credits（`Cost`：Shape 每月、LB 15、DB 40、卷 0.04/GB、对象 STANDARD 2/GB、ARCHIVE 0.2/GB），**不是 Oracle 官方价格**，仅用于教学权衡。

## 官方参考

- [Networking Overview](https://docs.oracle.com/en-us/iaas/Content/Network/Concepts/overview.htm)
- [How Policies Work](https://docs.oracle.com/en-us/iaas/Content/Identity/Concepts/policies.htm)
- Compute / Bastion / Block Volume / Object Storage / Load Balancer / DNS / Certificates：本阶段依据已知公开概念编写；精确参数（Shape 规格、限制、生命周期细节）未逐项核验，因而全部标注为简化或教学值。后续阶段编码 OKE、Monitoring、Email Delivery 前应逐页核对规格第 13 节所列文档。

## Android 兼容基线

- AGP 8.7 / Gradle 8.9 / JDK 17 / compile & target SDK 35。存在更新的稳定平台；尚未升级，不声称使用最新 SDK。

## v0.2.1 复核补充

- LB 与后端通信同时检查 LB egress 和后端 ingress，不能因是托管服务而跳过 LB NSG。[Oracle Load Balancing 入门](https://docs.oracle.com/en/learn/get_started_load_balancing/)描述了为 LB 至后端流量配置出站安全规则。
- HTTPS URL 的显式端口不再被静默替换为 443；当前教学 Listener 只支持 HTTP 80 / HTTPS 443，其他端口明确报告不支持。HTTP 重定向目标同时要求外部实际可达 80。
- 新建 Phase 2 Compute 必须选择 Shape / Image / 模拟密钥，不能靠清空 Shape 获取未列出的默认容量。Phase 1 旧教学端点的兼容默认值保留。
- Bucket 未指定 visibility 时，由引擎设 private。训练模式 DB 强制 Private Subnet（教学范围限定，未宣称真实 OCI 的全部 DB 类型都仅支持私有子网）。
- DB_BACKUP.createdTick 表示创建时间，pointInTime 表示其中数据的实际恢复点。对已恢复数据库再次备份，不得把旧数据的恢复点更新为“现在”。旧内容未提供 pointInTime 时仅按备份原 createdTick 兼容解释。
- L21 不仅核对样本 tier，还检查 logs/ 的 30/90 天规则及相冲突的前缀规则。
- 模拟器的事件数字格式使用 Locale.ROOT，切换设备语言不会改变同一动作序列的事件内容。
