# 测试计划

已自动化：

- 所有已注册关卡的初始不通过、golden 三星、典型 negative 不通过、逐个遗漏关键修复不通过、Boss 不可单动作解决。
- JSON Schema、ID/章节数量（当前阶段）、本地化、引用、严格 Predicate 参数、DAG；完整产品 60 关门禁。
- 确定性序列化重放、撤销/重做、防非法派生状态写入、权限拒绝与事务原子性。
- CIDR/IP、Route LPM、NSG 方向/协议/端口/stateful/stateless、网关差异、Public IP、Instance Principal/最小权限、最小 provisioning 状态机。
- 所有注册 Predicate 的至少一个通过/不通过样例；非法参数一律失败。
- Phase 2 规则：电源状态机、磁盘挂载归属、SSH/Bastion 各阻断点、卷备份不可变与恢复容量、Bucket 匿名/授权、生命周期只在推进时间时运行、DB 供应/明文拒绝/恢复点 RPO、LB 健康分层与过载雪崩、DNS CNAME/环/非法记录、TLS 过期/主机名/通配符层级、陈旧 DNS 诊断、成本推导、观察动作零副作用、新命令白名单与注入拒绝、Schema 与运行时注册表一致。
- Compose 设备用例源码：L25 经 ViewModel/Room 三星与截图；真实 Console Region 选择、概念卡、Inspect、三星、旋转重建、关闭 Activity 后新 ViewModel 从 Room 重放恢复。

测试命令和真实执行结果见 test-results.md，不能把编译测试 APK 当作执行通过。

仍需补充的设备矩阵：Android 10/API 29 与 API 35+；16:9、19.5:9、20:9 手机；16:10 平板；折叠/分屏；200% 字号；TalkBack；色盲与高对比；软键盘/复制粘贴/终端历史；系统强杀/重启设备后的 Room 恢复；离线且无中文 TTS 包；用户主动锁屏；100 节点地图的帧时间、内存和启动测量。

仍需后续阶段增加：Alarm/Email/OKE/Terraform/灾备规则单测与 L31–L60 成功/失败脚本。

构建脚本/CI 必须同时执行 Android tasks 与 :core:testing:test，因 `testDebugUnitTest` 不自动包含纯 JVM 模块。当前 workflow 是 Phase 0–2 门禁，不能称全产品 CI green。
