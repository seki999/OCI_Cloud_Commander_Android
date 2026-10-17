# 规则目录（Phase 1–2）

| 模块 | 模拟规则 | 非法配置 / 失败反馈 |
|---|---|---|
| 资源事务 | 固定 ID、Kind 可写字段白名单、派生字段只读、引用类型与 Region、原子写入、引用者级联重新校验 | 原 WorldState 不变，显示具体原因 |
| CIDR | IPv4 canonical CIDR、包含/重叠、子网分配地址排除首两/末一地址 | CIDR host bits / overlap / out of range |
| Route | 同 VCN 下一跳、最长前缀匹配、独立服务 label | blockedAt=route / gateway |
| IGW / NAT / SGW | IGW 需公有 Subnet + Public IP；NAT 只出站；SGW 只到教学服务地址 | blockedAt=publicIp / gateway |
| NSG | 方向/CIDR/协议/端口；默认拒绝；stateful 自动回程，stateless 需 49152 回程 | blockedAt=<id>.ingress / egress / return |
| 端点 | Compute / Database / LB / Bastion 共享 VNIC 语义：Private IP 唯一、Public IP 唯一且仅公有子网、NSG 同 VCN | IP 已分配 / Private Subnet 禁止 Public IP |
| IAM | 用户/组成员、Dynamic Group；verb 累进；kind/scope/targetId；默认拒绝 | 403 NotAuthorizedOrNotFound |
| Compute | 创建 PROVISIONING，2 tick 后 RUNNING；`power` START/STOP（需 use）；Phase 2 必填 Shape/Image/模拟密钥；STOPPED 不计费 | 无法 START/STOP 当前状态 |
| 磁盘 | 日志路径归属最长匹配挂载点，否则根卷（50GB，系统 10GB）；>90% 失败；应用端口降级，22 不受影响 | blockedAt=<id>.service |
| SSH | 直连：Internet 路径 → NSG 22 → 监听 → authorized_keys；Bastion：允许 CIDR → Session（目标/端口/TTL/公钥）→ 目标 NSG → 监听 → 密钥 | allowlist / session / ttl / key / ingress / listener / authorized_keys |
| Block Volume | 50–32768GB；挂载点非系统目录且同实例唯一；挂载中禁止删除 | 先分离再删除 |
| 备份 / 恢复 | 备份复制 checksum/数据版本/容量；恢复卷 ≥ 备份容量；备份、来源不可修改 | 不可修改 / 容量不足 |
| Object Storage | Bucket 默认私有；匿名与 IAM 分开；生命周期 `prefix|archive:N|delete:M`（M>N）每日运行；L21 按规则阈值/前缀验收，避免样本巧合 | 匿名 ALLOW / 读取 DENY |
| Database | 5 tick PROVISIONING→AVAILABLE；Private Subnet；仅 Vault 引用凭证；端口/保留/自动备份；备份需 AVAILABLE；恢复复制数据与真实恢复点；备份新鲜度按数据点而非重新打包时间 | ORA-01017 模拟、明文拒绝 |
| LB | Listener 端口、`id:port` 后端、健康路径、超时、HTTPS 需证书、重定向需 80+443；健康 = LB egress + 后端 ingress + 监听 + 路径 + 不超时 | CRITICAL + 原因（404 / timeout / listener / ingress） |
| 容量 / SLO | 利用率 = requestRate / 健康后端吞吐；成功率 = min(100, 100/利用率)；延迟 = 40/(1−min(u,0.95)) ms | 延迟超过超时则全部摘除 |
| DNS | 最长后缀权威 Zone、A / CNAME（≤6 跳）、TTL 30–86400、同名 CNAME 排他 | NXDOMAIN / 链过长 |
| TLS | 443 Listener + 证书；未过期；CN 精确或一级通配符 | 过期 / 名称不匹配 |
| 门户 | DNS → 显式端口 → 公网端点 → Listener/NSG → TLS → 健康后端数；HTTP 重定向必须实际可达 80 | 逐层原因（陈旧记录、502/503） |
| 成本 | Shape、LB、DB、卷、对象层级的教学 Credits | 超预算时最多 1 星 |
| 评分 | required 全满足且无 failure trigger=1；constraints + 预算=2；optional + 提示≤1 + 变更≤上限=3 | 无 objective setter |

## 终端命令（白名单）

`oci iam compartment|group|policy list`、`oci compute instance list`、`oci network vcn|subnet list`、`oci bastion bastion|session list`、`oci bv volume|backup list`、`oci os bucket|object list`、`oci db database|backup list`、`oci lb load-balancer list`、`oci lb backend-health <lb>`、`oci dns zone list`、`oci certs certificate list`、`inspect <principal> <id>`、`curl --from <id> http[s]://<id>:<port>`、`curl https://<fqdn>/`、`ssh <client>@<id>`、`ssh --bastion <bastion> <client>@<id>`、`dig|nslookup <fqdn>`、`df -h <compute>`、`advance <1-100>`。

客户端只有模拟的 `operator`（198.51.100.10）与 `intruder`（192.0.2.66）。未知命令、shell 元字符、真实密钥路径均拒绝，不回退系统 shell。

Predicate 的严格参数集合在 `Predicates.parameters`，与 Schema oneOf 由 `SchemaRegistryTest` 保持一致；每个 Predicate 都有纯 evaluate 和正/反例。
