# OCI Cloud Commander — Android 云运维学习游戏开发规格 v1.0

> 方案 C：云端城市建设 + OCI Console 配置 + 终端运维故障排查。目标：60 关完整离线学习游戏，Android 手机/平板。所有服务为本地模拟，不连接真实 OCI。

## 0. 产品定位与交付标准

- 项目包名 `com.example.ocicloudcommander`（发布前替换）；游戏中文名《OCI 云端守护者》，英文名 OCI Cloud Commander。
- 目标用户：初学者、云运维工程师、OCI/IAM/网络/OKE 学习者。主要学习语言简体中文，技术名词用 Oracle 文档英文，预留日语和英语国际化。
- 手机以横屏为主要交互模式，平板支持宽屏多窗格；最小建议 Android 10 / API 29，target SDK 与编译 SDK 采用开发时 Google Play/Android SDK 最新稳定兼容值，不硬编码未知未来版本。
- 单机、离线、无账号、无广告、无云 API、无计费。Android TTS 为可选功能：离线可用性取决于用户设备上是否安装对应语音包，游戏不能把 TTS 不可用作为阻塞条件。
- 每一关有可验收的初始状态、世界状态修改、玩家操作、检查器、失败事件、提示、三星评分和自动化测试。
- 交付源码、完整 60 关数据、自动化测试、构建指南、Debug APK 构建任务；不能编译时如实记录阻塞，不能假称已构建成功。
- 不使用 Oracle 官方 Logo、Oracle 控制台截图、商标作为游戏品牌，不宣称 Oracle 官方授权产品。OCI 术语和 API 模式仅用于教育模拟并在游戏中注明非官方。

## 1. 世界观、角色与剧情

2030 年虚构企业 Astra Cloud Operations 接管星港市（Starport）的混合云业务。开局：新员工在东京区域建立实验资源。中期：公司接入新客户并迁移订单系统，经历攻击、权限不足、配置错误、数据库恢复、邮件投递异常、容器化改造。后期：自动化建设与综合事故演练，最终遇到跨区域业务中断。

角色：
- 玩家（新晋工程师，角色名可编辑）：行动主体；不限定性别。
- 林岚（导师/架构师）：概念教学、关卡复盘。
- 佐藤美咲（SRE 值班负责人）：告警、故障分析、变更审批。
- Ravi（安全负责人）：IAM、NSG、最小权限、Audit。
- Nova（虚拟助手）：可选逐级提示，只基于关卡数据和规则给出提示，离线不接在线 LLM。
- The Glitch（拟人化故障代理）：以配置错漏、资源失效、服务退化造成挑战，不暗示未经授权攻击现实系统。

章节剧情：1 新人报到；2 建设网络城；3 上线第一台服务；4 数据守护；5 门户开放；6 不眠监控；7 消失的邮件；8 容器舰队；9 自动化革命；10 星港危机。每章最终关为章节 Boss，并有清晰过场、复盘与新系统解锁。

## 2. 主要玩法、交互及屏幕

### 2.1 屏幕与导航

1. 启动画面：继续游戏、章节选择、自由实验室、故障挑战、设置。
2. 章节星图：10 章节×6 节点；显示完成率、锁定条件、奖章。
3. 城市资源视图（Map）：俯视/等角视觉建筑、区域边框、逻辑连线、故障脉冲动画；地图布局只影响视觉展示，真正网络以配置的 CIDR、路由、安全规则判断。
4. OCI 控制台视图（Console）：左侧资源树与 Region/Compartment 选择；主区域为服务配置表单、资源清单和详细状态；右侧可折叠帮助。
5. 终端视图（Terminal）：命令输入栏、命令历史、自动补全、可点选示例命令；支持 `oci`, `kubectl`, `ping`, `curl`, `dig`, `ssh`, `nslookup`, `terraform` 的白名单教学子集。
6. 监控中心（Incident）：Metric 趋势、Alert 时间线、Logs、Trace 证据、Notification/Email Delivery 状态、根因归纳。
7. 任务弹层（Mission）：剧情、当前目标、可选目标、危险操作确认、提示等级、成绩。
8. 复盘界面：变更时间线、根因、知识点、错误解释、星级、下一关。
9. 自由实验室：所有已解锁资源的模拟沙箱；支持预设蓝图、快照、重置。
10. 设置：声音、朗读语速、暗色/高对比、字号、语言、震动、屏幕常亮（仅游戏前台且用户开启）、学习资料。

### 2.2 输入交互

- 点击添加资源、拖拽放置建筑、长按显示信息、捏合缩放地图、点击端点连接逻辑链路。
- Console 使用原生控件编辑字段，IPv4 CIDR 校验、端口 1–65535 校验、对象关系与缺失权限即时反馈。
- 终端支持输入复制粘贴、多行 YAML 练习（仅指定关卡）、命令提示按钮；手机输入不要求盲打长 OCID。
- 回退/重做：本关配置动作支持 undo/redo；危险删除须确认；关卡重置独立于全局存档。
- 错误产生因果反馈，例如：`403 NotAuthorizedOrNotFound`、模拟 `connection timeout`、`ImagePullBackOff`，并可定位到当前世界状态证据。

### 2.3 教育玩法

- **建设**：在城市地图逐层放置 VCN/Subnet/Compute/LB/Database/OKE。
- **配置**：模拟 OCI Console 中的 IAM、路由、NSG、卷、报警、SMTP、OKE 配置。
- **执行**：模拟 CLI/命令运行，操作实际更改共享状态。
- **观察**：模拟流量、服务健康、日志、指标、告警和变更后结果。
- **诊断**：需要修复根因而不是只选择答案。
- **复盘**：显示“症状→证据→根因→修复→验证”的链条。

## 3. 六十关逐关设计

每条记录格式：`编号 标题｜初始/异常｜玩家关键操作｜验收断言`。逐关 JSON 需包含四个字段并扩展为完整 Step、Action、Failure、Tutorial、Hints、Validator、Rewards 数据。

### Chapter 1 新人报到：OCI 基础与 IAM（L01–L06）

