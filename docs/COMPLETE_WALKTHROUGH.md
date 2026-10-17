# OCI Cloud Commander Android · 第 1–30 关完整通关秘籍

> 对应仓库：[OCI_Cloud_Commander_Android](https://github.com/seki999/OCI_Cloud_Commander_Android)。依据 `content/chapters/ch01–ch05`、`core/testing/fixtures/level-001.json` 至 `level-030.json` 的自动化三星通关操作（golden fixtures）整理，2026-10-17。
>
> **版本边界**：当前实际实现 L01–L30。L31–L60 尚未实现，不能提供真实可玩的通关命令。这里列的是本游戏内的模拟资源，不连接真实 OCI。测试 fixtures 是 **GameAction 序列**，攻略中资源创建/修改需在 **Console 表单** 完成，而不是把 `create...` 字段直接输入终端。

## 开始前先看：操作流程

1. 首页 **继续游戏 / 章节星图** 选关；关卡会加载独立的 **initialWorld**，所以不同关反复创建同名对象并不代表继承之前的云资源。
2. 在 **Mission** 查看目标/安全约束及可选证据，在 **Console** 选择资源类型、创建或点击已有资源编辑；表单里的 **ID、Compartment、Region、属性、References** 都要匹配下方攻略。
3. 资源操作完成后用 **Terminal** 查询或探测。终端以白名单为准，并不支持 `oci compute instance launch` 等真实 OCI 创建命令。需要时间流逝时输入 `advance 2` 或 `advance 5`。
4. 若关卡要求概念学习，在 Mission 中打开概念卡并**实际点击学习**；如有可选证据，按攻略操作 `Inspect`。单纯打开界面不等于完成学习动作。
5. 观察 Mission 目标是否全部达成；进度由引擎根据状态直接计算。**不需要寻找 KubeCraft 的“验证任务并结算”按钮**。
6. 三星取决于必需目标、安全约束、预算、可选证据、提示次数、修改操作数量。下文均以测试通过的 `golden` 序列为准。

**符号说明**：`字段=值` 表示 Console 中创建/编辑该字段；`Resource A → Resource B` 表示 References 关联。终端命令才用代码块。含 `;` 的 NSG 规则和 DNS records 应作为**一个字段值**整体填写。

## 第 1–30 关逐关标准答案

## 第 1 章：云账号、Region 与 IAM（01–06）

### 第 01 关 · 东京区域与概念学习

**依次操作：** Console 选择 Region `ap-tokyo-1`；Mission 概念卡依次学习 `Tenancy`、`Region`、`AD`、`FD`；Inspect `root`。

**通关注意：** 选择东京并完成四张概念卡；不要选大阪。

### 第 02 关 · 开发与生产隔离

**依次操作：** 创建 `COMPARTMENT dev`、`COMPARTMENT prod`（parent=`root`）；编辑 `test-server.compartment=dev`、`prod-server.compartment=prod`；Inspect `vcn`。

**通关注意：** 测试和生产服务器不能放反。

### 第 03 关 · 只读开发运维组

**依次操作：** 创建 `GROUP operators`（compartment=`dev`, members=`newcomer`）；创建 `POLICY read-dev`（group=`operators`, verb=`read`, resourceType=`COMPUTE`, scope=`dev`）；Inspect `dev`。

**通关注意：** 不要把 read 误设成 inspect 或 manage。

### 第 04 关 · 纠正错误授权

**依次操作：** 编辑 `POLICY read-dev`：verb=`read`、resourceType=`COMPUTE`、scope=`dev`；Inspect `dev`。

**通关注意：** 三个字段都要核对，单改 verb 不足以完成。

### 第 05 关 · 工作负载身份

**依次操作：** 创建 `DYNAMIC_GROUP workloads`（matchInstance=`worker`, matchCompartment=`dev`）；创建 `POLICY bucket-read`（group=`workloads`, verb=`read`, resourceType=`BUCKET`, scope=`dev`, targetId=`bucket`）；Inspect `dev`。

**通关注意：** Dynamic Group 必须精确匹配 worker。

### 第 06 关 · IAM 综合排错

**依次操作：** 选东京；`sample.compartment=dev`；`operators.members=newcomer`；`read-dev` 设置 `read/COMPUTE/dev`；`workloads.matchInstance=worker`；Inspect `dev`。

**通关注意：** 逐项修复，不要只修改组成员。

## 第 2 章：VCN、Subnet 与网关（07–12）

### 第 07 关 · 创建 VCN 和公有子网

**依次操作：** 创建 `VCN vcn` CIDR=`10.10.0.0/16`；创建 `SUBNET web-sub` CIDR=`10.10.1.0/24`，public=`true`，vcn=`vcn`；Inspect `dev`。

**通关注意：** Subnet CIDR 必须属于 VCN。

### 第 08 关 · 应用与数据库私有分区

**依次操作：** 创建 `app-sub`=`10.10.2.0/24`、`db-sub`=`10.10.3.0/24`，均 public=`false`、VCN=`vcn`；设置 `app` IP=`10.10.2.10` 并关联 app-sub；设置 `db` IP=`10.10.3.10` 并关联 db-sub；清空二者 Public IP；Inspect `dev`。

**通关注意：** 必须同时处理 app 和 db。

### 第 09 关 · 互联网网关

**依次操作：** 创建 `IGW igw`（enabled=true, VCN=vcn）；创建 `ROUTE_TABLE public-rt`（routes=`0.0.0.0/0>igw`, VCN=vcn）；把 `web-sub.routeTable=public-rt`；将 `web-nsg.rules=egress|0.0.0.0/0|tcp|443|stateful`；Inspect `dev`。

**通关注意：** 只有路由没有 NSG 出站规则仍会被阻断。

### 第 10 关 · 私有子网 NAT 出站

**依次操作：** 创建 `NAT nat`（enabled=true, VCN=vcn）；创建 `private-rt` routes=`0.0.0.0/0>nat`；`app-sub.routeTable=private-rt`；`app-nsg.rules=egress|0.0.0.0/0|tcp|443|stateful`；Inspect `dev`。

**通关注意：** NAT 是出站，不是开放互联网入站。

### 第 11 关 · Service Gateway

**依次操作：** 创建 `SERVICE_GATEWAY sgw`（enabled=true, service=`oci-services`, VCN=vcn）；创建 `service-rt` routes=`oci-services>sgw`；关联 `app-sub.routeTable=service-rt`；设置 `app-nsg.rules=egress|192.0.2.0/24|tcp|443|stateful`；Inspect `dev`。

**通关注意：** 需要 route table 与 NSG 同时允许。

### 第 12 关 · 网络综合修复

**依次操作：** `collision.cidr=10.10.4.0/24`；`public-rt.routes=0.0.0.0/0>igw`；`web-nsg.rules=ingress|0.0.0.0/0|tcp|443|stateful;egress|10.10.2.0/24|tcp|8080|stateful`；`app-nsg.rules=ingress|10.10.1.0/24|tcp|8080|stateful;egress|10.10.3.0/24|tcp|1521|stateful`；`db-nsg.rules=ingress|10.10.2.0/24|tcp|1521|stateful`；清空 `db.publicIp` 并将 `db-sub.public=false`；Inspect `dev`。

**通关注意：** 7 个修复点缺一不可；修改多条 NSG rule 时保留分号。

## 第 3 章：Compute、SSH 与 Bastion（13–18）

### 第 13 关 · 部署 Compute

**依次操作：** 创建 `COMPUTE web` 到 `web-sub`，privateIp=`10.10.1.10`，publicIp=`203.0.113.10`，nsgs=`web-nsg`，listenPorts=`22,443`，sshKey=`ops-key`，shape=`VM.Standard.E4.Flex.1`，image=`Oracle-Linux-9`；终端 `advance 2`；Inspect `dev`。

**通关注意：** 实例要经过 PROVISIONING 才会 RUNNING；不要填 laptop-key。

### 第 14 关 · 修复 SSH

**依次操作：** 编辑 `web.publicIp=203.0.113.10`；`web-nsg.rules=ingress|0.0.0.0/0|tcp|443|stateful;ingress|198.51.100.0/24|tcp|22|stateful`；设置 `web.listenPorts=443,22`、`web.sshKey=ops-key`；Inspect `dev`。

**通关注意：** SSH 22 只对训练管理网段开放，不能放开 0.0.0.0/0。

### 第 15 关 · Bastion SSH

**依次操作：** 创建 `BASTION bastion`（app-sub, privateIp=`10.10.2.5`, allowedCidr=`198.51.100.0/24`）；创建 `BASTION_SESSION bastion-ssh`（bastion=bastion, target=app, port=22, sshKey=ops-key, ttlTicks=60）；`app-nsg.rules=ingress|10.10.1.0/24|tcp|8080|stateful;ingress|10.10.2.5/32|tcp|22|stateful`；Inspect `dev`。

**通关注意：** 拒绝全网 Bastion allowlist；目标实例 NSG 也要允许 Bastion。

### 第 16 关 · 日志磁盘扩容

**依次操作：** 创建 `VOLUME logs-vol`（attachedTo=app, sizeGb=100, mountPoint=`/data`, backupPolicy=`none`）；编辑 `app.logPath=/data/logs`；Inspect `dev`。

**通关注意：** 日志路径必须落在已挂载卷上。

### 第 17 关 · 备份与恢复演练

**依次操作：** 将 `orders-vol.backupPolicy=silver`；创建 `VOLUME_BACKUP orders-bk`（type=FULL, volume=orders-vol）；创建 `VOLUME restore-test`（sizeGb=200, mountPoint=`/restore`, sourceBackup=orders-bk, attachedTo=test-vm）；Inspect `dev`。

**通关注意：** 恢复卷容量必须覆盖备份原容量。

### 第 18 关 · 恢复挂载与服务

**依次操作：** 给 `data-vol` 设置 attachedTo=`app`；`app-nsg.rules=ingress|10.10.1.0/24|tcp|8080|stateful;ingress|10.10.2.5/32|tcp|22|stateful`；对 `app` 执行电源 `START`；Inspect `dev`。

**通关注意：** START 在 Console 电源操作完成，不是 Terminal 命令。

## 第 4 章：Block/Object Storage 与数据库（19–24）

### 第 19 关 · 私有对象存储

**依次操作：** 创建 `BUCKET assets`，visibility=`private`；创建 `OBJECT logo`，name=`static/logo.svg`，sizeMb=`2`，bucket=`assets`；Inspect `dev`。

**通关注意：** 默认保持私有，不要开启匿名读。

### 第 20 关 · 收紧存储权限

**依次操作：** `reports.visibility=private`；`reports-read.verb=read`；Inspect `dev`。

**通关注意：** Bucket 匿名可见性与 IAM 权限是两个不同维度。

### 第 21 关 · 配置对象生命周期

**依次操作：** 将 `logs-bkt.lifecycle=logs/|archive:30|delete:90`；终端 `advance 1`；Inspect `dev`。

**通关注意：** 按前缀 logs/、30 天归档、90 天删除设置。

### 第 22 关 · 创建私有数据库

**依次操作：** 创建 `DATABASE orders-db`（subnet=db-sub, privateIp=`10.10.3.20`, nsgs=db-nsg, port=1521, adminSecret=`vault:orders-admin`, autoBackup=true, retentionDays=30）；`db-nsg.rules=ingress|10.10.2.0/24|tcp|1521|stateful`；`app-nsg.rules=ingress|10.10.1.0/24|tcp|8080|stateful;egress|10.10.3.0/24|tcp|1521|stateful`；`app.dbEndpoint=orders-db`、`app.dbSecret=vault:orders-admin`；`advance 5`；Inspect `dev`。

**通关注意：** 数据库仅私网、Vault 引用凭据；需要 5 tick。

### 第 23 关 · 数据库备份恢复

**依次操作：** 创建 `DATABASE orders-restore`（subnet=db-sub, sourceBackup=bk-70, privateIp=`10.10.3.21`, nsgs=db-nsg, port=1521, adminSecret=`vault:orders-admin`, autoBackup=true, retentionDays=30）；`advance 5`；`app.dbEndpoint=orders-restore`；Inspect `dev`。

**通关注意：** 先等恢复数据库 AVAILABLE 再切换应用。

### 第 24 关 · 泄露与备份安全修复

**依次操作：** `exports.visibility=private`；`app.dbSecret=vault:orders-2030`；`orders-db.autoBackup=true`、`retentionDays=30`；创建 `DB_BACKUP orders-ondemand`（type=FULL, database=orders-db）；Inspect `dev`。

**通关注意：** 完成对象私有化、凭据轮换、备份与保留期。

## 第 5 章：负载均衡、DNS、TLS 与门户（25–30）

### 第 25 关 · 上线负载均衡

**依次操作：** 创建 `COMPUTE web2`（web-sub, privateIp=`10.10.1.11`, publicIp 空，nsgs=web-nsg, listenPorts=8080, sshKey=ops-key, shape=`VM.Standard.E4.Flex.2`, image=`Oracle-Linux-9`, httpPaths=`/,/health`）；创建 `LOAD_BALANCER lb`（web-sub, privateIp=`10.10.1.50`, publicIp=`203.0.113.50`, nsgs=lb-nsg, listenPorts=80, backends=`web1:8080,web2:8080`, healthPath=`/health`, healthTimeoutMs=3000, redirectHttp=false）；`advance 2`；Inspect `dev`。

**通关注意：** 后端列表写完整，并等待 web2 RUNNING。

### 第 26 关 · 健康检查排障

**依次操作：** 编辑 `lb.healthPath=/health`；`lb.backends=web1:8080,web2:8080`；`web2.listenPorts=8080`；Inspect `dev`。

**通关注意：** 健康路径、后端端口、实际监听端口必须一致。

### 第 27 关 · 设置 DNS

**依次操作：** 创建 `DNS_ZONE starport-zone`，domain=`starport.example`，records=`www|A|203.0.113.50|300;api|CNAME|www.starport.example|300`；Inspect `dev`。

**通关注意：** A 指向 LB 公网 IP，CNAME 目标为完整域名。

### 第 28 关 · 配置 TLS 与重定向

**依次操作：** 创建 `CERTIFICATE www-cert`（commonName=`www.starport.example`, validTicks=90）；编辑 `lb.listenPorts=80,443`、`lb.redirectHttp=true`、certificate=`www-cert`；编辑 `lb-nsg.rules=ingress|0.0.0.0/0|tcp|80|stateful;egress|10.10.1.0/24|tcp|8080|stateful;ingress|0.0.0.0/0|tcp|443|stateful`；Inspect `dev`。

**通关注意：** HTTPS 要有证书及 443，重定向还需要 80 可达。

### 第 29 关 · 高负载容量修复

**依次操作：** 创建 `web3`（web-sub, privateIp=10.10.1.12, listenPorts=8080, shape=`VM.Standard.E4.Flex.2`, image=`Oracle-Linux-9`, httpPaths=`/,/health`）；创建 `web4`（web-sub, privateIp=10.10.1.13, listenPorts=8080, shape=`VM.Standard.E4.Flex.1`, image=`Oracle-Linux-9`, httpPaths=`/,/health`）；两者 publicIp 留空、nsgs=web-nsg、sshKey=ops-key；`lb.backends=web1:8080,web2:8080,web3:8080,web4:8080`、`lb.healthTimeoutMs=500`；`advance 2`；Inspect `dev`。

**通关注意：** 两个新实例都要创建，且 LB 后端须包含四台。

### 第 30 关 · 门户毕业排错

**依次操作：** `starport-zone.records=www|A|203.0.113.50|300`；创建 `CERTIFICATE www-2031`（commonName=`www.starport.example`, validTicks=365）；设置 `lb.certificate=www-2031`；`web2.httpPaths=/,/health`；Inspect `dev`。

**通关注意：** 修正 DNS、证书有效期、后端 /health，确认 HTTPS 链路。

## 常用终端命令（仅用于模拟查询、探测与推进）

```text
oci iam compartment list
oci iam group list
oci iam policy list
oci network vcn list
oci network subnet list
oci compute instance list
oci bv volume list
oci bv backup list
oci os bucket list
oci os object list
oci db database list
oci db backup list
oci lb load-balancer list
oci lb backend-health lb
oci dns zone list
oci certs certificate list
inspect commander dev
curl --from web http://app:8080
ssh operator@web
ssh --bastion bastion operator@app
df -h app
dig www.starport.example
curl https://www.starport.example/
advance 5
```

**注意**：以上仅是可用语法示例，并非每关都需要执行。终端中的 `inspect <principal> <id>` 与 Console 的 Inspect 可能对应相同的教学动作；golden fixtures 中的 `inspect` 已作为可选证据步骤注明。不要对真实 OCI 账户复制模拟 IP、假密钥或教学密码。

## 主要卡关原因与检查顺序

| 现象 | 核查 |
|---|---|
| 提示命令不支持 | OCI Command Terminal 只支持查询、探测、时间推进；**创建和更新要到 Console 表单**。 |
| Region 目标不达成 | 选 `ap-tokyo-1`，不要选 `ap-osaka-1`。 |
| IAM 拒绝访问 | Group Members、Dynamic Group match、Policy verb/type/scope/target 逐项查，保持最小权限。 |
| CIDR/子网冲突 | 子网必须属于 VCN，网段不能重叠，Private Subnet 不能分配 Public IP。 |
| 网络不通 | 依次检查 Route Table → IGW/NAT/SGW → NSG 出入站、回程 → 目标端口 Listener。 |
| SSH 失败 | Public IP、22 端口 NSG、监听和 sshKey 需全部正确；Bastion 还要检查允许 CIDR、session、target。 |
| Compute/DB 还在 Provisioning | 终端 `advance 2`（Compute）或 `advance 5`（DB），再检查状态。 |
| 卷仍不能用 | 核实容量、attachedTo、mountPoint、日志路径及源备份容量。 |
| 对象存储权限不对 | `visibility=private` 是匿名访问控制，不能替代 IAM Policy。 |
| LB 后端不健康 | 实例运行、监听 8080、healthPath /health、backend 列表及相关 NSG 规则都必须一致。 |
| HTTPS 失败 | DNS A 记录、443 Listener、证书 commonName/有效期、LB NSG、后端健康逐层诊断。 |
| 做完仍只有 1–2 星 | 首先检查安全 constraints、可选 Inspect 证据、操作次数、提示次数和成本预算。 |
| 误操作导致无法三星 | 用撤销或重置关卡从该关独立 initialWorld 重新按顺序操作。 |

## 已实现范围与真实 OCI 的区别

- 游戏目前只完成 **L01–L30**；L31–L60 仍是后续计划，禁止凭产品规格伪造已经实现的通关步骤。
- 游戏里的所有 IP（如 `203.0.113.50`）、模拟密钥（`ops-key`）、凭据别名（`vault:orders-admin`）都仅供教学。
- 创建、更新、删除均通过游戏 Console 的资源表单触发 `GameAction`，不是实际 OCI CLI；`advance` 也不是 OCI CLI 命令。
- 本攻略根据自动化黄金路线生成；不同版本的 UI 字段顺序、部分隐藏默认值可能不同。以当前 APK 的 Mission、Console 错误提示及当前仓库 JSON 为准。

## 维护与回归验证

- [原始 30 关 JSON](../content/chapters)
- [逐关 golden fixtures](../core/testing/fixtures)
- [模拟器规则和终端白名单](rules-catalog.md)
- [已完成范围与验收](implementation-status.md)
- [测试结果](test-results.md)

修改关卡或模拟规则后，更新这份秘籍，运行：
```powershell
python scripts/validate_content.py
.\gradlew.bat validateContent solveLevels
```
