package commander

import kotlin.math.min
import kotlin.math.roundToInt

/** Phase 2 service rules. Every function is pure: results are recomputed from WorldState, never stored as flags. */
data class Health(val healthy: Boolean, val reason: String)

private fun list(text: String, separator: Char = ',') = text.split(separator).map(String::trim).filter(String::isNotEmpty)

object Disk {
    const val osGb = 10.0
    /** Filesystem receiving a path: the attached volume with the longest matching mount point, else the root volume. */
    fun mountFor(w: WorldState, compute: Resource, path: String): Resource? = w.resources.values
        .filter { it.kind == Kind.VOLUME && it.ref("attachedTo") == compute.id && it.p("mountPoint").isNotEmpty() && (path == it.p("mountPoint") || path.startsWith(it.p("mountPoint") + "/")) }
        .maxByOrNull { it.p("mountPoint").length }
    fun check(w: WorldState, compute: Resource): Health {
        val logGb = compute.p("logGb").toDoubleOrNull() ?: 0.0
        if (logGb <= 0.0) return Health(true, "无持续写入的日志负载")
        val path = compute.p("logPath").ifEmpty { "/var/log/app" }
        val volume = mountFor(w, compute, path)
        val (capacity, used, where) = if (volume != null) Triple(volume.p("sizeGb").toDouble(), logGb, "${volume.id}:${volume.p("mountPoint")}")
            else Triple(compute.p("rootGb").toDoubleOrNull() ?: 50.0, osGb + logGb, "根卷 /")
        val pct = (used / capacity * 100).roundToInt()
        return if (used / capacity > 0.9) Health(false, "$path 写入 $where 使用率 $pct%（>90%），日志写入失败") else Health(true, "$path 位于 $where，使用率 $pct%")
    }
}

object Ssh {
    fun check(w: WorldState, clientId: String, targetId: String, via: String): Reachability {
        val client = Catalog.clients[clientId] ?: return Reachability(false, listOf(clientId), "client", "未知 SSH 客户端")
        val target = w.resources[targetId]
        if (target?.kind != Kind.COMPUTE) return Reachability(false, listOf(clientId), targetId, "目标实例不存在")
        val network = if (via == "direct") Network.reachable(w, "internet", targetId, 22, sourceIp = client.ip) else {
            val bastion = w.resources[via]
            if (bastion?.kind != Kind.BASTION) return Reachability(false, listOf(clientId), via, "Bastion 不存在")
            if (!Cidr.parse(bastion.p("allowedCidr")).contains(Cidr.ip(client.ip))) return Reachability(false, listOf(clientId, via), "$via.allowlist", "客户端 ${client.ip} 不在 Bastion 允许 CIDR")
            val session = w.resources.values.firstOrNull { it.kind == Kind.BASTION_SESSION && it.ref("bastion") == via && it.ref("target") == targetId && it.p("port") == "22" }
                ?: return Reachability(false, listOf(clientId, via), "$via.session", "没有指向 $targetId:22 的 Bastion Session")
            if (w.tick >= session.p("expiresTick").toLong()) return Reachability(false, listOf(clientId, via), "${session.id}.ttl", "Session 已过期")
            if (session.p("sshKey") != client.key) return Reachability(false, listOf(clientId, via), "${session.id}.key", "Session 公钥别名与客户端密钥不匹配")
            Network.reachable(w, via, targetId, 22).let { it.copy(path = listOf(clientId) + it.path) }
        }
        if (!network.allowed) return network
        if (target.p("sshKey") != client.key) return Reachability(false, network.path, "$targetId.authorized_keys", "实例未授权密钥 ${client.key}（Permission denied (publickey)）")
        return Reachability(true, network.path, reason = "SSH 握手模拟成功（SIMULATED，非真实连接）")
    }
}