01 **登录云端**｜空白租户教学快照，东京区域未选｜选择 Region `ap-tokyo-1`，认清 Tenancy / Region / AD / FD｜当前区域正确，术语概念卡完成。
02 **项目隔离**｜测试、生产混放默认 Compartment｜建立 `dev`、`prod` compartment，将示例资源分别归类｜不存在生产资源落入 dev，树结构正确。
03 **谁能看见它**｜新人用户缺权限，资源列表拒绝｜建立 `operators` Group，添加身份，赋予 dev 中 inspect/read 的教学 Policy｜新人只能查看目标资源，不能删除。
04 **只给必要权限**｜Policy 过宽导致安全红牌｜将 `manage all-resources` 缩小到明确资源类型、动作范围和 compartment｜完成指定操作、生产环境无越权。
05 **实例身份**｜工作负载需获取允许的 OCI 资源但不应内嵌用户密钥｜配置 Dynamic Group 匹配规则与权限策略，模拟 Instance Principal 授权｜目标实例可读指定存储，其他实例不允许。
06 **权限迷宫·Boss**｜混杂 Region、Compartment、Group、Policy、Dynamic Group 缺陷｜逐项检查并修复，但不得给予 tenancy 管理员权限｜验收目标操作全部通过；越权探测全部拒绝。

### Chapter 2 网络之城：VCN（L07–L12）

07 **画出网络**｜不存在 VCN｜创建 `10.10.0.0/16` VCN 和 `10.10.1.0/24` subnet｜Subnet 包含于 VCN 且不重叠。
08 **公共与私有**｜Web/App/DB 全放公有网段｜分别建立公有 Web 子网、私有 App/DB 子网；校验公网 IP 与私有属性｜只有指定公有实例可公网寻址。
09 **打开城门**｜Web 实例无法出站连公网｜部署 Internet Gateway 与匹配的 route table 规则，并满足公网 IP/安全规则｜公网路由有效且外部探测成功。
10 **私有上网**｜私网应用需下载补丁，不允许互联网入站｜配置 NAT Gateway、相应 Route Table 与出站规则｜私网出站成功，公网主动入站被拒绝。
11 **服务专线与防火墙**｜访问特定受支持 OCI 公共服务路径失败，NSG 规则错误｜按场景配置 Service Gateway 路由及 NSG 的 443 出站与必要回程规则｜目标 OCI 服务私有访问成功，禁止多余暴露。
12 **网络黑洞·Boss**｜CIDR 冲突、错误路由、被拒绝端口与错误公共标记｜分析拓扑、路由和 NSG，修复 Web→App→DB 路径｜端到端健康检查成功，DB 从公网不可达。

### Chapter 3 计算引擎：Compute/Block Volume（L13–L18）

13 **启动首台实例**｜无计算资源｜选择教学 Shape、OS Image、Subnet、VNIC、SSH key（模拟），启动 Compute｜状态从 provisioning→running，初始化成功。
14 **不能 SSH**｜目标实例 ssh 失败｜逐层排查密钥、目标地址、网络路由、NSG ingress 22、实例监听｜模拟 SSH 成功，非授权来源仍被拒绝。
15 **跳板救援**｜私网实例不可从公网直接管理｜配置 Bastion 会话与允许来源/目标端口，并测试安全连接｜经 Bastion 连接成功，实例不拥有公网 IP。
16 **磁盘告急**｜根卷空间不足，日志写入失败｜附加 Block Volume、设置模拟挂载点并迁移日志，检查容量｜磁盘不足事件清除，日志业务恢复。
17 **备份前夜**｜关键卷无可恢复备份｜创建卷备份计划、执行快照，并测试到隔离测试卷的恢复｜恢复校验 checksum 与数据版本正确。
18 **服务器风暴·Boss**｜实例重启、存储不足、SSH 连不上，多因组合｜按变更顺序恢复卷→网络→服务并执行健康测试｜目标服务稳定，数据未丢失，满足安全约束。

### Chapter 4 数据守护：Object Storage & Database（L19–L24）

19 **第一座存储仓**｜静态资源无存储｜创建 Bucket，上传教学数据，选择适当可见性｜对象可被允许主体读取，默认不公开。
20 **权限之锁**｜桶存在错误公共访问和对象读取拒绝｜修正访问策略、身份权限，验证匿名拒绝与应用允许｜授权读取成功、匿名读取失败。
21 **生命周期工程**｜旧日志耗费预算｜设定适当的对象生命周期归档/删除规则（模拟时间快进）｜新对象保留，超过年龄阈值的对象按规则迁移/清理。
22 **数据库上线**｜新业务无 DB｜配置虚拟数据库服务（简化 OCI DB System/Autonomous Database 训练模式）、私网访问与密钥引用｜App 可以访问，公网直连拒绝。
23 **丢失的订单**｜开发误删数据｜选择正确恢复点/备份恢复到隔离环境，再安全执行切换｜业务数据完整，恢复目标 RPO 达标。
24 **数据保卫战·Boss**｜公有存储泄漏风险、DB 连接错误、恢复点超期｜隔离风险、修复应用链路并重新制定备份策略｜三个业务探针通过、安全规则全部满足。

### Chapter 5 业务门户：LB/DNS/TLS（L25–L30）

25 **分担流量**｜单 Compute 过载｜创建两后端与 Load Balancer，配置 Backend Set、Health Check 和 Listener｜请求均衡分配且全部健康。
26 **消失的后端**｜LB 某后端 Unhealthy｜检查目标端口、健康检查路径和服务进程｜Backend Set 中所有预期后端健康。
27 **域名在哪里**｜客户端只知道服务 IP｜创建模拟 DNS Zone 与 A 记录（教学 DNS 解析器），测试解析｜模拟 dig 返回正确预期地址。
28 **加密的入口**｜HTTP 暴露敏感业务｜配置模拟 HTTPS Listener、证书资源与 HTTP→HTTPS 重定向｜TLS 模拟握手成功、HTTP 自动重定向。
29 **突发大流量**｜请求骤增，后端性能下降｜添加后端、调优健康阈值、模拟容量扩缩容｜规定时段内成功率、延迟、成本满足目标。
30 **门户停摆·Boss**｜DNS 错误、证书失效、LB Backend 不健康｜修复解析→证书→健康检查，进行端到端探测｜模拟 HTTPS 业务连通且冗余恢复。

