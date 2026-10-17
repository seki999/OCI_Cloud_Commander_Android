package commander

object Validation {
    /** Writable configuration fields per Kind. Derived state is listed separately and is never writable. */
    val fields = mapOf(
        Kind.COMPARTMENT to setOf("name", "parent"), Kind.GROUP to setOf("members"),
        Kind.DYNAMIC_GROUP to setOf("matchInstance", "matchCompartment"),
        Kind.POLICY to setOf("verb", "resourceType", "scope", "targetId"), Kind.VCN to setOf("cidr"),
        Kind.SUBNET to setOf("cidr", "public"), Kind.IGW to setOf("enabled"), Kind.NAT to setOf("enabled"),
        Kind.SERVICE_GATEWAY to setOf("enabled", "service"), Kind.ROUTE_TABLE to setOf("routes"), Kind.NSG to setOf("rules"),
        Kind.COMPUTE to setOf("privateIp", "publicIp", "nsgs", "listenPorts", "sshKey", "shape", "image", "logPath", "httpPaths", "dbEndpoint", "dbSecret"),
        Kind.BUCKET to setOf("visibility", "lifecycle"),
        Kind.BASTION to setOf("privateIp", "allowedCidr"), Kind.BASTION_SESSION to setOf("port", "sshKey", "ttlTicks"),
        Kind.VOLUME to setOf("sizeGb", "mountPoint", "backupPolicy"), Kind.VOLUME_BACKUP to setOf("type"),
        Kind.OBJECT to setOf("name", "sizeMb"),
        Kind.DATABASE to setOf("privateIp", "nsgs", "port", "adminSecret", "autoBackup", "retentionDays"), Kind.DB_BACKUP to setOf("type"),
        Kind.LOAD_BALANCER to setOf("privateIp", "publicIp", "nsgs", "listenPorts", "backends", "healthPath", "healthTimeoutMs", "redirectHttp"),
        Kind.DNS_ZONE to setOf("domain", "records"), Kind.CERTIFICATE to setOf("commonName", "validTicks")
    )
    /** Rule-owned state: allowed in authored initial worlds, rejected from every Action. */
    val derived = mapOf(
        Kind.COMPUTE to setOf("status", "readyTick", "rootGb", "logGb"), Kind.BASTION_SESSION to setOf("expiresTick"),
        Kind.VOLUME to setOf("checksum", "dataVersion"), Kind.VOLUME_BACKUP to setOf("checksum", "dataVersion", "sizeGb", "createdTick"),
        Kind.OBJECT to setOf("tier", "createdTick"),
        Kind.DATABASE to setOf("status", "readyTick", "checksum", "records", "pointInTime", "incidentTick"),
        Kind.DB_BACKUP to setOf("checksum", "records", "createdTick", "pointInTime"), Kind.CERTIFICATE to setOf("issuedTick", "notAfterTick")
    )
    val refs = mapOf(
        Kind.SUBNET to setOf("vcn", "routeTable"), Kind.IGW to setOf("vcn"), Kind.NAT to setOf("vcn"), Kind.SERVICE_GATEWAY to setOf("vcn"),
        Kind.ROUTE_TABLE to setOf("vcn"), Kind.NSG to setOf("vcn"), Kind.COMPUTE to setOf("subnet"), Kind.POLICY to setOf("group"),
        Kind.BASTION to setOf("subnet"), Kind.BASTION_SESSION to setOf("bastion", "target"), Kind.VOLUME to setOf("attachedTo", "sourceBackup"),
        Kind.VOLUME_BACKUP to setOf("volume"), Kind.OBJECT to setOf("bucket"), Kind.DATABASE to setOf("subnet", "sourceBackup"),
        Kind.DB_BACKUP to setOf("database"), Kind.LOAD_BALANCER to setOf("subnet", "certificate")
    )
    fun refKinds(owner: Kind, key: String): Set<Kind> = when(key) {
        "vcn" -> setOf(Kind.VCN); "subnet" -> setOf(Kind.SUBNET); "routeTable" -> setOf(Kind.ROUTE_TABLE); "group" -> setOf(Kind.GROUP, Kind.DYNAMIC_GROUP)
        "bastion" -> setOf(Kind.BASTION); "target", "attachedTo" -> setOf(Kind.COMPUTE); "volume" -> setOf(Kind.VOLUME); "bucket" -> setOf(Kind.BUCKET)
        "database" -> setOf(Kind.DATABASE); "certificate" -> setOf(Kind.CERTIFICATE)
        "sourceBackup" -> if(owner == Kind.DATABASE) setOf(Kind.DB_BACKUP) else setOf(Kind.VOLUME_BACKUP)
        else -> emptySet()
    }
    /** Snapshot-like resources are immutable after creation; replace them instead. */
    val immutable = setOf(Kind.VOLUME_BACKUP, Kind.DB_BACKUP, Kind.CERTIFICATE, Kind.OBJECT, Kind.BASTION_SESSION)
    /** References fixed at creation (restore source, backup source, session endpoints). */
    val fixedRefs = setOf("sourceBackup", "volume", "database", "bastion", "target", "bucket")
    val defaults = mapOf("public" to "false", "enabled" to "true", "visibility" to "private", "parent" to "root", "backupPolicy" to "none", "type" to "FULL",
        "autoBackup" to "false", "redirectHttp" to "false", "healthPath" to "/health", "healthTimeoutMs" to "3000", "port" to "1521", "ttlTicks" to "60", "validTicks" to "90")
    private val alias = Regex("[a-z][a-z0-9-]{0,39}")
    private val path = Regex("(/[a-z0-9._-]+)+")
    val fqdn = Regex("([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")
    private fun list(text: String, separator: Char = ',') = text.split(separator).map(String::trim).filter(String::isNotEmpty)
    private fun int(r: Resource, key: String, range: IntRange, label: String = key): Int {
        val value = r.p(key).toIntOrNull(); require(value != null && value in range) { "$label 范围 ${range.first}–${range.last}" }; return value
    }
    fun secretRef(value: String) = value.matches(Regex("vault:[a-z][a-z0-9-]{0,39}"))
    /** Whether [r] names [id] through a reference or a typed id list (used for delete protection and cascade validation). */
    fun mentions(r: Resource, id: String) = id in r.references.values || id in list(r.p("nsgs")) ||
        list(r.p("routes"), ';').any { it.substringAfter('>') == id } || r.p("dbEndpoint") == id ||
        (r.kind == Kind.LOAD_BALANCER && list(r.p("backends")).any { it.substringBefore(':') == id }) ||
        r.compartment == id || r.p("scope") == id || r.p("matchInstance") == id || r.p("matchCompartment") == id || r.p("targetId") == id
    private fun endpoint(w: WorldState, r: Resource) {
        val sub=w.resources[r.ref("subnet")] ?: error("缺少 Subnet")
        val ip=Cidr.ip(r.p("privateIp")); val range=Cidr.parse(sub.p("cidr")); require(range.contains(ip) && ip > range.base+1 && ip < range.last) { "Private IP 不在 Subnet 可分配范围" }
        require(r.p("publicIp").isEmpty() || sub.p("public") == "true") { "Private Subnet 禁止 Public IP" }
        if(r.p("publicIp").isNotEmpty()) {
            Cidr.ip(r.p("publicIp"))
            require(w.resources.values.none { it.id != r.id && it.kind in Network.endpoints && it.region == r.region && it.p("publicIp") == r.p("publicIp") }) { "Public IP 已分配" }
        }
        require(w.resources.values.none { it.id != r.id && it.kind in Network.endpoints && it.ref("subnet") == sub.id && it.p("privateIp") == r.p("privateIp") }) { "IP 已分配" }
        list(r.p("nsgs")).forEach { require(w.resources[it]?.kind == Kind.NSG && w.resources[it]?.ref("vcn") == sub.ref("vcn")) { "NSG 必须属于端点 VCN" } }
        list(r.p("listenPorts")).forEach { require(it.toIntOrNull() in 1..65535) { "端口范围 1–65535" } }
    }
    fun resource(w: WorldState, r: Resource) {
        require(r.id.matches(Regex("[a-z][a-z0-9-]{0,39}"))) { "资源 ID 需为短英文 ID" }
        require(r.region in Catalog.regions) { "未知 Region" }
        require(r.compartment == "root" || w.resources[r.compartment]?.kind == Kind.COMPARTMENT) { "Compartment 不存在" }
        require(r.references.keys.all { it in refs[r.kind].orEmpty() }) { "未知关联字段" }
        r.references.forEach { (key,id) ->
            val linked = w.resources[id]; require(linked?.kind in refKinds(r.kind,key)) { "$key 引用 $id 不存在或类型错误" }
            require(linked?.region == r.region || key == "group") { "$key Region 不一致" }
        }
        when(r.kind) {
            Kind.COMPARTMENT -> { require(r.p("name") == r.id && r.p("parent") == "root") { "初版 Compartment 位于 root，name 与 ID 一致" } }
            Kind.GROUP -> require(r.p("members").split(',').all { member -> member.isBlank() || w.principals.any { it.id == member.trim() && it.instanceId == null } }) { "Group member 不存在" }
            Kind.DYNAMIC_GROUP -> require(w.resources[r.p("matchInstance")]?.kind == Kind.COMPUTE && w.resources[r.p("matchCompartment")]?.kind == Kind.COMPARTMENT) { "Dynamic Group 匹配目标不存在" }
            Kind.POLICY -> {
                require(r.ref("group").isNotBlank()) { "Policy 必须关联 Group" }
                require(r.p("verb") in setOf("inspect","read","use","manage")) { "未知 IAM verb" }
                require(r.p("resourceType") in Kind.entries.map { it.name } + "all-resources") { "未知 resourceType" }
                require(r.p("scope") == "tenancy" || w.resources[r.p("scope")]?.kind == Kind.COMPARTMENT) { "未知 Policy scope" }
                require(r.p("targetId").isEmpty() || w.resources.containsKey(r.p("targetId"))) { "Policy targetId 不存在" }
            }
            Kind.VCN -> {
                val range=Cidr.parse(r.p("cidr")); require(range.prefix in 16..30) { "教学 VCN prefix 16–30" }
                require(w.resources.values.filter { it.kind == Kind.SUBNET && it.ref("vcn") == r.id }.all { range.contains(Cidr.parse(it.p("cidr"))) }) { "VCN CIDR 不能排除现有 Subnet" }
            }
            Kind.SUBNET -> {
                val parent = w.resources[r.ref("vcn")] ?: error("缺少 VCN")
                val cidr = Cidr.parse(r.p("cidr")); require(Cidr.parse(parent.p("cidr")).contains(cidr)) { "Subnet 必须包含于 VCN" }
                require(w.resources.values.none { it.id != r.id && it.kind == Kind.SUBNET && it.ref("vcn") == parent.id && Cidr.parse(it.p("cidr")).overlaps(cidr) }) { "Subnet CIDR 重叠" }
                require(r.p("public") in setOf("true","false")) { "public 必须为 true/false" }
                r.references["routeTable"]?.let { require(w.resources[it]?.ref("vcn") == parent.id) { "Route Table 不属于该 VCN" } }
                require(w.resources.values.none { it.kind in Network.endpoints && it.ref("subnet") == r.id && (!cidr.contains(Cidr.ip(it.p("privateIp"))) || r.p("public") == "false" && it.p("publicIp").isNotEmpty()) }) { "先迁移 VNIC IP 或移除 Public IP" }
            }
            Kind.IGW, Kind.NAT, Kind.SERVICE_GATEWAY -> { require(r.ref("vcn").isNotEmpty()); require(r.p("enabled") in setOf("true","false")); if(r.kind == Kind.SERVICE_GATEWAY) require(r.p("service") == "oci-services") }
            Kind.ROUTE_TABLE -> {
                require(r.ref("vcn").isNotEmpty()); val routes=r.p("routes").split(';').filter(String::isNotBlank)
                routes.forEach { s ->
                    val p=s.split('>'); require(p.size == 2) { "Route 格式 CIDR>gateway-id" }; if(p[0] != "oci-services") Cidr.parse(p[0])
                    val gw=w.resources[p[1]]; require(gw?.kind in setOf(Kind.IGW,Kind.NAT,Kind.SERVICE_GATEWAY) && gw?.ref("vcn") == r.ref("vcn") && gw?.region == r.region) { "Route Gateway 不存在或 VCN/Region 不匹配" }
                    require(p[0] != "oci-services" || gw?.kind == Kind.SERVICE_GATEWAY) { "服务路由需要 Service Gateway" }
                }
                require(routes.map { it.substringBefore('>') }.distinct().size == routes.size) { "重复 Route 目标" }
            }
            Kind.NSG -> { require(r.ref("vcn").isNotBlank()); r.p("rules").split(';').filter(String::isNotBlank).forEach(Network::parseRule) }
            Kind.COMPUTE -> {
                endpoint(w,r)
                require(r.p("shape").isEmpty() || r.p("shape") in Catalog.shapes) { "未知教学 Shape" }
                require(r.p("image").isEmpty() || r.p("image") in Catalog.images) { "未知教学 Image" }
                require(r.p("sshKey").isEmpty() || r.p("sshKey").matches(alias)) { "sshKey 只接受模拟别名（如 ops-key），禁止粘贴真实密钥" }
                require(r.p("logPath").isEmpty() || r.p("logPath").matches(path)) { "logPath 需为绝对路径，如 /data/logs" }
                require(list(r.p("httpPaths")).all { it.matches(Regex("/[a-z0-9._/-]*")) }) { "httpPaths 以 / 开头，逗号分隔" }
                require(r.p("dbEndpoint").isEmpty() || w.resources[r.p("dbEndpoint")]?.kind == Kind.DATABASE) { "dbEndpoint 必须是 DATABASE" }
                require(r.p("dbSecret").isEmpty() || secretRef(r.p("dbSecret"))) { "dbSecret 只接受 vault:<name> 引用；本游戏不接受明文密码" }
            }
            Kind.BUCKET -> { require(r.p("visibility") in setOf("private", "public")) { "visibility: private/public" }; Storage.lifecycle(r) }
            Kind.BASTION -> { endpoint(w,r.copy(properties=r.properties+("publicIp" to ""))); Cidr.parse(r.p("allowedCidr")) }
            Kind.BASTION_SESSION -> {
                val bastion=w.resources[r.ref("bastion")] ?: error("缺少 Bastion"); val target=w.resources[r.ref("target")] ?: error("缺少目标实例")
                require(w.resources[bastion.ref("subnet")]?.ref("vcn") == w.resources[target.ref("subnet")]?.ref("vcn")) { "Bastion 与目标实例必须在同一 VCN" }
                int(r,"port",1..65535); int(r,"ttlTicks",1..180); require(r.p("sshKey").matches(alias)) { "sshKey 只接受模拟别名" }
            }
            Kind.VOLUME -> {
                int(r,"sizeGb",50..32768,"Block Volume sizeGb")
                require(r.p("backupPolicy") in setOf("none","bronze","silver","gold")) { "backupPolicy: none/bronze/silver/gold" }
                val mount=r.p("mountPoint")
                require(mount.isEmpty() || mount.matches(path) && mount !in setOf("/boot","/usr","/etc","/bin","/proc")) { "mountPoint 需为非系统绝对路径，如 /data" }
                require(mount.isEmpty() || r.ref("attachedTo").isEmpty() || w.resources.values.none { it.id != r.id && it.kind == Kind.VOLUME && it.ref("attachedTo") == r.ref("attachedTo") && it.p("mountPoint") == mount }) { "挂载点已被同实例其他卷占用" }
                w.resources[r.ref("sourceBackup")]?.let { require(r.p("sizeGb").toInt() >= it.p("sizeGb").toInt()) { "恢复卷容量不能小于备份 ${it.p("sizeGb")}GB" } }
            }
            Kind.VOLUME_BACKUP -> { require(r.ref("volume").isNotEmpty()) { "缺少源卷" }; require(r.p("type") in setOf("FULL","INCREMENTAL")) { "type: FULL/INCREMENTAL" } }
            Kind.OBJECT -> {
                require(r.ref("bucket").isNotEmpty()) { "缺少 Bucket" }; require(r.p("name").matches(Regex("[a-z0-9][a-z0-9._/-]{0,99}"))) { "对象名需小写，如 logs/app.log" }
                int(r,"sizeMb",1..1_000_000)
                require(w.resources.values.none { it.id != r.id && it.kind == Kind.OBJECT && it.ref("bucket") == r.ref("bucket") && it.p("name") == r.p("name") }) { "同名对象已存在" }
            }
            Kind.DATABASE -> {
                require(w.resources[r.ref("subnet")]?.p("public") == "false") { "训练模式 Database 必须使用 Private Subnet" }
                endpoint(w,r.copy(properties=r.properties+("publicIp" to "")))
                int(r,"port",1..65535); int(r,"retentionDays",1..60)
                require(secretRef(r.p("adminSecret"))) { "adminSecret 只接受 vault:<name> 引用；本游戏不接受明文密码" }
                require(r.p("autoBackup") in setOf("true","false")) { "autoBackup: true/false" }
            }
            Kind.DB_BACKUP -> { require(r.ref("database").isNotEmpty()) { "缺少源数据库" }; require(r.p("type") in setOf("FULL","INCREMENTAL")) { "type: FULL/INCREMENTAL" } }
            Kind.LOAD_BALANCER -> {
                endpoint(w,r)
                val vcn=w.resources[r.ref("subnet")]?.ref("vcn")
                val backends=Lb.backends(r); require(backends.map { it.id }.distinct().size == backends.size) { "后端重复" }
                backends.forEach { b -> val c=w.resources[b.id]; require(c?.kind == Kind.COMPUTE && w.resources[c.ref("subnet")]?.ref("vcn") == vcn) { "后端 ${b.id} 必须是同 VCN 的 Compute" } }
                require(r.p("healthPath").matches(Regex("/[a-z0-9._/-]*"))) { "healthPath 以 / 开头" }
                int(r,"healthTimeoutMs",50..10000)
                require(r.p("redirectHttp") in setOf("true","false")) { "redirectHttp: true/false" }
                val ports=list(r.p("listenPorts")); require(ports.isNotEmpty()) { "至少一个 Listener 端口" }
                require(r.p("redirectHttp") == "false" || "80" in ports && "443" in ports) { "HTTP→HTTPS 重定向需要 80 与 443 Listener" }
                require("443" !in ports || r.ref("certificate").isNotEmpty()) { "HTTPS Listener 需要证书" }
            }
            Kind.DNS_ZONE -> {
                require(r.p("domain").matches(fqdn)) { "domain 需为小写 FQDN，如 starport.example" }
                require(w.resources.values.none { it.id != r.id && it.kind == Kind.DNS_ZONE && it.p("domain") == r.p("domain") }) { "Zone 已存在" }
                val records=Dns.records(r)
                require(records.map { it[0] to it[1] }.distinct().size == records.size && records.groupBy { it[0] }.values.none { g -> g.size > 1 && g.any { it[1] == "CNAME" } }) { "记录重复或 CNAME 与其他记录同名" }
                records.forEach { (name,type,value,ttl) ->
                    require(name == "@" || name.matches(Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?"))) { "记录名需为 @ 或标签" }
                    require(type in setOf("A","CNAME")) { "教学子集支持 A / CNAME" }
                    if(type == "A") Cidr.ip(value) else require(value.trimEnd('.').matches(fqdn)) { "CNAME 目标需为 FQDN" }
                    require(ttl.toIntOrNull() in 30..86400) { "TTL 30–86400" }
                }
            }
            Kind.CERTIFICATE -> { require(r.p("commonName").removePrefix("*.").matches(fqdn)) { "commonName 需为 FQDN 或 *.domain" }; int(r,"validTicks",1..397) }
        }
    }
}