object Lb {
    data class Backend(val id: String, val port: Int)
    data class BackendHealth(val id: String, val port: Int, val healthy: Boolean, val reason: String)
    data class Slo(val utilizationPct: Int, val successPct: Double, val latencyMs: Int)
    fun backends(lb: Resource) = list(lb.p("backends")).map { val p = it.split(':'); require(p.size == 2 && p[1].toIntOrNull() in 1..65535) { "Backend 格式 compute-id:port" }; Backend(p[0], p[1].toInt()) }
    private fun capacity(w: WorldState, ids: List<String>) = ids.sumOf { id -> (Catalog.shapes[w.resources[id]?.p("shape")] ?: Catalog.defaultShape).rps }
    private fun latency(utilization: Double) = (40.0 / (1.0 - min(utilization, 0.95))).roundToInt()
    fun utilization(w: WorldState, ids: List<String>): Double = capacity(w, ids).let { if (it == 0) Double.POSITIVE_INFINITY else w.requestRate.toDouble() / it }
    fun health(w: WorldState, lb: Resource): List<BackendHealth> {
        val staticChecks = backends(lb).map { b ->
            val target = w.resources[b.id]
            val reason = when {
                target?.kind != Kind.COMPUTE -> "后端实例不存在"
                else -> Network.reachable(w, lb.id, b.id, b.port).let { r ->
                    if (!r.allowed) "${r.blockedAt}: ${r.reason}"
                    else if (lb.p("healthPath") !in list(target.p("httpPaths"))) "HTTP 404：后端未提供健康检查路径 ${lb.p("healthPath")}"
                    else ""
                }
            }
            BackendHealth(b.id, b.port, reason.isEmpty(), reason.ifEmpty { "HTTP 200 ${lb.p("healthPath")}" })
        }
        val candidates = staticChecks.filter { it.healthy }.map { it.id }
        if (candidates.isEmpty()) return staticChecks
        val ms = latency(utilization(w, candidates)); val timeout = lb.p("healthTimeoutMs").toInt()
        return if (ms > timeout) staticChecks.map { if (it.healthy) it.copy(healthy = false, reason = "健康检查超时：响应 ${ms}ms > timeout ${timeout}ms（过载）") else it } else staticChecks
    }
    fun slo(w: WorldState, lb: Resource): Slo {
        val healthy = health(w, lb).filter { it.healthy }.map { it.id }
        if (healthy.isEmpty()) return Slo(0, 0.0, 0)
        val u = utilization(w, healthy)
        return Slo((u * 100).roundToInt(), if (u <= 1.0) 100.0 else 100.0 / u, latency(u))
    }
    fun report(w: WorldState, lb: Resource): String = health(w, lb).joinToString("\n") { "${it.id}:${it.port}\t${if (it.healthy) "OK" else "CRITICAL"}\t${it.reason}" }.ifEmpty { "无后端" } +
        slo(w, lb).let { "\nrequestRate=${w.requestRate}/s · 利用率 ${it.utilizationPct}% · 成功率 ${"%.1f".format(java.util.Locale.ROOT,it.successPct)}% · 延迟 ${it.latencyMs}ms" }
}

object Dns {
    data class Answer(val ip: String?, val chain: List<String>, val reason: String)
    fun records(zone: Resource) = list(zone.p("records"), ';').map { val p = it.split('|'); require(p.size == 4) { "DNS 记录格式 name|A|value|ttl" }; p }
    fun resolve(w: WorldState, name: String): Answer {
        var current = name.lowercase().trimEnd('.'); val chain = mutableListOf(current)
        repeat(6) {
            val zone = w.resources.values.filter { it.kind == Kind.DNS_ZONE && (current == it.p("domain") || current.endsWith("." + it.p("domain"))) }.maxByOrNull { it.p("domain").length }
                ?: return Answer(null, chain, "NXDOMAIN：没有权威 Zone")
            val label = if (current == zone.p("domain")) "@" else current.removeSuffix("." + zone.p("domain"))
            val record = records(zone).firstOrNull { it[0] == label } ?: return Answer(null, chain, "NXDOMAIN：${zone.id} 中无 $label 记录")
            if (record[1] == "A") return Answer(record[2], chain + record[2], "A ${record[2]} TTL ${record[3]}")
            current = record[2].trimEnd('.'); chain += current
        }
        return Answer(null, chain, "CNAME 链过长")
    }
    fun owner(w: WorldState, ip: String) = w.resources.values.firstOrNull { it.kind in Network.endpoints && it.p("publicIp") == ip }
}