### Chapter 6 不眠监控：Monitoring/Logging/Notifications（L31–L36）

31 **读懂脉搏**｜CPU 与成功率曲线异常无监测｜添加指定 Metric 图表、选择 namespace/dimension/time window｜图表覆盖监控目标并识别峰值。
32 **告警阈值**｜高 CPU 无自动报警｜创建 Alarm，设置适用的指标条件、持续时间和严重度｜超阈值时间窗后转为 FIRING，恢复时转 OK。
33 **通知链路**｜Alarm FIRING 但指定团队没有收到通知｜绑定 OCI Notifications Topic/Subscription，完成订阅确认模拟，检查权限/投递状态｜事件投递到模拟 Inbox；与 Email Delivery 区分。
34 **查找真相**｜应用 5xx 增长，指标无上下文｜开启资源 Logging，按 requestId/timeSeverity 过滤日志，定位超时服务｜根因证据集齐并修复对应配置。
35 **错误风暴**｜CPU 98%、应用停止响应、通知缺失｜排查应用超载与独立的 Notifications 配置，修复两处问题｜业务恢复、Alarm 清除、模拟收件箱收到恢复消息。
36 **值班室·Boss**｜复合事件：磁盘、网络、阈值/通知异常｜建立事件优先级、定位根因、处理并产出复盘｜SLO 恢复，关键告警触发/恢复闭环通过。

### Chapter 7 消失的邮件：Email Delivery（L37–L42）

37 **批准发信身份**｜业务应用需邮件发送但 From 未授权｜创建 Approved Sender，匹配当前 Region 与 Compartment，并赋权｜模拟邮件不会因发件人未批准而被拒。
38 **认证邮件通道**｜SMTP 配置错误导致认证失败｜创建/绑定 SMTP Credentials（模拟秘密不可明文记录），设置服务器、TLS、端口｜模拟 SMTP AUTH 与 TLS 通道通过。
39 **DNS 身份证明**｜业务域 DNS 认证不正确｜按教学流程配置 SPF 和 DKIM 相关 DNS 记录，检测格式与匹配情况｜域身份检查成功；明确 DKIM 缺失不应在所有情形必然导致投递失败。
40 **送出但未收到**｜SMTP accepted 但用户收不到信｜读投递事件/抑制/认证/模拟收件端策略，区分 accepted、delivered、bounced、blocked｜定位真正失败阶段并实现 delivered。
41 **退信与抑制**｜重复发送触发抑制/退信模拟｜使用合规流程处理失效地址、调整退信策略，重试合法接收方｜有效地址 delivered；无对错误地址的盲目重发。
42 **邮件谜案·Boss**｜Approved Sender、SMTP、DNS、收件端拒绝混合故障｜从 API/SMTP 到 DNS/收件端逐段检查｜交易邮件 delivered、错误诊断与安全约束全部达标。

### Chapter 8 容器舰队：OKE/Kubernetes（L43–L48）

43 **启动舰队**｜无集群和节点｜创建模拟 OKE Cluster、Managed Node Pool、网络条件与工作节点｜控制面可访问，节点 Ready（必要 OCI IAM 合规）。
44 **第一个 Pod**｜镜像可用，尚未部署｜通过模拟 `kubectl apply -f` 创建 Deployment 与 Pod｜期望副本 Ready、状态 Running。
45 **寻找服务**｜Pod 正常但业务端点不可访问｜创建 Service、配置 Selector、targetPort/port，并检查 ClusterIP/LoadBalancer 类型｜指定 Service 到 Pod 的流量通过。
46 **权限被拒**｜操作者可查询 OKE，但不能更新 Namespace 部署｜区分 OCI IAM 与 Kubernetes RBAC，配置正确 Role/RoleBinding（限定 Namespace）｜允许部署修改、禁止跨 Namespace 管理。
47 **Pod 的噩梦**｜ImagePullBackOff + CrashLoopBackOff + 不足容量组合｜检查模拟 `describe`/logs/events、镜像、环境变量、资源请求，适当扩容｜Deployment 所有期望副本 Ready。
48 **舰队失联·Boss**｜错误网络、安全、集群角色、节点故障与 Service 错配｜依层定位控制面、节点、工作负载和服务｜外部探测成功、RBAC 不越权、资源请求满足容量。

### Chapter 9 自动化革命：CLI/Terraform/Security（L49–L54）

49 **第一次 CLI**｜手动配置繁琐｜通过受限 `oci iam compartment list`、`oci compute instance list` 等模拟 CLI 查询｜返回值与当前 WorldState 一致。
50 **用代码建云**｜空白新项目需要网络｜完成 Terraform 教学 HCL 模板的缺失变量，`terraform plan` 预览、`apply` 建资源｜目标拓扑与预期一致；只支持已定义教学子集。
51 **识别配置漂移**｜人工改动偏离 Terraform 计划｜模拟 `terraform plan` diff，审查并恢复基线｜实际状态匹配声明目标，未经批准变更清零。
52 **秘密不应外泄**｜凭证错误放到明文环境/日志｜迁移秘密引用至模拟 Vault、收敛读权限与输出脱敏｜日志无秘密明文，允许主体仍能访问。
53 **审计追踪**｜生产资源被误删，需要追责线索｜按时间、主体、动作查询模拟 Audit 事件，恢复资源｜定位事件主体、时间、动作并恢复服务。
54 **自动化守门人·Boss**｜HCL 漂移、过宽 Policy、Secrets 暴露及自动化失败｜建立安全流水线检查、最小权限与变更审批，再 apply｜部署通过、敏感值不泄漏、无越权操作。

### Chapter 10 星港危机：综合演练（L55–L60）

