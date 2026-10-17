package commander

import kotlinx.serialization.Serializable

@Serializable
enum class Kind {
    COMPARTMENT, GROUP, DYNAMIC_GROUP, POLICY, VCN, SUBNET, IGW, NAT, SERVICE_GATEWAY, ROUTE_TABLE, NSG, COMPUTE, BUCKET,
    BASTION, BASTION_SESSION, VOLUME, VOLUME_BACKUP, OBJECT, DATABASE, DB_BACKUP, LOAD_BALANCER, DNS_ZONE, CERTIFICATE
}

@Serializable
data class Resource(
    val id: String, val kind: Kind, val compartment: String = "dev", val region: String = "ap-tokyo-1",
    val properties: Map<String, String> = emptyMap(), val references: Map<String, String> = emptyMap()
) { fun p(key: String) = properties[key].orEmpty(); fun ref(key: String) = references[key].orEmpty() }

@Serializable
data class Principal(val id: String, val instanceId: String? = null)
@Serializable
data class SimEvent(val sequence: Int, val tick: Long, val actor: String, val action: String, val detail: String)
@Serializable
data class MapPoint(val x: Int, val y: Int)
@Serializable
data class WorldState(
    val schemaVersion: Int = 1, val levelId: String = "", val seed: Long = 1, val tick: Long = 0,
    val region: String = "", val resources: Map<String, Resource> = emptyMap(),
    val principals: List<Principal> = listOf(Principal("commander"), Principal("newcomer")),
    val studiedCards: Set<String> = emptySet(), val evidence: Set<String> = emptySet(),
    val events: List<SimEvent> = emptyList(), val changes: Int = 0, val hintsUsed: Int = 0,
    val layout: Map<String, MapPoint> = emptyMap(), val budgetCredits: Double = 500.0,
    /** Scenario traffic in simulated requests/second; never writable by actions. */
    val requestRate: Int = 0
)
@Serializable
data class GameAction(
    val type: String, val id: String = "", val resource: Resource? = null,
    val patch: Map<String, String> = emptyMap(), val references: Map<String, String> = emptyMap(),
    val value: String = "", val actor: String = "commander", val ticks: Int = 1
)
@Serializable
data class Objective(val id: String, val predicate: String, val params: Map<String, String> = emptyMap(), val messageKey: String)
@Serializable
data class Scenario(val region: String, val briefKey: String, val seed: Long)
@Serializable
data class Step(val id: String, val textKey: String, val hintKeys: List<String>)
@Serializable
data class Reward(val xp: Int, val maxStars: Int = 3, val badgeId: String = "")
@Serializable
data class Level(
    val schemaVersion: Int = 1, val id: String, val chapter: Int, val order: Int, val titleKey: String,
    val scenario: Scenario, val initialWorld: WorldState, val allowedActions: Set<String>,
    val steps: List<Step>, val objectives: List<Objective>, val constraints: List<Objective>,
    val optionalObjectives: List<Objective> = emptyList(), val failureTriggers: List<Objective> = emptyList(),
    val tutorialKeys: List<String>, val optimalActionLimit: Int, val reward: Reward,
    val dependsOn: List<String> = emptyList(), val products: Map<String, Kind> = emptyMap()
)
@Serializable
data class Solution(val levelId: String, val golden: List<GameAction>, val negative: List<GameAction>)
@Serializable
data class SaveGame(val schemaVersion: Int = 1, val levelId: String, val actions: List<GameAction>, val cursor: Int, val snapshot: WorldState)

data class Check(val passed: Boolean, val reason: String)
data class Reachability(val allowed: Boolean, val path: List<String>, val blockedAt: String = "", val reason: String = "")
data class DispatchResult(val world: WorldState, val accepted: Boolean, val message: String)
data class Score(val stars: Int, val required: List<Pair<Objective, Check>>, val constraints: List<Pair<Objective, Check>>)
object Catalog {
    val regions = setOf("ap-tokyo-1", "ap-osaka-1", "us-ashburn-1")
    val cards = mapOf("Tenancy" to "Tenancy 是隔离的租户边界；本游戏全部为本地教学资源。", "Region" to "Region 是地理区域，资源区域影响可见性与网关关联。", "AD" to "Availability Domain 是区域内独立故障域集合。", "FD" to "Fault Domain 在 AD 内隔离硬件故障风险。")
    val actions = setOf("select_region", "study_card", "create", "update", "delete", "inspect", "probe", "advance_tick", "hint", "move", "power", "ssh", "dig", "http")
    /** Observation actions do not edit configuration; their normal ticks can finish pending provisioning. */
    val observations = setOf("inspect", "probe", "ssh", "dig", "http")
    /** Teaching shapes: OCPU, served requests/second, simulated monthly Credits. Not Oracle prices. */
    data class Shape(val ocpu: Int, val rps: Int, val credits: Double)
    val shapes = mapOf(
        "VM.Standard.E4.Flex.1" to Shape(1, 300, 10.0), "VM.Standard.E4.Flex.2" to Shape(2, 600, 25.0),
        "VM.Standard.E4.Flex.4" to Shape(4, 1200, 60.0), "VM.Standard.A1.Flex.2" to Shape(2, 500, 12.0)
    )
    val defaultShape = Shape(2, 600, 20.0)
    val images = setOf("Oracle-Linux-9", "Ubuntu-24.04")
    /** Simulated SSH clients: source address and key alias. No real key material is accepted anywhere. */
    data class Client(val ip: String, val key: String, val label: String)
    val clients = mapOf(
        "operator" to Client("198.51.100.10", "ops-key", "值班工程师工作站"),
        "intruder" to Client("192.0.2.66", "ops-key", "未登记来源（模拟：即使持有被复制的密钥，也应被来源规则拒绝）")
    )
}