object Tls {
    fun matches(pattern: String, host: String) = pattern == host || pattern.startsWith("*.") && host.endsWith(pattern.drop(1)) && host.count { it == '.' } == pattern.count { it == '.' }
    fun check(w: WorldState, lb: Resource, host: String): Health {
        if (!Network.listens(lb, 443)) return Health(false, "${lb.id} 没有 HTTPS 443 Listener")
        val cert = w.resources[lb.ref("certificate")] ?: return Health(false, "HTTPS Listener 未绑定证书")
        if (w.tick >= cert.p("notAfterTick").toLong()) return Health(false, "证书 ${cert.id} 已于 tick ${cert.p("notAfterTick")} 过期")
        if (!matches(cert.p("commonName"), host)) return Health(false, "证书名称 ${cert.p("commonName")} 与 Host $host 不匹配")
        return Health(true, "TLS 模拟握手成功：${cert.id} 有效至 tick ${cert.p("notAfterTick")}（未执行真实加密）")
    }
}

object Portal {
    /** End-to-end HTTPS: DNS → public LB → Listener/NSG → TLS → healthy backends. */
    fun check(w: WorldState, url: String, minBackends: Int = 1): Health {
        val match = Regex("(https?)://([a-z0-9.-]+)(:[0-9]{1,5})?(/.*)?").matchEntire(url) ?: return Health(false, "URL 无效")
        val scheme = match.groupValues[1]; val host = match.groupValues[2]
        val answer = Dns.resolve(w, host); val ip = answer.ip ?: return Health(false, "DNS：${answer.reason}")
        val lb = Dns.owner(w, ip) ?: return Health(false, "DNS 指向 $ip，但该地址没有公网端点（陈旧记录）")
        val explicitPort = match.groupValues[3].removePrefix(":")
        val port = if(explicitPort.isEmpty()) { if(scheme == "https") 443 else 80 } else explicitPort.toIntOrNull() ?: return Health(false,"端口无效")
        if(port !in 1..65535) return Health(false,"端口范围 1–65535")
        // This teaching model only implements HTTP 80 and HTTPS 443 listeners; never reinterpret other ports.
        if(port != if(scheme == "https") 443 else 80) return Health(false,"未配置 $scheme Listener $port（教学子集仅支持 HTTP 80 / HTTPS 443）")
        val path = Network.reachable(w, "internet", lb.id, port)
        if (!path.allowed) return Health(false, "${lb.id}:$port 不可达：${path.blockedAt} ${path.reason}")
        if (lb.kind != Kind.LOAD_BALANCER) return Health(scheme == "http", "直接访问实例 ${lb.id}，没有负载均衡冗余")
        if (scheme == "http") return if (lb.p("redirectHttp") == "true") Health(true, "301 → https://$host/") else Health(true, "200 明文 HTTP（未重定向）")
        Tls.check(w, lb, host).let { if (!it.healthy) return it }
        val healthy = Lb.health(w, lb).count { it.healthy }
        if (healthy < minBackends) return Health(false, "${lb.id} 健康后端 $healthy < $minBackends（502/503）")
        return Health(true, "HTTPS 200 · ${answer.chain.joinToString(" → ")} → ${lb.id} · 健康后端 $healthy")
    }
}