55 **架构审查**｜不合格单点 Web-App-DB 结构｜用地图架构设计两层负载均衡、私网数据、防护和监控｜全部架构策略检查通过。
56 **成本警报**｜实例超配和闲置资源导致预算告警｜比较教学价格单位、选合适 Shape/存储层与容量｜月度模拟成本低于预算、SLO 不下降。
57 **潜伏的权限漏洞**｜生产组有过大权限且日志记录不足｜修复 IAM、审计与网络暴露，不中断业务｜攻击面模拟规则检查通过、合法任务可执行。
58 **区域中断预案**｜主区域健康，备用区无可用恢复点｜设置跨区域模拟副本、配置备用 DNS/业务切换和 RPO/RTO｜演练符合明确数值目标。
59 **灾难降临**｜主区域模拟中断、部分备份落后、DNS 指向旧服务｜按事故流程切流、恢复数据、监视可用性｜指定业务探针恢复，满足安全与 RPO/RTO 约束。
60 **星港总决战·Final Boss**｜网络中断、OKE Pod 故障、数据库退化、通知链路异常、容量限制、成本预算多重问题｜建立优先级，基于监控+日志+IAM+CLI+拓扑分段处理，做完整恢复验证与复盘｜SLO、数据一致性、权限、安全、预算与告警闭环均达标；解锁全图沙盒与首席架构师结局。

## 4. 关卡难度和评分

- 新手 L01–12：引导明显，3 次提示免费；中级 L13–36：需要至少两种证据；高级 L37–54：跨服务组合；专家 L55–60：资源、安全、成本多约束。
- 星数分层：`1星=all required goals true`；`2星=1星+没有严重安全违例+预算限制满足`；`3星=2星+所有 optional goals + hintsUsed<=1 + changes<=level.optimalActionLimit`。
- 不因读题速度或动作操作速度影响通关；限时仅可选挑战模式，防止学习者焦虑。
- XP：每关 100–500；奖章：网络侦探、权限守卫、日志大师、OKE 舰长、最终守护者；XP 不能替代关卡验收。
- 关卡允许无限重置与重试，教程中解释本地模拟与真实 OCI 控制台差异。

## 5. 技术架构

### 5.1 技术栈

- Android：Kotlin、Jetpack Compose、Material 3、Navigation Compose、ViewModel、StateFlow、Kotlin Coroutines。
- Dependency injection：Hilt 或简单显式依赖注入（初版优先显式，避免工具链版本耦合）。
- Persistence：Room（进度、事件/快照）、DataStore（设置）。
- Serialization：kotlinx.serialization（JSON Schema 随文件维护，运行时 DTO 校验）。
- Map：Compose Canvas 绘图/手势与原创 Vector 资源，无需 Unity/3D 引擎。
- Tests：JUnit、Kotest 可选、Turbine 可选、Compose UI Test、Gradle CI。
- Min SDK：API 29，目标 SDK 按构建环境最新稳定可用版选择并在 README 固定验证版本。
- TTS：Android TextToSpeech 离线优先、设备语音包缺失时允许无声学习。

### 5.2 Gradle 多模块

```
oci-cloud-commander/
├── app/
├── core/model/
├── core/content/
├── core/simulator/
├── core/command/
├── core/persistence/
├── core/testing/
├── feature/home/
├── feature/world-map/
├── feature/console/
├── feature/terminal/
├── feature/incident/
├── feature/mission/
├── feature/lab/
├── feature/settings/
├── content/chapters/ch01..ch10/level-001..level-060.json
├── content/schemas/level.schema.json
├── content/locales/zh-CN.json
├── content/locales/en-US.json
├── docs/architecture.md
├── docs/rules-catalog.md
├── docs/oci-fidelity.md
├── docs/content-guide.md
├── docs/test-plan.md
├── .github/workflows/android-ci.yml
├── README.md
└── gradlew
```

核心原则：UI 不直接写世界状态，所有改动通过 `GameEngine.dispatch(Action)`；世界状态序列化为确定性结构；Rules 不访问 UI、系统时间、网络或文件系统；终端最终映射到同一组 Action。

### 5.3 基础 Kotlin 对象

```kotlin
@Serializable data class WorldState(
  val schemaVersion: Int,
  val levelId: String,
  val tick: Long,
  val resources: Map<String, SimResource>,
  val identities: Map<String, Principal>,
  val policies: List<PolicyStatement>,
  val events: List<SimEvent>,
  val budget: SimBudget,
  val scenarioFlags: Set<String>
)

@Serializable data class SimResource(
  val id: String,
  val kind: ResourceKind,
  val region: String,
  val compartmentId: String,
  val state: ResourceState,
  val properties: JsonObject,
  val references: Map<String, String> = emptyMap()
)

sealed interface GameAction {
  data class CreateResource(val type: ResourceKind, val id: String, val properties: Map<String, String>): GameAction
  data class UpdateResource(val id: String, val patch: Map<String, String>): GameAction
  data class DeleteResource(val id: String): GameAction
  data class ExecuteCommand(val principalId: String, val input: String): GameAction
  data class AdvanceSimulation(val ticks: Int): GameAction
  data class RunProbe(val probeId: String): GameAction
}

data class DispatchResult(
  val world: WorldState,
  val events: List<SimEvent>,
  val outcome: ActionOutcome,
  val objectiveStatus: Map<String, Boolean>
)

interface GameEngine {
  suspend fun dispatch(action: GameAction): DispatchResult
  suspend fun resetLevel(levelId: String)
  fun observeWorld(): StateFlow<WorldState>
}
```

生产代码可将资源属性从 `JsonObject` 转为 sealed typed resources。示例只是接口规范，编译需要补齐 Import、enum、DTO、序列化定义。

## 6. 模拟引擎设计

### 6.1 确定性处理流水线

`UI Gesture or CLI → Validate syntax/schema → Resolve identity & IAM → Validate preconditions → Apply action immutable reduction → Advance ticks → Recompute derived state (network/service/metrics) → Generate logs/events → Evaluate goals and score → Persist snapshot → Render UI`。

