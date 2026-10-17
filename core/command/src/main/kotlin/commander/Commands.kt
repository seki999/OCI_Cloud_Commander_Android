package commander

/** Explicit teaching whitelist: no subprocess, sockets, host filesystem or arbitrary shell. */
object Commands {
    val examples = listOf(
        "oci iam compartment list", "oci compute instance list", "oci network vcn list", "curl --from web https://internet:443", "curl --from web http://app:8080",
        "ssh operator@web", "ssh --bastion bastion operator@app", "oci bv volume list", "df -h app", "oci os object list", "oci db database list",
        "oci lb backend-health lb", "advance 5", "dig www.starport.example", "curl https://www.starport.example/", "oci iam policy list"
    )
    /** [report] renders a read-only view of the current world; it never mutates state. */
    data class Parsed(val action: GameAction? = null, val queryKind: Kind? = null, val report: ((WorldState) -> String)? = null, val error: String? = null)
    private val lists = mapOf(
        "oci iam compartment list" to Kind.COMPARTMENT, "oci iam group list" to Kind.GROUP, "oci iam policy list" to Kind.POLICY,
        "oci compute instance list" to Kind.COMPUTE, "oci network vcn list" to Kind.VCN, "oci network subnet list" to Kind.SUBNET,
        "oci bastion bastion list" to Kind.BASTION, "oci bastion session list" to Kind.BASTION_SESSION,
        "oci bv volume list" to Kind.VOLUME, "oci bv backup list" to Kind.VOLUME_BACKUP,
        "oci os bucket list" to Kind.BUCKET, "oci os object list" to Kind.OBJECT,
        "oci db database list" to Kind.DATABASE, "oci db backup list" to Kind.DB_BACKUP,
        "oci lb load-balancer list" to Kind.LOAD_BALANCER, "oci dns zone list" to Kind.DNS_ZONE, "oci certs certificate list" to Kind.CERTIFICATE
    )
    private val id = Regex("[a-z][a-z0-9-]{0,39}")
    fun parse(input: String): Parsed {
        val s=input.trim(); if(s.length > 4096 || s.any { it in ";&|`$<>\n\r" }) return Parsed(error="命令不在教学白名单中")
        val t=s.split(Regex("\\s+"))
        lists[s]?.let { return Parsed(queryKind=it) }
        if(t.size == 4 && t[0] == "curl" && t[1] == "--from") {
            val match=Regex("https?://([a-z][a-z0-9-]*):([0-9]{1,5})/?").matchEntire(t[3]) ?: return Parsed(error="示例 curl --from web https://internet:443")
            val port=match.groupValues[2].toInt(); if(port !in 1..65535) return Parsed(error="端口范围 1–65535")
            return Parsed(action=GameAction("probe",patch=mapOf("source" to t[2],"destination" to match.groupValues[1],"port" to port.toString())))
        }
        if(t.size == 2 && t[0] == "curl") {
            if(!t[1].matches(Regex("https?://[a-z0-9-]+(\\.[a-z0-9-]+)+(:[0-9]{1,5})?/?"))) return Parsed(error="示例 curl https://www.starport.example/")
            return Parsed(action=GameAction("http",value=t[1].trimEnd('/')+"/"))
        }
        if(t.size == 2 && t[0] in setOf("dig","nslookup")) {
            if(!t[1].matches(Validation.fqdn)) return Parsed(error="需要小写 FQDN，如 www.starport.example")
            return Parsed(action=GameAction("dig",value=t[1]))
        }
        if(t[0] == "ssh") {
            val via=if(t.size == 4 && t[1] == "--bastion" && t[2].matches(id)) t[2] else if(t.size == 2) "direct" else return Parsed(error="示例 ssh operator@web 或 ssh --bastion bastion operator@app")
            val target=Regex("([a-z]+)@([a-z][a-z0-9-]{0,39})").matchEntire(t.last()) ?: return Parsed(error="格式 client@instance，client 为 operator / intruder")
            if(target.groupValues[1] !in Catalog.clients) return Parsed(error="未知模拟客户端；可用 ${Catalog.clients.keys}")
            return Parsed(action=GameAction("ssh",patch=mapOf("client" to target.groupValues[1],"target" to target.groupValues[2],"via" to via)))
        }
        if(t.size == 3 && t[0] == "df" && t[1] == "-h" && t[2].matches(id)) return Parsed(report={ w ->
            val c=w.resources[t[2]]; if(c?.kind != Kind.COMPUTE) "SIMULATED: 实例不存在" else "SIMULATED df -h ${c.id}\n"+Disk.check(w,c).reason+"\n"+
                w.resources.values.filter { it.kind == Kind.VOLUME && it.ref("attachedTo") == c.id }.joinToString("\n") { "${it.id}\t${it.p("sizeGb")}G\t${it.p("mountPoint").ifEmpty { "(未挂载)" }}" }
        })
        if(t.size == 4 && t.take(3) == listOf("oci","lb","backend-health") && t[3].matches(id)) return Parsed(report={ w ->
            val lb=w.resources[t[3]]; if(lb?.kind != Kind.LOAD_BALANCER) "SIMULATED: Load Balancer 不存在" else "SIMULATED backend-health ${lb.id}\n"+Lb.report(w,lb)
        })
        if(t.size == 2 && t[0] == "advance") {
            val n=t[1].toIntOrNull(); if(n == null || n !in 1..100) return Parsed(error="advance 1–100（推进模拟时间）")
            return Parsed(action=GameAction("advance_tick",ticks=n))
        }
        if(t.size == 3 && t[0] == "inspect") return Parsed(action=GameAction("inspect",id=t[2],actor=t[1]))
        return Parsed(error="暂不支持的教学命令。请使用白名单示例；本终端不会执行真实命令。")
    }
    fun query(w: WorldState, kind: Kind): String = "SIMULATED\n"+w.resources.values.filter { it.kind == kind && (kind in setOf(Kind.COMPARTMENT,Kind.GROUP,Kind.POLICY) || it.region == w.region) }.joinToString("\n") { "${it.id}\t${it.kind}\t${it.compartment}\t${it.properties}" }.ifEmpty { "[]" }
}