object Storage {
    data class Rule(val prefix: String, val archive: Int?, val delete: Int?)
    fun lifecycle(bucket: Resource) = list(bucket.p("lifecycle"), ';').map { text ->
        val p = text.split('|'); require(p.size in 2..3 && p[0].isNotEmpty()) { "生命周期格式 prefix|archive:30|delete:90" }
        val actions = p.drop(1).associate { val kv = it.split(':'); require(kv.size == 2 && kv[0] in setOf("archive", "delete") && kv[1].toIntOrNull() in 1..3650) { "动作 archive:N 或 delete:N，N=1–3650" }; kv[0] to kv[1].toInt() }
        require(actions.size == p.size - 1) { "同一规则动作重复" }
        Rule(p[0], actions["archive"], actions["delete"]).also { require(it.archive == null || it.delete == null || it.delete > it.archive) { "delete 天数必须大于 archive" } }
    }
    /** Applies lifecycle rules at the current tick (1 tick = 1 simulated day for object age). */
    fun apply(w: WorldState): Pair<WorldState, List<String>> {
        val notes = mutableListOf<String>(); var resources = w.resources
        w.resources.values.filter { it.kind == Kind.OBJECT }.forEach { o ->
            val bucket = w.resources[o.ref("bucket")] ?: return@forEach
            val age = w.tick - o.p("createdTick").toLong()
            val rules = lifecycle(bucket).filter { o.p("name").startsWith(it.prefix) }
            when {
                rules.any { it.delete != null && age >= it.delete } -> { resources = resources - o.id; notes += "生命周期删除 ${o.p("name")}（age=$age）" }
                o.p("tier") == "STANDARD" && rules.any { it.archive != null && age >= it.archive } -> { resources = resources + (o.id to o.copy(properties = o.properties + ("tier" to "ARCHIVE"))); notes += "生命周期归档 ${o.p("name")}" }
            }
        }
        return w.copy(resources = resources) to notes
    }
    fun restricted(w: WorldState, bucketId: String, principal: String): Health {
        val bucket = w.resources[bucketId] ?: return Health(false, "Bucket 不存在")
        if (bucket.p("visibility") != "private") return Health(false, "匿名读取 ALLOW：Bucket 为 public")
        if (!Iam.permits(w, principal, "read", Kind.BUCKET.name, bucket.compartment, bucket.id)) return Health(false, "$principal 读取 DENY：缺少 read BUCKET 授权")
        return Health(true, "匿名 DENY · $principal ALLOW")
    }
}

object Db {
    fun target(w: WorldState, app: Resource) = w.resources[app.p("dbEndpoint")]?.takeIf { it.kind == Kind.DATABASE }
    fun connected(w: WorldState, appId: String): Health {
        val app = w.resources[appId] ?: return Health(false, "应用实例不存在")
        val db = target(w, app) ?: return Health(false, "应用未配置 dbEndpoint")
        val r = Network.reachable(w, app.id, db.id, db.p("port").toIntOrNull() ?: 1521)
        if (!r.allowed) return Health(false, "${r.blockedAt}: ${r.reason}")
        if (app.p("dbSecret") != db.p("adminSecret")) return Health(false, "ORA-01017 模拟：凭证引用 ${app.p("dbSecret").ifEmpty { "(空)" }} 与数据库不一致")
        return Health(true, "${app.id} → ${db.id}:${db.p("port")} 连接成功，records=${db.p("records")}")
    }
    fun latestBackupPoint(w: WorldState, dbId: String) = w.resources.values.filter { it.kind == Kind.DB_BACKUP && it.ref("database") == dbId }
        .maxOfOrNull { it.p("pointInTime").ifEmpty { it.p("createdTick") }.toLong() }
    fun latestBackupTick(w: WorldState, dbId: String) = w.resources.values.filter { it.kind == Kind.DB_BACKUP && it.ref("database") == dbId }.maxOfOrNull { it.p("createdTick").toLong() }
}

object Cost {
    fun of(r: Resource): Double = when (r.kind) {
        Kind.COMPUTE -> if (r.p("status") == "STOPPED") 0.0 else (Catalog.shapes[r.p("shape")] ?: Catalog.defaultShape).credits
        Kind.LOAD_BALANCER -> 15.0
        Kind.DATABASE -> 40.0
        Kind.VOLUME -> (r.p("sizeGb").toDoubleOrNull() ?: 0.0) * 0.04
        Kind.OBJECT -> (r.p("sizeMb").toDoubleOrNull() ?: 0.0) / 1000 * if (r.p("tier") == "ARCHIVE") 0.2 else 2.0
        else -> 0.0
    }
    /** Simulated monthly Credits; teaching unit, not an Oracle price list. */
    fun total(w: WorldState) = w.resources.values.sumOf(::of)
    fun within(w: WorldState) = total(w) <= w.budgetCredits
}
