package commander

/** IPv4 only; canonical CIDRs prevent ambiguous host bits and parsing overflow. */
data class Cidr(val base: Long, val prefix: Int) {
    val last: Long get() = base + (1L shl (32 - prefix)) - 1
    fun contains(ip: Long) = ip in base..last
    fun contains(other: Cidr) = prefix <= other.prefix && contains(other.base) && contains(other.last)
    fun overlaps(other: Cidr) = base <= other.last && other.base <= last
    companion object {
        fun ip(text: String): Long {
            val parts = text.split('.')
            require(parts.size == 4 && parts.all { it.matches(Regex("0|[1-9][0-9]{0,2}")) && it.toInt() <= 255 }) { "无效 IPv4: $text" }
            return parts.fold(0L) { n, s -> (n shl 8) or s.toLong() }
        }
        fun parse(text: String): Cidr {
            val parts = text.split('/'); require(parts.size == 2) { "需要 IPv4 CIDR" }
            val prefix = parts[1].toIntOrNull(); require(prefix != null && prefix in 0..32) { "CIDR prefix 范围 0–32" }
            val ip = ip(parts[0]); val mask = if (prefix == 0) 0L else (0xffffffffL shl (32 - prefix)) and 0xffffffffL
            require(ip and mask == ip) { "CIDR 必须使用网络地址" }; return Cidr(ip, prefix)
        }
    }
}

object Iam {
    private val verbs = listOf("inspect", "read", "use", "manage")
    fun permits(w: WorldState, principal: String, verb: String, kind: String, compartment: String, id: String = ""): Boolean {
        if (verb !in verbs || w.principals.none { it.id == principal }) return false
        // Commander is the supervised training author, never the identity being tested by mission predicates.
        if (principal == "commander") return true
        val instance = w.principals.find { it.id == principal }?.instanceId?.let(w.resources::get)
        val groups = w.resources.values.filter { r -> when(r.kind) {
            Kind.GROUP -> instance == null && principal in r.p("members").split(',').map(String::trim)
            Kind.DYNAMIC_GROUP -> instance != null && r.p("matchInstance") == instance.id && r.p("matchCompartment") == instance.compartment
            else -> false
        }}.map { it.id }
        return w.resources.values.any { p -> p.kind == Kind.POLICY && p.ref("group") in groups &&
            verbs.indexOf(p.p("verb")) >= verbs.indexOf(verb) &&
            (p.p("resourceType") == kind || p.p("resourceType") == "all-resources") &&
            (p.p("scope") == compartment || p.p("scope") == "tenancy") &&
            (p.p("targetId").isEmpty() || p.p("targetId") == id)
        }
    }
    fun leastPrivilege(w: WorldState) = w.resources.values.filter { it.kind == Kind.POLICY }.none {
        it.p("scope") == "tenancy" || it.p("resourceType") == "all-resources" || it.p("verb") == "manage"
    }
}

