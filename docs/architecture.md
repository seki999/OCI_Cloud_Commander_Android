# 架构与数据流

原始需求源：OCI_Cloud_Commander_Android_Development_Spec.md。当前阶段不裁减后续目标，但仅将已实现且可验证的内容加入可玩 index。

`Compose / Terminal → GameAction → Simulator.dispatch → syntax / identity / permission / precondition → immutable resource transaction → tick / provisioning → SimEvent → Predicates.score → Room snapshot + action log → StateFlow → Compose`。

`core/model` 不依赖 Android；`core/simulator` 不读文件、网络、时钟，不依赖 UI。`core/command` 不运行 shell。`core/content` 负责严格 DTO 和语义检查。`core/persistence` 为 Android Room / DataStore。依赖以构造参数/显式实例注入，未使用 Hilt。

Phase 1 无随机事件，seed 固定于 WorldState，tick 仅由接受的 Action 推进。同一初始状态与 action sequence 产生完全相同的资源、布局、目标及事件。Simulator 是无状态 StateReducer；ViewModel 是串行化 GameEngine 适配层，以 Mutex 防止保存/撤销/命令竞争。任何 Action 失败都返回原 WorldState，不推进 tick，不追加成功事件。失败原因保留于 UI / Terminal。

资源 properties 使用受限 String Map 作为可扩展教学 DTO，按 Kind 注册字段和语义验证；引用字段验证目标类型、Region、VCN。`status` 和 `readyTick` 仅由规则设置，禁止 UI/脚本写入。网络结果每次从配置重算，不储存“已连通”标志。章节目标无 setter。

Room session 原子保存初始世界标识、动作序列、历史游标及快照；加载重放并校验 snapshot equality。进度采用最高星级，不因重置回退；当前关卡 reset 只清本关状态。撤销/重做重放已接受动作，布局也在动作历史中。存档 schemaVersion=1；未知版本拒绝加载并提示，迁移未来版本尚未实现。存档不加密/签名，防作弊针对游戏正常输入和非法 Action，并非抵抗恶意修改 APK/本地数据库。

统一资源网络引用是 VCN → Subnet → Compute，Subnet 可关联 Route Table，Route Table 指向 Gateway；Compute 关联同一 VCN 的 NSG。地图 layout 只有视觉语义，不能改变 reachability。IAM 的训练操作者 commander 是明确的教学配置角色；关卡被考核的 newcomer / Instance Principal 按默认拒绝策略计算，不能冒用 commander 通过目标。

JSON Schema 扩展保持 schemaVersion=1，以 products 声明关卡将创建的资源 ID/Kind，便于校验目标的前向引用；其余核心字段对应原规格。initialWorld 与生产 WorldState DTO 一致，而非文档中的示例简写 resources array。Schema 的 Predicate union 严格限制参数集合，运行时再次检查。每次新增类型需要同步模型、字段/关系验证、规则、Predicate 参数、Schema 和正反例。

首版 UI 位于 app 的 Compose 组件，按核心依赖拆为六个模块，未为了目录数量创建空 feature 模块。Phase 2 后按功能成熟程度拆分 feature/world-map、console 等，避免把未实现功能伪装为已有模块。