- 使用 `seeded Random` 与可注入 `SimClock`，严禁业务逻辑直接读取 `System.currentTimeMillis()`。
- 一个动作要么整体成功提交，要么返回错误，不能产生半写入状态。
- 世界资源 ID 使用固定教学 ID，不使用真实 OCID 或真实 Oracle 账号。
- 日志按事件序列决定，重复回放相同初始状态+相同动作须得到相同结果。
- 初始快照、任务动作和回放日志均带 schemaVersion；内容升级支持迁移或回退兼容。
- `TimeAdvance` 驱动 Metric 评估、健康检查、Backup、生命周期转换、SMTP 投递、Pod 调度。

### 6.2 网络连通性模型

网络模拟器输入 `source/destination/protocol/port/principal`，输出 `ReachabilityResult{allowed, path, blockedAt, reason}`。规则顺序：源状态→目标状态→IP 地址/网段→选择 Route Table→下一跳网关→公网/IP 属性→NSG/Security List rules→无状态/有状态回程规则→目标监听服务→应用健康。简化但显式记录简化点。不得把可视化的地图连线当作网络层连接条件。支持 RFC1918、CIDR overlap、最长前缀匹配、路由缺失、Internet/NAT/Service Gateway 语义教学子集。

### 6.3 IAM 模型

使用授权主体 `User/Group/DynamicGroup/InstancePrincipal`、action、resourceType、scope、compartment、policyStatement 与条件子集做 allow/deny 评估，默认无权限。`inspect/read/use/manage` 有包含关系但按 OCI 教学规则表映射允许动作；保留 service-specific action 覆盖。高危 `manage all-resources` 可能满足操作但触发安全约束失败。RBAC 需与 OCI IAM 分开实现，`Role/RoleBinding/Verb/Resource/Namespace` 单独判断。

### 6.4 Compute & Storage

Compute 状态机 `PROVISIONING→RUNNING↔STOPPED→TERMINATING→TERMINATED`；依子网、Image、Shape 容量、密钥及 IAM 决定创建/访问结果。Block Volume 维护容量、attachment、filesystemMount、fileDataChecksum、backups。Object Storage 模拟 Bucket/Object、Visibility、StorageTier、Lifecycle、authorized principal，不连接真存储服务。

### 6.5 LB / DNS / TLS

LB 有 Listener、BackendSet、BackendHealth、HealthCheck、TrafficPolicy；运行流量时按健康后端与教学加权规则调度。DNS 模拟解析与 TTL；TLS 模拟证书有效期、Host 匹配和 HTTPS 配置，不实现真实 TLS 密钥操作，必须用 UI 明确是仿真。

### 6.6 Monitoring / Logging / Notifications

监控点数据：`MetricPoint(tick, namespace, name, dimensions, value)`；Alarm 包含 query selector、threshold、operator、pendingDuration、state(FIRING/OK)、lastTransitionTick。Notifications 是 Topic→Subscription(confirm state)→InboxDelivery 的独立路径。Logging/Audit 基于事件派生，可用时序/requestId 搜索，并含明确 evidenceId。

### 6.7 Email Delivery

对象：ApprovedSender、SmtpCredentials(仅 hash/模拟 secret reference)、DomainAuth(SPF/DKIM)、EmailRequest、EmailDeliveryEvent。状态：`CREATED→ACCEPTED→DELIVERED` 或 `REJECTED/BOUNCED/BLOCKED/DEFERRED`。按明确的场景规则解释 SPF/DKIM/approvedSender/suppression，而不是武断地把 DKIM 缺失等同所有邮件必失败。应用邮件路径与 Notifications Topic 邮件订阅路径独立。

### 6.8 OKE + Kubernetes

OKE 对象：Cluster(control plane endpoint status)、NodePool、Node、Namespace、Deployment、ReplicaSet(简化)、Pod、Service、Role、RoleBinding。Pod 状态 Pending→Running→Ready / ImagePullBackOff / CrashLoopBackOff；调度检查 Node Ready/CPU/Mem/capacity；Service 以 selector/endpoints/port/targetPort 进行探测。`kubectl` Parser 支持 `get`, `describe`, `logs`, `apply -f <内置文件别名>`, `scale`, `rollout status` 教学子集。明确它不是通用 kube-apiserver。

### 6.9 Terraform/CLI

- OCI CLI 子集：`oci iam compartment list`, `oci compute instance list`, `oci network vcn list`, `oci ce cluster list` 等，参数可接受教学短 ID。全部命令解析为只读 query 或 action。
- Terraform 教学 DSL：只实现为关卡服务的受限 HCL 样式资源声明，`init`, `plan`, `apply`, `state list`, `destroy`（沙盒限定）。不得调用真实 Terraform，不宣称完整解析器。
- 错误输出采用与真实工作流概念相近但注明 `SIMULATED` 的格式。

### 6.10 成本与 SLO

价格按游戏固定模拟 Credits/小时，**不是 Oracle 官方实时价格**；每关设 `budget.maxCredits` 和 `durationTicks`。可靠性 KPI 可为可用率、5xx、延迟、RPO/RTO、通知完成率；全为模拟模型并记录 seed。

## 7. JSON Schema 设计