object Network {
    /** Resources with a VNIC-like private address in a Subnet. */
    val endpoints = setOf(Kind.COMPUTE, Kind.DATABASE, Kind.LOAD_BALANCER, Kind.BASTION)
    /** Bastion has a dedicated allow-list/session model; LB traffic still requires NSG egress. */
    private val managedSources = setOf(Kind.BASTION)
    private fun deny(at: String, reason: String, path: List<String>) = Reachability(false, path, at, reason)
    private fun subnet(w: WorldState, r: Resource) = w.resources[r.ref("subnet")]
    private fun rules(w: WorldState, r: Resource): List<Rule> = r.p("nsgs").split(',').flatMap { id ->
        w.resources[id]?.p("rules").orEmpty().split(';').filter(String::isNotBlank).map(::parseRule)
    }
    data class Rule(val direction: String, val cidr: Cidr, val protocol: String, val port: Int, val stateful: Boolean)
    fun parseRule(s: String): Rule {
        val p = s.split('|'); require(p.size == 5) { "NSG rule: ingress|CIDR|tcp|443|stateful" }
        require(p[0] in setOf("ingress", "egress") && p[2] in setOf("tcp", "udp", "icmp", "all")) { "NSG 方向/协议无效" }
        require(p[3].toIntOrNull() in 1..65535 && p[4] in setOf("stateful", "stateless")) { "端口 1–65535，stateful/stateless" }
        return Rule(p[0], Cidr.parse(p[1]), p[2], p[3].toInt(), p[4] == "stateful")
    }
    private fun matching(w: WorldState, r: Resource, direction: String, ip: Long, protocol: String, port: Int) =
        rules(w,r).filter { it.direction == direction && it.cidr.contains(ip) && (it.protocol == protocol || it.protocol == "all") && it.port == port }
    fun publicAddressable(w: WorldState, r: Resource) = r.p("publicIp").isNotEmpty() && subnet(w,r)?.p("public") == "true"
    fun running(r: Resource) = when(r.kind) { Kind.COMPUTE -> r.p("status") == "RUNNING"; Kind.DATABASE -> r.p("status") == "AVAILABLE"; else -> true }
    fun listens(r: Resource, port: Int) = when(r.kind) {
        Kind.DATABASE -> r.p("port") == port.toString()
        Kind.COMPUTE, Kind.LOAD_BALANCER -> port.toString() in r.p("listenPorts").split(',').map(String::trim)
        else -> false
    }
    fun route(w: WorldState, sub: Resource, target: String): Resource? {
        val table = w.resources[sub.ref("routeTable")] ?: return null
        val routes = table.p("routes").split(';').filter(String::isNotBlank).map { val p=it.split('>'); p[0] to p[1] }
        val gatewayId = if (target == "oci-service") routes.firstOrNull { it.first == "oci-services" }?.second
        else routes.filter { it.first != "oci-services" && Cidr.parse(it.first).contains(Cidr.ip(target)) }.maxByOrNull { Cidr.parse(it.first).prefix }?.second
        return w.resources[gatewayId]
    }
    /** [sourceIp] overrides the simulated Internet client address (for allow-list teaching). */
    fun reachable(w: WorldState, source: String, destination: String, port: Int, protocol: String = "tcp", sourceIp: String? = null): Reachability {
        val src = w.resources[source]; val dst = w.resources[destination]
        val path = mutableListOf(source)
        if(port !in 1..65535 || protocol !in setOf("tcp","udp","icmp")) return deny("input","无效端口或协议",path)
        if (source != "internet" && src?.kind !in endpoints) return deny(source,"源端点不存在",path)
        if (destination !in setOf("internet", "oci-service") && (dst?.kind !in endpoints || dst?.kind == Kind.BASTION)) return deny(destination,"目标不存在",path)
        if (src != null && !running(src)) return deny(source,"源未 RUNNING/AVAILABLE",path)
        if (dst != null && !running(dst)) return deny(destination,"目标未 RUNNING/AVAILABLE",path)
        if (src != null && src.region != w.region || dst != null && dst.region != w.region) return deny("region","Region 不匹配",path)
        val srcSub = src?.let { subnet(w,it) }; val dstSub = dst?.let { subnet(w,it) }
        if (src != null && srcSub == null || dst != null && dstSub == null) return deny("subnet","缺少 Subnet",path)
        val srcIp = src?.p("privateIp")?.let(Cidr::ip) ?: Cidr.ip(sourceIp ?: "198.51.100.10")
        val dstIp = dst?.p("privateIp")?.let(Cidr::ip) ?: Cidr.ip(if(destination == "oci-service") "192.0.2.80" else "203.0.113.80")
        if (src != null && dst != null) {
            if (srcSub!!.ref("vcn") != dstSub!!.ref("vcn")) return deny("vcn","不支持跨 VCN；缺少 Peering",path)
            path += srcSub.id; path += dstSub.id
        } else {
            val sub = srcSub ?: dstSub!!
            val gateway = route(w, sub, if(destination == "oci-service") "oci-service" else "203.0.113.80") ?: return deny("route", "没有匹配的 Route",path)
            path += sub.id; path += gateway.id
            if (gateway.ref("vcn") != sub.ref("vcn") || gateway.region != sub.region || gateway.p("enabled") != "true") return deny(gateway.id,"Gateway 未启用或 VCN/Region 不匹配",path)
            if (source == "internet") {
                if (gateway.kind != Kind.IGW || !publicAddressable(w,dst!!)) return deny("publicIp", "公网入站需 IGW、公有 Subnet 和 Public IP；NAT 不接收入站",path)
            } else if (destination == "oci-service") {
                if (gateway.kind != Kind.SERVICE_GATEWAY || gateway.p("service") != "oci-services") return deny(gateway.id,"目标需要受支持的 Service Gateway",path)
            } else if (gateway.kind == Kind.IGW) {
                if (!publicAddressable(w,src!!)) return deny("publicIp","IGW 出站需 Public IP 和公有 Subnet",path)
            } else if (gateway.kind != Kind.NAT) return deny(gateway.id,"Internet 出站需 NAT 或 IGW",path)
        }
        if (src != null && src.kind !in managedSources) {
            val matches = matching(w,src,"egress",dstIp,protocol,port)
            if(matches.isEmpty()) return deny("${src.id}.egress","NSG 默认拒绝出站 $protocol/$port",path)
            if(matches.any { !it.stateful } && matching(w,src,"ingress",dstIp,protocol,49152).isEmpty()) return deny("${src.id}.return","Stateless 需要显式回程 ephemeral port 49152",path)
        }
        if (dst != null) {
            val matches = matching(w,dst,"ingress",srcIp,protocol,port)
            if(matches.isEmpty()) return deny("${dst.id}.ingress","NSG 默认拒绝入站 $protocol/$port",path)
            if(matches.any { !it.stateful } && matching(w,dst,"egress",srcIp,protocol,49152).isEmpty()) return deny("${dst.id}.return","Stateless 缺少回程规则",path)
            if(!listens(dst,port)) return deny("${dst.id}.listener","目标未监听 $port",path)
            // sshd keeps answering when the application log disk is full; application ports degrade.
            if(dst.kind == Kind.COMPUTE && port != 22) Disk.check(w,dst).let { if(!it.healthy) return deny("${dst.id}.service","服务退化：${it.reason}",path) }
        }
        path += destination
        return Reachability(true,path,reason="链路、Route、NSG、监听检查通过")
    }
}
