package commander

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

object ContentLoader {
    val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
    fun level(text: String, strings: Map<String,String>): Level = json.decodeFromString<Level>(text).also { validate(it,strings) }
    fun validate(level: Level, strings: Map<String,String>) {
        require(level.schemaVersion == 1 && level.id == "oci-%03d".format(java.util.Locale.ROOT,level.order))
        require(level.order in 1..60 && level.chapter == (level.order-1)/6+1)
        require(level.initialWorld.schemaVersion == 1 && level.initialWorld.levelId == level.id && level.initialWorld.seed == level.scenario.seed)
        require(level.scenario.region in Catalog.regions && level.allowedActions.isNotEmpty() && level.allowedActions.all { it in Catalog.actions })
        require(level.objectives.isNotEmpty() && level.steps.isNotEmpty() && level.optimalActionLimit > 0 && level.reward.maxStars == 3 && level.reward.xp in 100..500)
        val keys=listOf(level.titleKey,level.scenario.briefKey)+level.steps.flatMap { listOf(it.textKey)+it.hintKeys }+level.tutorialKeys+
            (level.objectives+level.constraints+level.optionalObjectives+level.failureTriggers).map { it.messageKey }
        require(keys.all { !strings[it].isNullOrBlank() }) { "本地化 key 缺失: ${keys.filter { strings[it].isNullOrBlank() }}" }
        val all=level.objectives+level.constraints+level.optionalObjectives+level.failureTriggers
        require(all.map { it.id }.distinct().size == all.size) { "重复 Objective ID" }
        all.forEach { require(Predicates.parameters[it.predicate] == it.params.keys) { "${it.id} 未注册 Predicate/参数不匹配" }
            val ids=level.initialWorld.resources.keys+level.products.keys
            val kinds=level.initialWorld.resources.mapValues { entry -> entry.value.kind }+level.products
            it.params["kind"]?.let { kind -> require(kind in Kind.entries.map { k -> k.name }) { "未知资源 Kind" } }
            it.params["verb"]?.let { verb -> require(verb in setOf("inspect","read","use","manage")) { "未知 IAM verb" } }
            it.params["compartment"]?.let { id -> require(kinds[id] == Kind.COMPARTMENT) { "目标 Compartment 未声明" } }
            if(it.predicate == "world.region") require(it.params["value"] in Catalog.regions)
            if(it.predicate == "network.publicAddressable") require(it.params["value"] in setOf("true","false"))
            if(it.predicate == "resource.exists") require(kinds[it.params["id"]]?.name == it.params["kind"]) { "目标 Kind 与已声明资源类型不一致" }
            if(it.predicate == "resource.referenceEquals") require(it.params["value"] in ids) { "目标关联资源未声明" }
            listOf("id","source","destination","vcn","target","app","restored","bucket").forEach { key -> it.params[key]?.let { id -> require(id in ids || id in setOf("internet","oci-service","root")) { "目标引用 $id 未存在且非任务产物" } } }
            it.params["via"]?.let { via -> require(via == "direct" || kinds[via] == Kind.BASTION) { "via 必须为 direct 或 Bastion" } }
            it.params["client"]?.let { client -> require(client in Catalog.clients) { "未知 SSH 客户端" } }
            it.params["tier"]?.let { tier -> require(tier in setOf("STANDARD","ARCHIVE")) }
            listOf("min","maxTicks","maxAge","minRetention","maxLatencyMs","minBackends").forEach { key -> it.params[key]?.let { n -> require(n.toIntOrNull() in 0..100000) { "$key 需为非负整数" } } }
            listOf("archiveDays","deleteDays").forEach { key -> it.params[key]?.let { value -> require(value.toIntOrNull() in 1..3650) { "$key 范围 1–3650" } } }
            it.params["minSuccessPct"]?.let { n -> require(n.toDoubleOrNull()?.let { v -> v in 0.0..100.0 } == true) }
            it.params["url"]?.let { url -> require(url.matches(Regex("https?://[a-z0-9.-]+/?"))) { "url 需为 http(s)://host/" } }
            it.params["ids"]?.split(',')?.forEach { id -> require(id in ids) }
            it.params["port"]?.let { port -> require(port.toIntOrNull() in 1..65535) }
            it.params["principal"]?.let { id -> require(level.initialWorld.principals.any { principal -> principal.id == id }) }
        }
        require(level.initialWorld.principals.map { it.id }.distinct().size == level.initialWorld.principals.size)
        require(level.initialWorld.resources.all { (id,r) -> id == r.id && r.region in Catalog.regions })
        require(level.initialWorld.resources.values.all { r ->
            r.properties.keys.all { it in Validation.fields.getValue(r.kind) || it in Validation.derived[r.kind].orEmpty() } &&
            r.references.keys.all { it in Validation.refs[r.kind].orEmpty() } &&
            r.references.values.all { it in level.initialWorld.resources } &&
            (r.compartment == "root" || level.initialWorld.resources[r.compartment]?.kind == Kind.COMPARTMENT)
        }) { "初始世界悬空引用" }
        require(level.initialWorld.resources.values.none { r -> r.properties.any { (k,v) -> k.contains("secret",true) && v.isNotEmpty() && !v.startsWith("vault:") } }) { "禁止初始明文 secret" }
    }
    fun validateCollection(levels: List<Level>, complete: Boolean = false) {
        require(levels.map { it.id }.distinct().size == levels.size)
        if(complete) { require(levels.size == 60); (1..10).forEach { c -> require(levels.count { it.chapter == c } == 6) } }
        val byId=levels.associateBy { it.id }; val visiting=mutableSetOf<String>(); val done=mutableSetOf<String>()
        fun visit(id: String) { if(id in done) return; require(visiting.add(id)) { "关卡依赖成环" }; val l=byId[id] ?: error("依赖不存在 $id"); l.dependsOn.forEach(::visit); visiting.remove(id); done.add(id) }
        levels.forEach { visit(it.id) }
    }
}