建议发布 `draft 2020-12` JSON Schema，核心结构：

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.invalid/schemas/oci-level.schema.json",
  "title": "OCI Cloud Commander Level",
  "type": "object",
  "additionalProperties": false,
  "required": ["schemaVersion", "id", "chapter", "order", "titleKey", "scenario", "initialWorld", "allowedActions", "objectives", "reward"],
  "properties": {
    "schemaVersion": {"const": 1},
    "id": {"type": "string", "pattern": "^oci-[0-9]{3}$"},
    "chapter": {"type": "integer", "minimum": 1, "maximum": 10},
    "order": {"type": "integer", "minimum": 1, "maximum": 60},
    "titleKey": {"type": "string", "minLength": 1},
    "scenario": {"type": "object", "required": ["region", "briefKey"], "properties": {
      "region": {"type": "string"}, "briefKey": {"type": "string"}, "seed": {"type": "integer"}
    }, "additionalProperties": false},
    "initialWorld": {"type": "object", "required": ["resources"], "properties": {
      "resources": {"type": "array", "items": {"$ref": "#/$defs/resource"}},
      "principals": {"type": "array", "items": {"type": "object"}},
      "policies": {"type": "array", "items": {"type": "object"}},
      "events": {"type": "array", "items": {"type": "object"}},
      "budgetCredits": {"type": "number", "minimum": 0}
    }, "additionalProperties": false},
    "allowedActions": {"type": "array", "minItems": 1, "items": {"type": "string"}},
    "steps": {"type": "array", "items": {"type": "object", "required": ["id", "textKey"], "properties": {
      "id": {"type": "string"}, "textKey": {"type": "string"}, "hintKeys": {"type": "array", "items": {"type": "string"}}
    }, "additionalProperties": false}},
    "objectives": {"type": "array", "minItems": 1, "items": {"$ref": "#/$defs/objective"}},
    "constraints": {"type": "array", "items": {"$ref": "#/$defs/objective"}},
    "optionalObjectives": {"type": "array", "items": {"$ref": "#/$defs/objective"}},
    "failureTriggers": {"type": "array", "items": {"$ref": "#/$defs/objective"}},
    "tutorialKeys": {"type": "array", "items": {"type": "string"}},
    "optimalActionLimit": {"type": "integer", "minimum": 1},
    "reward": {"type": "object", "additionalProperties": false, "required": ["xp", "maxStars"], "properties": {
      "xp": {"type": "integer", "minimum": 0}, "maxStars": {"const": 3}, "badgeId": {"type": "string"}
    }}
  },
  "$defs": {
    "resource": {"type": "object", "required": ["id", "kind", "compartment", "region", "properties"], "additionalProperties": false, "properties": {
      "id": {"type": "string"}, "kind": {"type": "string"}, "compartment": {"type": "string"},
      "region": {"type": "string"}, "properties": {"type": "object"}, "references": {"type": "object", "additionalProperties": {"type": "string"}}
    }},
    "objective": {"type": "object", "required": ["id", "predicate", "params"], "additionalProperties": false, "properties": {
      "id": {"type": "string"}, "predicate": {"type": "string"}, "params": {"type": "object"}, "messageKey": {"type": "string"}
    }}
  }
}
```

运行时另做语义校验：Objective Predicate 必须注册，引用的 ID 必须存在或规定为任务产物；Region 必须来自已知列表；关卡依赖不成环；动作类型存在；本地化 key 全部可解析；所有关卡至少存在一条可达通关路径；不允许全局初始 secret 明文。

关卡 L35 示意：

```json
{
  "schemaVersion": 1,
  "id": "oci-035",
  "chapter": 6,
  "order": 35,
  "titleKey": "levels.035.title",
  "scenario": {"region": "ap-tokyo-1", "briefKey": "levels.035.brief", "seed": 3501},
  "initialWorld": {
    "resources": [
      {"id": "app1", "kind": "COMPUTE", "compartment": "prod", "region": "ap-tokyo-1", "properties": {"health": "DOWN", "cpu": 98, "servicePort": 8080}},
      {"id": "alarm1", "kind": "MONITORING_ALARM", "compartment": "prod", "region": "ap-tokyo-1", "properties": {"status": "FIRING", "topicId": "topic1"}},
      {"id": "topic1", "kind": "NOTIFICATION_TOPIC", "compartment": "prod", "region": "ap-tokyo-1", "properties": {"subscriptionConfirmed": false}}
    ],
    "principals": [], "policies": [], "events": [], "budgetCredits": 500
  },
  "allowedActions": ["inspect", "configure_app", "configure_subscription", "run_probe", "advance_tick"],
  "steps": [
    {"id": "s1", "textKey": "levels.035.step1", "hintKeys": ["levels.035.hint1"]},
    {"id": "s2", "textKey": "levels.035.step2", "hintKeys": ["levels.035.hint2"]}
  ],
  "objectives": [
    {"id": "appHealthy", "predicate": "resource.propertyEquals", "params": {"id": "app1", "key": "health", "value": "HEALTHY"}},
    {"id": "noticeDelivered", "predicate": "notifications.delivered", "params": {"topicId": "topic1"}}
  ],
  "constraints": [
    {"id": "noBroadPermissions", "predicate": "security.noBroadPermissions", "params": {}}
  ],
  "optionalObjectives": [
    {"id": "allProbesPass", "predicate": "probe.success", "params": {"probeId": "end-to-end"}}
  ],
  "failureTriggers": [],
  "tutorialKeys": ["tutorial.monitoring", "tutorial.notifications"],
  "optimalActionLimit": 8,
  "reward": {"xp": 400, "maxStars": 3, "badgeId": "night-watch"}
}
```

注意：上述 L35 JSON 是 Schema 演示而不是可直接运行的完整案例。实际关卡还需把事件与动作转移配置到独立规则表中，否则修改 app health 等同作弊；实现时由动作和模拟器规则产生 `HEALTHY`，不允许 UI 直接篡改此字段。

## 8. 可验证 Predicate 库

- `resource.exists`, `resource.stateEquals`, `resource.propertyEquals`, `resource.compartmentEquals`
- `iam.permits`, `iam.denies`, `iam.leastPrivilege`, `rbac.permits`, `rbac.denies`
- `network.reachable`, `network.unreachable`, `network.nonOverlapping`, `network.hasRoute`
- `compute.running`, `storage.backupRestorable`, `storage.objectRestricted`, `db.dataChecksumEquals`
- `lb.backendsHealthy`, `dns.resolves`, `tls.validHostname`
- `monitoring.alarmState`, `notifications.delivered`, `logging.evidenceFound`
- `email.approvedSender`, `email.smtpAuthValid`, `email.delivered`, `email.dnsAuthValid`
- `oke.nodesReady`, `k8s.deploymentReady`, `k8s.serviceReachable`, `k8s.rbacAllowed`
- `terraform.planConverged`, `audit.eventIdentified`, `security.noPlaintextSecret`
- `cost.underBudget`, `slo.meetsTarget`, `disaster.rpoWithin`, `disaster.rtoWithin`

每个谓词有严格类型参数 Schema、纯函数 `evaluate(world, params)`、人类可读成功/失败原因、至少一组通过/不通过单测。

## 9. 关卡的防作弊与状态一致性

- 禁止通过单纯点击“完成目标”直接设置 `objective=true`。
- ViewModel 不可直接改模拟值，地图/控制台/终端都只能发送 GameAction。
- Goal Evaluator 每次从 WorldState 重新计算。
- 任务的初始错误与修复步骤必须可复现，测试 runner 使用设计好的 action sequence 验证。
- 强制负面测试：非法策略、缺路由、错 Pod selector、错凭证都失败，并有具体 blockedAt/reason。
- 任务剧情描述与真实 WorldState 必须一致；故事可不透露根因，但不能自相矛盾。

## 10. 测试规范

### 10.1 单元测试

1. CIDR 包含关系、网段冲突、Longest Prefix Match。
2. NSG ingress/egress 默认拒绝、有状态回程、port/protocol 匹配。
3. Public IP、IGW/NAT/Service Gateway 行为区分。
4. IAM inspect/read/use/manage、Compartment scope、Dynamic Group 与 Instance Principal。
5. OCI IAM vs K8s RBAC 不混用。
6. Compute/Block Volume/Backup 状态转换与校验。
7. Bucket 默认私有、policy 范围、生命周期 tick。
8. LB healthy backend 与故障摘除。
9. Alarm threshold + pending + OK/FIRING，Topic subscription confirmed 约束。
10. Email accepted 与 delivered 区别、Approved Sender、SPF/DKIM 场景式判断。
11. Pod Scheduling/CrashLoop/ImagePull/Service selector/RBAC。
12. Terraform plan/apply/drift；模拟 CLI 命令白名单。
13. 成本计算和 RPO/RTO 数学约束。
14. 输入校验、权限不足、动作事务性、防止非法状态。

### 10.2 内容测试

- 校验全部 60 个 JSON 与 JSON Schema、ID 唯一性、章节每章恰好 6 关、关卡解锁 DAG 无循环、本地化 key 完整、资源引用正确、所有 Predicate 存在。
- 对 60 关逐一运行通过脚本（golden path），必须可获得至少 1 星。
- 每关至少有一条典型失败脚本，且应无法误通关。
- Boss 关验证不得靠单一动作或答题完成，必须修复两个以上独立问题。

### 10.3 UI 与系统测试

- 手机 16:9/19.5:9/20:9、平板 16:10、折叠/分屏布局基本可用。
- 横屏旋转恢复、后台/前台、进程终止后恢复、设备重启后存档恢复。
- 触控目标、TalkBack、字体扩大 200%、高对比色、色盲下不依赖颜色区分状态。
- 终端软键盘弹出时输入框不被遮挡、复制粘贴、撤销、重做、错误提示不丢失。
- 断网和无 TTS 语音包均可完成游戏。
- 长时间操作不异常黑屏：仅在用户明确启用并在关卡活动前台时使用 `FLAG_KEEP_SCREEN_ON`；用户按电源键锁屏时遵从系统控制。不需后台音频的情况下不申请不必要后台权限。
- 性能预算：中端 Android 手机上地图典型 100 资源节点交互流畅，内存与启动时间具体阈值用设备基准测试后定标。
- 回归测试不应需要 OCI 账户、不应产生网络流量到 OCI API。

### 10.4 CI 必须通过

- `./gradlew lintDebug`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- JSON Schema / semantic validator task
- Headless level solver task（60 关 golden path + negative paths）
- UI Instrumentation tests 适合在 emulator runner 可用时执行。

## 11. 分阶段开发计划与每阶段交付

### Phase 0：技术地基

- Gradle 工程、导航、主题、UI 原型、文本国际化、内容加载器、Schema validator、确定性 WorldState、事件流、快照持久化、测试框架。
- 验收：可启动 APP，加载测试世界，编辑一个资源，重启后继续且事件重放一致。

### Phase 1：第一完整闭环（L01–L12）

- 实现 IAM + VCN/网关/路由/NSG + 城市地图/Console/任务步骤/CLI 基础。
- 验收：12 关 golden path/negative path 全通过；L06/L12 Boss 真正需要多步动作解决。

### Phase 2：基础平台（L13–L30）

- Compute、Bastion、Block Volume、Object Storage、简化 DB、LB、DNS、TLS。
- 验收：前 30 关全部具备玩法，跨服务联动且不能“点完成”。

### Phase 3：运维（L31–L42）

- Metrics、Alarm、Notifications、Logging、Email Delivery。
- 验收：通知邮件和 SMTP Email Delivery 的路径独立；Accepted≠Delivered；通过全部 42 关。

### Phase 4：容器与自动化（L43–L54）

- OKE、K8s RBAC、受限 kubectl、Terraform DSL、CLI、Vault/Audit。
- 验收：容器状态由调度规则驱动、权限分离、漂移/审计检测正确。

### Phase 5：最终章+打磨（L55–L60）

- 灾备、成本、综合 incident、剧情、画质、辅助功能、平板优化。
- 验收：60 关逐关可通关，60 条失败路径验收、CI green、签发可安装 APK。

## 12. 给 Codex 的完整开发总提示词

下面是可直接复制到 Codex 的指令。将**本文件**保存在仓库根目录 `docs/OCI_Cloud_Commander_Android_Development_Spec.md`，以便 Codex 直接读取完整 60 关需求，而不是依赖对话上下文。

---

你是资深 Android Kotlin 工程师、云基础设施模拟器架构师和技术教育游戏设计师。请在当前仓库实现一款名为 **OCI Cloud Commander（OCI 云端守护者）** 的完整离线安卓游戏。首先阅读 `docs/OCI_Cloud_Commander_Android_Development_Spec.md` 的全部内容，将该文档作为唯一产品需求源；所有 60 个关卡、规则引擎、内容 Schema、画面和测试必须与之保持一致。

**交付目标**：Android 手机和平板可运行；横屏为主；游戏是“云端城市建设地图 + 仿真 OCI Console + CLI/日志/故障排查”的混合策略学习游戏，不是选择题合集。用户可以通过创建/配置模拟 OCI 资源让业务拓扑真实生效。必须离线运行、无需真实 OCI 账号、不访问 OCI API、不发生真实费用。

**约束**：使用 Kotlin、Jetpack Compose、Material3、ViewModel、StateFlow、Coroutines、kotlinx.serialization、Room、DataStore；建议 Gradle 多模块。不要使用 Unity、WebView 包装网站、真实 shell/SSH/OCI SDK 连接；所有命令必须经过白名单 Parser 映射为纯模拟 Action。不得下载或嵌入 Oracle 官方商标 Logo 或非授权素材；采取原创中性云资源图形，清晰标记“非官方教育模拟器”。默认简体中文，技术名词英文，预留 en-US/ja-JP。支持可选 TTS 和设置前台屏幕常亮，设备锁屏行为遵循 Android 系统。

**开发顺序**：
1. 检查仓库，编写实施计划与文件结构；如仓库为空，初始化 Android Gradle 工程并安装固定版本工具链。创建架构说明、Schema、content loader、WorldState、GameAction、StateReducer、SimClock、Validation、EventLog、PredicateRegistry、ProgressRepository。
2. 优先交付 L01–L12 的真实可玩闭环：地图可编辑 VCN/Subnet，控制台有 IAM/路由/NSG 表单，终端可运行教学命令。实现网络 CIDR、路由、网关、NSG、IAM 评估的真实本地规则，不使用通过题目按钮代替操作。编写 golden path 与 negative path 测试。
3. 增加 Compute/Volume/ObjectStorage/Database/LB/DNS/TLS 规则与 L13–L30 数据。
4. 增加 Monitoring/Logging/Notifications/Email Delivery 与 L31–L42 数据。Email Delivery 要有 Approved Sender/SMTP/SPF/DKIM/Accepted/Delivered/Bounce 等真实概念；不得错误假设 DKIM 未设置就一律送达失败；OCI Notifications 与 Email Delivery 不能混为一个 service。
5. 增加 OKE/NodePools/Nodes/Namespaces/Deployments/Pods/Services、Kubernetes RBAC、CLI、Terraform 教学 DSL、Audit、Secrets 与 L43–L54 数据。OCI IAM 与 K8s RBAC 不可合并；Pod/Service 健康随节点容量、镜像、进程、selector 等条件变化。
6. 完成灾难恢复/成本/SLO/第55–60关与章末剧情，完善自由实验室、失败复盘和成就系统。
7. 执行 lint、单元测试、所有 60 关可通关模拟脚本、失败路径测试、Debug 构建；维护 README、CHANGELOG、设计差异表和后续工作记录。

**关卡内容**：严格按规格文件中 60 条逐关要求建立 `content/chapters/ch01..ch10/level-001..level-060.json`，必须每关均具备初始资源、问题、关键动作、必达 objectives、安全 constraints、失败信息、提示文本、奖励和至少一个 golden path 单测。不要用标题和几道选择题来冒充完成的关卡。Boss 必须包含多层故障。

**引擎规则**：UI/命令输入只提交 `GameAction`；业务规则统一在 Simulator 执行；对同一 seed、WorldState 和 Action 序列输出完全确定；模拟时间可快进；每个动作通过权限、前置条件、合法性校验后才可原子提交；指标/事件/日志/告警从变更派生；目标谓词从真实 WorldState 判断。错误响应应能解释 block point 和 root cause，不要硬编码“点一次修复就通过”。

**画面**：主页、章节星图、建设地图、OCI 仿真 Console、Terminal、Monitoring/Incident、Mission/Hints、通关复盘、Sandbox、Settings。地图支持点击放置、选择和关联资源、缩放、状态颜色与形状双重提示；Console 适配平板分栏；终端适配手机软键盘；危险删除需确认。保存当前关卡与关卡状态并可中断恢复。支持暗色、高对比与大字体。

**验收**：必须具备 JSON Schema 校验与语义校验，60 关 ID 唯一、章节各 6 关；每关至少有一个成功解法脚本和一个负例脚本；所有实战目标由模型推导。编译失败则记录具体环境阻塞，不要虚构结果。至少运行 `./gradlew lintDebug testDebugUnitTest assembleDebug`（当前环境可用时），在 README 记录真实执行命令与结果。不要访问真实 OCI API，不处理真实密钥。最终输出目录清单、游戏截图/Preview（构建环境支持时）、测试结果、构建 APK 路径、尚未实现的特性和下一步建议。

**工作方式**：直接开始实现，不要把工作退化为写一篇方案；大任务分阶段提交可运行成果，每阶段保持可编译、可测试、可玩。优先完成规则正确、关卡可解和手机可操作，再完善插画/动效。遇到文档示例与架构冲突时，保留原始学习目标、按最小修改扩展 Schema，并更新文档说明，不要静默丢弃功能。

---

## 13. 进一步精度核验说明

OCI 为持续演化的云服务。本游戏是有边界的教学模拟器，不是 OCI 全功能 API 实现。具体的资源类型、IAM 动词、服务区域限制、DNS/TLS/Email Delivery/OKE 版本、计价请在编码涉及真实语义时以 Oracle 官方文档为准，维护 `docs/oci-fidelity.md` 标注“精确模拟/简化教学/未覆盖”。不将教学内置价格称为 Oracle 实价。

官方参考：
- OKE 概念：https://docs.oracle.com/en-us/iaas/Content/ContEng/Concepts/contengclustersnodes.htm
- OKE 综述：https://docs.oracle.com/en-us/iaas/Content/ContEng/Concepts/contengoverview.htm
- Monitoring：https://docs.oracle.com/en-us/iaas/Content/Monitoring/Concepts/monitoringoverview.htm
- IAM Policies：https://docs.oracle.com/en-us/iaas/Content/Identity/Concepts/policies.htm
- Email Delivery Approved Sender：https://docs.oracle.com/en-us/iaas/Content/Email/Reference/gettingstarted_topic-Create_an_approved_sender.htm
