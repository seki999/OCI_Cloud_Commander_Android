package commander

/** Pure transaction boundary. All derived state is written here, never by UI or level scripts. */
class Simulator(val level: Level) {
    /** Rule-owned properties assigned when a resource is created; data copies come from the referenced source snapshot. */
    private fun derive(w: WorldState, r: Resource): Resource {
        val extra: Map<String,String> = when(r.kind) {
            Kind.COMPUTE -> mapOf("status" to "PROVISIONING", "readyTick" to (w.tick+2).toString(), "rootGb" to "50")
            Kind.DATABASE -> mapOf("status" to "PROVISIONING", "readyTick" to (w.tick+5).toString()) + (w.resources[r.ref("sourceBackup")]?.let {
                mapOf("checksum" to it.p("checksum"), "records" to it.p("records"), "pointInTime" to it.p("pointInTime").ifEmpty { it.p("createdTick") })
            } ?: mapOf("checksum" to "empty", "records" to "0"))
            Kind.VOLUME -> w.resources[r.ref("sourceBackup")]?.let { mapOf("checksum" to it.p("checksum"), "dataVersion" to it.p("dataVersion")) } ?: mapOf("checksum" to "empty", "dataVersion" to "v0")
            Kind.VOLUME_BACKUP -> {
                val v=w.resources.getValue(r.ref("volume"))
                mapOf("checksum" to v.p("checksum"), "dataVersion" to v.p("dataVersion"), "sizeGb" to v.p("sizeGb"), "createdTick" to w.tick.toString())
            }
            Kind.DB_BACKUP -> {
                val db=w.resources.getValue(r.ref("database")); require(db.p("status") == "AVAILABLE") { "数据库未 AVAILABLE，无法备份" }
                mapOf("checksum" to db.p("checksum"), "records" to db.p("records"), "createdTick" to w.tick.toString(), "pointInTime" to db.p("pointInTime").ifEmpty { w.tick.toString() })
            }
            Kind.OBJECT -> mapOf("tier" to "STANDARD", "createdTick" to w.tick.toString())
            Kind.CERTIFICATE -> mapOf("issuedTick" to w.tick.toString(), "notAfterTick" to (w.tick+r.p("validTicks").toLong()).toString())
            Kind.BASTION_SESSION -> mapOf("expiresTick" to (w.tick+r.p("ttlTicks").toLong()).toString())
            else -> emptyMap()
        }
        return r.copy(properties=r.properties+extra)
    }
    private fun validateComputeConfiguration(r: Resource) {
        // Phase 1 authored endpoints predate full Compute provisioning. New Phase 2 instances must be usable.
        if(level.order >= 13 && r.kind == Kind.COMPUTE) {
            require(r.p("shape") in Catalog.shapes) { "Compute 必须选择有效 Shape" }
            require(r.p("image") in Catalog.images) { "Compute 必须选择有效 Image" }
            require(r.p("sshKey").isNotBlank()) { "Compute 必须绑定模拟 SSH key 别名" }
        }
    }
    private fun writable(kind: Kind, keys: Set<String>) = keys.all { it in Validation.fields.getValue(kind) }
    fun dispatch(w: WorldState, a: GameAction): DispatchResult = try {
        require(a.type in level.allowedActions) { "该关卡未开放操作 ${a.type}" }
        require(w.principals.any { it.id == a.actor }) { "未知身份" }
        var changed = false
        var detail = ""
        var next = when(a.type) {
            "select_region" -> { require(a.actor == "commander"); require(a.value in Catalog.regions) { "未知 Region" }; changed=true; w.copy(region=a.value) }
            "study_card" -> { require(a.id in Catalog.cards) { "未知概念卡" }; w.copy(studiedCards=w.studiedCards+a.id) }
            "move" -> {
                require(a.actor == "commander" && a.id in w.resources) { "地图节点不存在或无权限" }
                val x=a.patch.getValue("x").toInt(); val y=a.patch.getValue("y").toInt()
                require(x in -10000..10000 && y in -10000..10000) { "地图位置超出范围" }
                w.copy(layout=w.layout+(a.id to MapPoint(x,y)))
            }
            "hint" -> w.copy(hintsUsed=w.hintsUsed+1)
            "inspect" -> {
                if(a.id == "root") {
                    require(a.actor == "commander") { "403 NotAuthorizedOrNotFound" }
                    detail="Tenancy=starport-training; Region=${w.region}; resources=${w.resources.size}; 非官方本地教学租户"
                } else {
                    val r=w.resources[a.id] ?: error("资源不存在")
                    require(Iam.permits(w,a.actor,"inspect",r.kind.name,r.compartment,r.id)) { "403 NotAuthorizedOrNotFound" }
                    detail="${r.id} ${r.kind} ${r.properties} ${r.references}"+when(r.kind) {
                        Kind.COMPUTE -> " · disk: ${Disk.check(w,r).reason}"
                        Kind.LOAD_BALANCER -> "\n"+Lb.report(w,r)
                        else -> ""
                    }
                }
                w.copy(evidence=w.evidence+a.id)
            }
            "create" -> {
                val input=a.resource ?: error("缺少 Resource")
                val r=if(input.kind == Kind.BUCKET && "visibility" !in input.properties) input.copy(properties=input.properties+("visibility" to "private")) else input
                require(!w.resources.containsKey(r.id)) { "资源 ID 已存在" }
                require(Iam.permits(w,a.actor,"manage",r.kind.name,r.compartment,r.id)) { "403 NotAuthorizedOrNotFound" }
                require(writable(r.kind,r.properties.keys)) { "不可写属性（含派生状态）" }
                Validation.resource(w,r); validateComputeConfiguration(r); changed=true
                w.copy(resources=w.resources+(r.id to derive(w,r)))
            }
            "update" -> {
                val old=w.resources[a.id] ?: error("资源不存在")
                require(Iam.permits(w,a.actor,"manage",old.kind.name,old.compartment,old.id)) { "403 NotAuthorizedOrNotFound" }
                require(a.patch.isNotEmpty() || a.references.isNotEmpty()) { "没有配置变更" }
                require(writable(old.kind,a.patch.keys-"compartment")) { "不可写属性（status/health 属于派生状态）" }
                require(old.kind !in Validation.immutable || a.patch.all { (k,v) -> k == "compartment" && v == old.compartment || old.properties[k] == v } && a.references.all { (k,v) -> old.references[k].orEmpty() == v }) { "${old.kind} 创建后不可修改；请新建资源替换" }
                require(a.references.all { (k,v) -> k !in Validation.fixedRefs || old.references[k].orEmpty() == v }) { "恢复来源/会话目标等关联在创建后固定" }
                val compartment=a.patch["compartment"] ?: old.compartment
                require(Iam.permits(w,a.actor,"manage",old.kind.name,compartment,old.id)) { "403 目标 Compartment 权限不足" }
                val updated=old.copy(compartment=compartment, properties=old.properties+(a.patch-"compartment"), references=(old.references-a.references.filterValues { it.isEmpty() }.keys)+a.references.filterValues { it.isNotEmpty() })
                Validation.resource(w,updated); validateComputeConfiguration(updated)
                val candidate=w.copy(resources=w.resources+(a.id to updated))
                candidate.resources.values.filter { it.id != updated.id && Validation.mentions(it,updated.id) }.forEach { Validation.resource(candidate,it) }
                changed=true; detail="${a.id} properties=${a.patch} references=${a.references}"; candidate
            }
            "delete" -> {
                val old=w.resources[a.id] ?: error("资源不存在")
                require(Iam.permits(w,a.actor,"manage",old.kind.name,old.compartment,old.id)) { "403 NotAuthorizedOrNotFound" }
                require(w.resources.values.none { it.id != a.id && Validation.mentions(it,a.id) }) { "资源仍被引用" }
                require(old.kind != Kind.VOLUME || old.ref("attachedTo").isEmpty()) { "先分离（清空 attachedTo）再删除卷" }
                changed=true; w.copy(resources=w.resources-a.id,layout=w.layout-a.id)
            }
            "power" -> {
                val old=w.resources[a.id] ?: error("资源不存在"); require(old.kind == Kind.COMPUTE) { "仅 Compute 支持电源操作" }
                require(Iam.permits(w,a.actor,"use",old.kind.name,old.compartment,old.id)) { "403 NotAuthorizedOrNotFound" }
                val status=when(a.value) {
                    "START" -> { require(old.p("status") == "STOPPED") { "实例当前 ${old.p("status")}，无法 START" }; "RUNNING" }
                    "STOP" -> { require(old.p("status") == "RUNNING") { "实例当前 ${old.p("status")}，无法 STOP" }; "STOPPED" }
                    else -> error("电源操作 START / STOP")
                }
                changed=true; detail="${a.id} ${old.p("status")} → $status"
                w.copy(resources=w.resources+(a.id to old.copy(properties=old.properties+("status" to status))))
            }
            "advance_tick" -> { require(a.ticks in 1..100) { "ticks 范围 1–100" }; w.copy(tick=w.tick+a.ticks-1) }
            "probe" -> {
                val p=a.patch
                require(p.keys == setOf("source","destination","port") || p.keys == setOf("source","destination","port","protocol")) { "Probe 参数不匹配" }
                require(p.getValue("port").toIntOrNull() in 1..65535 && (p["protocol"] ?: "tcp") in setOf("tcp","udp","icmp")) { "Probe 端口或协议无效" }
                val result=Network.reachable(w,p.getValue("source"),p.getValue("destination"),p.getValue("port").toInt(),p["protocol"] ?: "tcp")
                detail=if(result.allowed) "PASS ${result.path.joinToString(" → ")}" else "connection timeout: blockedAt=${result.blockedAt}; ${result.reason}"
                w.copy(evidence=w.evidence+"probe:${p["source"]}:${p["destination"]}:${p["port"]}")
            }
            "ssh" -> {
                val p=a.patch; require(p.keys == setOf("client","target","via")) { "SSH 参数 client/target/via" }
                val r=Ssh.check(w,p.getValue("client"),p.getValue("target"),p.getValue("via"))
                detail=if(r.allowed) "SSH PASS ${r.path.joinToString(" → ")} · ${r.reason}" else "ssh: blockedAt=${r.blockedAt}; ${r.reason}"
                w.copy(evidence=w.evidence+"ssh:${p["client"]}:${p["target"]}:${p["via"]}")
            }
            "dig" -> {
                require(a.value.matches(Validation.fqdn)) { "dig 需要小写 FQDN" }
                val answer=Dns.resolve(w,a.value); detail="${answer.chain.joinToString(" → ")} · ${answer.reason}"
                w.copy(evidence=w.evidence+"dig:${a.value}")
            }
            "http" -> {
                val r=Portal.check(w,a.value); detail="${if(r.healthy) "PASS" else "FAIL"} ${a.value} · ${r.reason}"
                w.copy(evidence=w.evidence+"http:${a.value}")
            }
            else -> error("未知 Action")
        }
        next=next.copy(tick=next.tick+1,changes=next.changes+if(changed) 1 else 0)
        next=next.copy(resources=next.resources.mapValues { (_,r) -> when {
            r.kind == Kind.COMPUTE && r.p("status") == "PROVISIONING" && next.tick >= r.p("readyTick").toLong() -> r.copy(properties=r.properties+("status" to "RUNNING"))
            r.kind == Kind.DATABASE && r.p("status") == "PROVISIONING" && next.tick >= r.p("readyTick").toLong() -> r.copy(properties=r.properties+("status" to "AVAILABLE"))
            else -> r
        } })
        // Lifecycle is a daily batch job: it runs only when simulated time is explicitly advanced.
        val (lifecycled,notes)=if(a.type == "advance_tick") Storage.apply(next) else next to emptyList(); next=lifecycled
        if(notes.isNotEmpty()) detail=(detail.ifBlank { "${a.id.ifBlank { a.resource?.id ?: a.value }} 配置已提交" })+" · "+notes.joinToString("; ")
        val event=SimEvent(next.events.size+1,next.tick,a.actor,a.type,detail.ifBlank { "${a.id.ifBlank { a.resource?.id ?: a.value }} 配置已提交" })
        DispatchResult(next.copy(events=next.events+event),true,"SIMULATED: ${event.detail}")
    } catch(e: Exception) { DispatchResult(w,false,"SIMULATED: ${e.message ?: "操作无效"}") }
    fun replay(actions: List<GameAction>): WorldState = actions.fold(level.initialWorld) { w,a -> val r=dispatch(w,a); require(r.accepted) { r.message }; r.world }
}

object Predicates {
    val parameters = mapOf(
        "world.region" to setOf("value"), "concept.studied" to setOf("card"),
        "resource.exists" to setOf("id","kind"), "resource.absent" to setOf("id"), "resource.propertyEquals" to setOf("id","key","value"),
        "resource.compartmentEquals" to setOf("id","compartment"),
        "iam.permits" to setOf("principal","verb","kind","compartment","id"), "iam.denies" to setOf("principal","verb","kind","compartment","id"),
        "iam.leastPrivilege" to emptySet(), "iam.hasBroadPermissions" to emptySet(), "logging.evidenceFound" to setOf("id"), "resource.referenceEquals" to setOf("id","key","value"), "network.reachable" to setOf("source","destination","port"),
        "network.unreachable" to setOf("source","destination","port"), "network.nonOverlapping" to emptySet(),
        "network.publicAddressable" to setOf("id","value"), "network.subnetContained" to setOf("id","vcn"),
        "security.privateInstances" to setOf("ids"), "cost.underBudget" to emptySet(),
        "compute.running" to setOf("id"), "compute.diskHealthy" to setOf("id"),
        "ssh.allowed" to setOf("client","target","via"), "ssh.denied" to setOf("client","target","via"),
        "storage.protected" to setOf("id"), "storage.backupRestorable" to setOf("source","restored"),
        "storage.objectRestricted" to setOf("bucket","principal"), "storage.lifecycleConfigured" to setOf("bucket","prefix","archiveDays","deleteDays"), "storage.objectTier" to setOf("id","tier"),
        "db.connected" to setOf("app"), "db.dataChecksumEquals" to setOf("app","checksum"), "db.rpoWithin" to setOf("app","source","maxTicks"),
        "db.backupFresh" to setOf("id","maxAge","minRetention"),
        "lb.backendsHealthy" to setOf("id","min"), "slo.meetsTarget" to setOf("id","minSuccessPct","maxLatencyMs"),
        "dns.resolves" to setOf("name","target"), "tls.validHostname" to setOf("id","host"), "lb.httpRedirect" to setOf("id"),
        "portal.endToEnd" to setOf("url","minBackends")
    )
    private fun health(h: Health, reasonOut: (String) -> Unit): Boolean { reasonOut(h.reason); return h.healthy }
    fun evaluate(w: WorldState, o: Objective): Check {
        val p=o.params
        return try {
            require(parameters[o.predicate] == p.keys) { "未知 Predicate 或参数不匹配" }
            var reason="配置尚未满足 ${o.id}"
            val setReason: (String) -> Unit = { reason=it }
            val passed=when(o.predicate) {
                "world.region" -> w.region == p["value"]
                "concept.studied" -> p["card"] in w.studiedCards
                "resource.exists" -> w.resources[p["id"]]?.kind?.name == p["kind"]
                "resource.absent" -> p["id"] !in w.resources
                "resource.propertyEquals" -> w.resources[p["id"]]?.p(p.getValue("key")) == p["value"]
                "resource.compartmentEquals" -> w.resources[p["id"]]?.compartment == p["compartment"]
                "iam.permits", "iam.denies" -> {
                    val allow=Iam.permits(w,p.getValue("principal"),p.getValue("verb"),p.getValue("kind"),p.getValue("compartment"),p.getValue("id"))
                    reason="IAM ${if(allow) "ALLOW" else "DENY"}: ${p["principal"]} ${p["verb"]} ${p["compartment"]}/${p["id"]}"
                    if(o.predicate == "iam.permits") allow && w.resources[p["id"]]?.let { it.kind.name == p["kind"] && it.compartment == p["compartment"] } == true else !allow
                }
                "iam.leastPrivilege" -> Iam.leastPrivilege(w)
                "iam.hasBroadPermissions" -> !Iam.leastPrivilege(w)
                "logging.evidenceFound" -> p["id"] in w.evidence
                "resource.referenceEquals" -> w.resources[p["id"]]?.references?.get(p["key"]) == p["value"]
                "network.reachable", "network.unreachable" -> {
                    val result=Network.reachable(w,p.getValue("source"),p.getValue("destination"),p.getValue("port").toInt())
                    reason=if(result.allowed) result.path.joinToString(" → ") else "${result.blockedAt}: ${result.reason}"
                    result.allowed == (o.predicate == "network.reachable")
                }
                "network.nonOverlapping" -> w.resources.values.filter { it.kind == Kind.SUBNET }.let { subs -> subs.all { a -> subs.none { b -> a.id != b.id && a.ref("vcn") == b.ref("vcn") && Cidr.parse(a.p("cidr")).overlaps(Cidr.parse(b.p("cidr"))) } } }
                "network.subnetContained" -> {
                    val sub=w.resources[p["id"]]; val vcn=w.resources[p["vcn"]]
                    sub?.kind == Kind.SUBNET && vcn?.kind == Kind.VCN && sub.ref("vcn") == vcn.id && Cidr.parse(vcn.p("cidr")).contains(Cidr.parse(sub.p("cidr")))
                }
                "network.publicAddressable" -> w.resources[p["id"]]?.let { Network.publicAddressable(w,it) == p.getValue("value").toBooleanStrict() } ?: false
                "security.privateInstances" -> p.getValue("ids").split(',').all { id -> w.resources[id]?.let { it.p("publicIp").isEmpty() && w.resources[it.ref("subnet")]?.p("public") == "false" } ?: false }
                "cost.underBudget" -> { reason="模拟成本 ${"%.1f".format(java.util.Locale.ROOT,Cost.total(w))} / 预算 ${w.budgetCredits} Credits"; Cost.within(w) }
                "compute.running" -> w.resources[p["id"]]?.let { c ->
                    reason="${c.id}: ${c.p("status")} shape=${c.p("shape").ifEmpty { "-" }} image=${c.p("image").ifEmpty { "-" }}"
                    c.kind == Kind.COMPUTE && c.p("status") == "RUNNING" && c.p("shape") in Catalog.shapes && c.p("image") in Catalog.images && c.p("sshKey").isNotEmpty()
                } ?: false
                "compute.diskHealthy" -> w.resources[p["id"]]?.takeIf { it.kind == Kind.COMPUTE }?.let { health(Disk.check(w,it),setReason) } ?: false
                "ssh.allowed", "ssh.denied" -> {
                    val r=Ssh.check(w,p.getValue("client"),p.getValue("target"),p.getValue("via"))
                    reason=if(r.allowed) r.path.joinToString(" → ") else "${r.blockedAt}: ${r.reason}"
                    r.allowed == (o.predicate == "ssh.allowed")
                }
                "storage.protected" -> w.resources[p["id"]]?.let { v ->
                    v.kind == Kind.VOLUME && v.p("backupPolicy") != "none" && w.resources.values.any { it.kind == Kind.VOLUME_BACKUP && it.ref("volume") == v.id }
                } ?: false
                "storage.backupRestorable" -> {
                    val source=w.resources[p["source"]]; val restored=w.resources[p["restored"]]; val backup=restored?.let { w.resources[it.ref("sourceBackup")] }
                    reason="恢复卷 ${restored?.p("checksum") ?: "-"}/${restored?.p("dataVersion") ?: "-"} 对比源 ${source?.p("checksum") ?: "-"}/${source?.p("dataVersion") ?: "-"}"
                    source?.kind == Kind.VOLUME && restored?.kind == Kind.VOLUME && backup?.ref("volume") == source.id &&
                        restored.p("checksum") == source.p("checksum") && restored.p("dataVersion") == source.p("dataVersion") &&
                        (restored.ref("attachedTo").isEmpty() || restored.ref("attachedTo") != source.ref("attachedTo"))
                }
                "storage.lifecycleConfigured" -> w.resources[p["bucket"]]?.takeIf { it.kind == Kind.BUCKET }?.let { bucket ->
                    val prefix=p.getValue("prefix"); val archive=p.getValue("archiveDays").toInt(); val delete=p.getValue("deleteDays").toInt()
                    val rules=Storage.lifecycle(bucket)
                    reason="${bucket.id} 生命周期需覆盖 $prefix：archive=$archive / delete=$delete 天，且无冲突规则"
                    rules.any { it.prefix == prefix } && rules.filter { it.prefix.startsWith(prefix) || prefix.startsWith(it.prefix) }.all { it.archive == archive && it.delete == delete }
                } ?: false
                "storage.objectRestricted" -> health(Storage.restricted(w,p.getValue("bucket"),p.getValue("principal")),setReason)
                "storage.objectTier" -> w.resources[p["id"]]?.let { it.kind == Kind.OBJECT && it.p("tier") == p["tier"] } ?: false
                "db.connected" -> health(Db.connected(w,p.getValue("app")),setReason)
                "db.dataChecksumEquals" -> w.resources[p["app"]]?.let { Db.target(w,it) }?.let { db -> reason="${db.id} checksum=${db.p("checksum")} records=${db.p("records")}"; db.p("checksum") == p["checksum"] } ?: false
                "db.rpoWithin" -> {
                    val db=w.resources[p["app"]]?.let { Db.target(w,it) }; val incident=w.resources[p["source"]]?.p("incidentTick")?.toLongOrNull(); val point=db?.p("pointInTime")?.toLongOrNull()
                    reason="恢复点 ${point ?: "-"} / 事故 ${incident ?: "-"}，RPO 上限 ${p["maxTicks"]} tick"
                    incident != null && point != null && point <= incident && incident-point <= p.getValue("maxTicks").toLong()
                }
                "db.backupFresh" -> w.resources[p["id"]]?.let { db ->
                    val latest=Db.latestBackupPoint(w,db.id); reason="autoBackup=${db.p("autoBackup")} retention=${db.p("retentionDays")}d 最新备份 tick=${latest ?: "-"}"
                    db.kind == Kind.DATABASE && db.p("autoBackup") == "true" && db.p("retentionDays").toInt() >= p.getValue("minRetention").toInt() && latest != null && w.tick-latest in 0..p.getValue("maxAge").toLong()
                } ?: false
                "lb.backendsHealthy" -> w.resources[p["id"]]?.takeIf { it.kind == Kind.LOAD_BALANCER }?.let { lb ->
                    val h=Lb.health(w,lb); reason=h.joinToString("; ") { "${it.id}:${if(it.healthy) "OK" else it.reason}" }.ifEmpty { "无后端" }
                    h.size >= p.getValue("min").toInt() && h.all { it.healthy }
                } ?: false
                "slo.meetsTarget" -> w.resources[p["id"]]?.takeIf { it.kind == Kind.LOAD_BALANCER }?.let { lb ->
                    val s=Lb.slo(w,lb); reason="成功率 ${"%.1f".format(java.util.Locale.ROOT,s.successPct)}% · 延迟 ${s.latencyMs}ms · 利用率 ${s.utilizationPct}%"
                    s.successPct >= p.getValue("minSuccessPct").toDouble() && s.latencyMs in 1..p.getValue("maxLatencyMs").toInt()
                } ?: false
                "dns.resolves" -> {
                    val answer=Dns.resolve(w,p.getValue("name")); val target=w.resources[p["target"]]
                    reason=answer.chain.joinToString(" → ")+" · "+answer.reason
                    target != null && target.p("publicIp").isNotEmpty() && answer.ip == target.p("publicIp")
                }
                "tls.validHostname" -> w.resources[p["id"]]?.takeIf { it.kind == Kind.LOAD_BALANCER }?.let { health(Tls.check(w,it,p.getValue("host")),setReason) } ?: false
                "lb.httpRedirect" -> w.resources[p["id"]]?.let { it.kind == Kind.LOAD_BALANCER && it.p("redirectHttp") == "true" && Network.listens(it,80) && Network.listens(it,443) && Network.reachable(w,"internet",it.id,80).allowed } ?: false
                "portal.endToEnd" -> health(Portal.check(w,p.getValue("url"),p.getValue("minBackends").toInt()),setReason)
                else -> false
            }
            Check(passed,if(passed) "满足：$reason" else reason)
        } catch(e: Exception) { Check(false,e.message ?: "Predicate 参数无效") }
    }
    fun score(w: WorldState, l: Level): Score {
        val required=l.objectives.map { it to evaluate(w,it) }; val constraints=l.constraints.map { it to evaluate(w,it) }
        val stars=if(required.any { !it.second.passed } || l.failureTriggers.any { evaluate(w,it).passed }) 0
            else if(constraints.any { !it.second.passed } || !Cost.within(w)) 1
            else if(l.optionalObjectives.any { !evaluate(w,it).passed } || w.hintsUsed > 1 || w.changes > l.optimalActionLimit) 2 else 3
        return Score(stars,required,constraints)
    }
}
