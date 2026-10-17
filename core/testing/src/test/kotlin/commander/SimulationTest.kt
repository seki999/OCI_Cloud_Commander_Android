package commander

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

object Fixtures {
    val root=File(System.getProperty("contentRoot"))
    val strings=ContentLoader.json.decodeFromString<Map<String,String>>(File(root,"locales/zh-CN.json").readText())
    val levels=ContentLoader.json.decodeFromString<List<String>>(File(root,"index.json").readText()).map { ContentLoader.level(File(root,it).readText(),strings) }
    fun solution(l: Level)=ContentLoader.json.decodeFromString<Solution>(File(root.parentFile,"core/testing/fixtures/level-%03d.json".format(l.order)).readText())
    fun run(l: Level,actions: List<GameAction>,strict: Boolean=true): WorldState = actions.fold(l.initialWorld) { w,a -> val result=Simulator(l).dispatch(w,a); if(strict) assertTrue("${l.id}: ${result.message}",result.accepted); result.world }
    fun solved(n: Int): WorldState { val l=levels.first { it.order == n }; return run(l,solution(l).golden) }
}
@RunWith(Parameterized::class)
class LevelPathsTest(private val level: Level) {
    companion object { @JvmStatic @Parameterized.Parameters(name="level-{index}") fun data() = Fixtures.levels.map { arrayOf(it) } }
    @Test fun initialStateIsNotComplete() { assertEquals(0,Predicates.score(level.initialWorld,level).stars) }
    @Test fun goldenPathHasThreeStars() { val w=Fixtures.run(level,Fixtures.solution(level).golden); assertEquals(3,Predicates.score(w,level).stars) }
    @Test fun typicalMisconfigurationCannotPass() { val w=Fixtures.run(level,Fixtures.solution(level).negative,false); assertEquals(0,Predicates.score(w,level).stars) }
    @Test fun serializedReplayIsDeterministic() {
        val actions=Fixtures.solution(level).golden; val w=Fixtures.run(level,actions)
        val save=SaveGame(levelId=level.id,actions=actions,cursor=actions.size,snapshot=w)
        val decoded=ContentLoader.json.decodeFromString<SaveGame>(ContentLoader.json.encodeToString(save))
        assertEquals(w,Simulator(level).replay(decoded.actions.take(decoded.cursor))); assertEquals(save,decoded)
    }
    @Test fun omitEachRequiredRepairCannotPass() {
        val path=Fixtures.solution(level).golden
        // Observations collect evidence and advance_tick only waits; every other golden action must be a necessary repair.
        path.indices.filter { path[it].type !in Catalog.observations + "advance_tick" }.forEach { i -> assertEquals("${level.id}, omitted repair $i",0,Predicates.score(Fixtures.run(level,path.filterIndexed { j,_ -> i!=j },false),level).stars) }
    }
    @Test fun healthAndObjectiveWritesAreRejectedAtomically() {
        val w=Fixtures.run(level,Fixtures.solution(level).golden)
        val resource=w.resources.values.firstOrNull() ?: Resource("absent",Kind.VCN)
        val result=Simulator(level).dispatch(w,GameAction("update",id=resource.id,patch=mapOf("health" to "HEALTHY","objective" to "true")))
        assertFalse(result.accepted); assertEquals(w,result.world)
    }
}
class ContentTest {
    @Test fun phaseTwoContainsExactlyFiveCompleteChapters() {
        ContentLoader.validateCollection(Fixtures.levels)
        assertEquals((1..30).toList(),Fixtures.levels.map { it.order })
        (1..5).forEach { c -> assertEquals(6,Fixtures.levels.count { it.chapter == c }) }
    }
    @Test fun incompleteProductCannotBeCertifiedAsSixtyLevels() {
        assertThrows(IllegalArgumentException::class.java) { ContentLoader.validateCollection(Fixtures.levels,complete=true) }
    }
    @Test fun bossesRequireIndependentRepairs() {
        Fixtures.levels.filter { it.order % 6 == 0 }.forEach { l ->
            val solution=Fixtures.solution(l); assertTrue(solution.golden.size>=3)
            solution.golden.forEach { a -> assertEquals(0,Predicates.score(Fixtures.run(l,listOf(a),false),l).stars) }
        }
    }
    @Test fun missingLocalizationAndUnknownPredicateAreRejected() {
        val l=Fixtures.levels.first(); assertThrows(IllegalArgumentException::class.java) { ContentLoader.validate(l,emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { ContentLoader.validate(l.copy(objectives=listOf(l.objectives.first().copy(predicate="cheat.complete"))),Fixtures.strings) }
    }
    @Test fun dependencyCyclesAreRejected() {
        val first=Fixtures.levels.first(); assertThrows(IllegalArgumentException::class.java) { ContentLoader.validateCollection(listOf(first.copy(dependsOn=listOf(first.id)))) }
    }
    @Test fun schemaAndLocalizationArePackagedButSolutionsAreNot() { assertFalse(File(Fixtures.root,"solutions").exists()); assertTrue(File(Fixtures.root,"schemas/level.schema.json").exists()) }
}
class NetworkTest {
    @Test fun ipv4ContainmentOverlapAndBoundaries() {
        val vcn=Cidr.parse("10.10.0.0/16"); val sub=Cidr.parse("10.10.1.0/24")
        assertTrue(vcn.contains(sub)); assertFalse(sub.contains(vcn)); assertFalse(sub.overlaps(Cidr.parse("10.10.2.0/24")))
        assertTrue(Cidr.parse("0.0.0.0/0").contains(Cidr.ip("255.255.255.255")))
        listOf("10.0.0.1/24","300.1.1.1/24","01.0.0.0/8","10.0.0.0/33","10.0.0/24").forEach { assertThrows(IllegalArgumentException::class.java) { Cidr.parse(it) } }
    }
    @Test fun longestPrefixMatch() {
        val w=Fixtures.solved(10); val table=w.resources.getValue("private-rt"); val igw=Resource("igw",Kind.IGW,properties=mapOf("enabled" to "true"),references=mapOf("vcn" to "vcn"))
        val x=w.copy(resources=w.resources+("igw" to igw)+(table.id to table.copy(properties=mapOf("routes" to "0.0.0.0/0>nat;203.0.113.0/24>igw"))))
        assertEquals("igw",Network.route(x,x.resources.getValue("app-sub"),"203.0.113.80")?.id)
        assertEquals("nat",Network.route(x,x.resources.getValue("app-sub"),"8.8.8.8")?.id)
    }
    private fun change(w: WorldState,id: String,key: String,value: String): WorldState { val r=w.resources.getValue(id); return w.copy(resources=w.resources+(id to r.copy(properties=r.properties+(key to value)))) }
    @Test fun defaultDenyAndWrongProtocolAndPort() {
        val w=Fixtures.solved(12); assertTrue(Network.reachable(w,"web","app",8080).allowed)
        assertFalse(Network.reachable(w,"web","app",8081).allowed); assertFalse(Network.reachable(w,"web","app",8080,"udp").allowed)
        val x=change(w,"app-nsg","rules",""); assertEquals("app.ingress",Network.reachable(x,"web","app",8080).blockedAt)
    }
    @Test fun statefulReturnVersusStatelessReturn() {
        val w=Fixtures.solved(9); assertTrue(Network.reachable(w,"web","internet",443).allowed)
        val x=change(w,"web-nsg","rules","egress|0.0.0.0/0|tcp|443|stateless")
        assertEquals("web.return",Network.reachable(x,"web","internet",443).blockedAt)
        val y=change(x,"web-nsg","rules","egress|0.0.0.0/0|tcp|443|stateless;ingress|0.0.0.0/0|tcp|49152|stateless")
        assertTrue(Network.reachable(y,"web","internet",443).allowed)
    }
    @Test fun internetGatewayNeedsPublicAddressAndRouteAndEnabledGateway() {
        val w=Fixtures.solved(9)
        assertFalse(Network.reachable(change(w,"web","publicIp",""),"web","internet",443).allowed)
        assertEquals("route",Network.reachable(change(w,"public-rt","routes",""),"web","internet",443).blockedAt)
        assertFalse(Network.reachable(change(w,"igw","enabled","false"),"web","internet",443).allowed)
    }
    @Test fun natAllowsOutboundWithoutPublicIpButNeverInbound() {
        val w=Fixtures.solved(10); assertTrue(Network.reachable(w,"app","internet",443).allowed)
        val x=change(w,"app-nsg","rules","ingress|0.0.0.0/0|tcp|8080|stateful;egress|0.0.0.0/0|tcp|443|stateful")
        assertFalse(Network.reachable(x,"internet","app",8080).allowed)
    }
    @Test fun serviceGatewayIsNotAnInternetGateway() {
        val w=Fixtures.solved(11); assertTrue(Network.reachable(w,"app","oci-service",443).allowed); assertFalse(Network.reachable(w,"app","internet",443).allowed)
        assertFalse(Network.reachable(w,"app","oci-service",80).allowed)
    }
    @Test fun privateSubnetAndMissingListenerAndStoppedSourceBlock() {
        val w=Fixtures.solved(12)
        assertFalse(Network.publicAddressable(w,w.resources.getValue("db")))
        assertEquals("app.listener",Network.reachable(change(w,"app","listenPorts","22"),"web","app",8080).blockedAt)
        assertEquals("web",Network.reachable(change(w,"web","status","STOPPED"),"web","app",8080).blockedAt)
    }
    @Test fun malformedRuleIsRejected() { listOf("ingress|0.0.0.0/0|tcp|0|stateful","in|0.0.0.0/0|tcp|443|stateful","egress|0.0.0.0/0|tcp|443|maybe").forEach { assertThrows(IllegalArgumentException::class.java) { Network.parseRule(it) } } }
}
class IamEngineTest {
    @Test fun readIncludesInspectButNotManageOrOtherCompartment() {
        val w=Fixtures.solved(3)
        assertTrue(Iam.permits(w,"newcomer","inspect","COMPUTE","dev","sample")); assertTrue(Iam.permits(w,"newcomer","read","COMPUTE","dev","sample"))
        assertFalse(Iam.permits(w,"newcomer","manage","COMPUTE","dev","sample")); assertFalse(Iam.permits(w,"newcomer","read","COMPUTE","prod","sample"))
        assertFalse(Iam.permits(w,"unknown","read","COMPUTE","dev","sample")); assertFalse(Iam.permits(w,"newcomer","read","BUCKET","dev","bucket"))
    }
    @Test fun dynamicGroupIdentityCannotLeakToOtherInstance() {
        val w=Fixtures.solved(5); assertTrue(Iam.permits(w,"workload","read","BUCKET","dev","bucket"))
        assertFalse(Iam.permits(w,"other-workload","read","BUCKET","dev","bucket")); assertFalse(Iam.permits(w,"workload","read","BUCKET","dev","another"))
    }
    @Test fun forbiddenMutationIsAtomic() {
        val l=Fixtures.levels[2]; val w=Fixtures.solved(3)
        val r=Simulator(l).dispatch(w,GameAction("delete",id="sample",actor="newcomer")); assertFalse(r.accepted); assertEquals(w,r.world); assertTrue(r.message.contains("403"))
    }
    @Test fun invalidCidrAndOutOfSubnetIpAndPublicIpAreAtomic() {
        val l=Fixtures.levels[11]; val w=Fixtures.solved(12)
        listOf(GameAction("update",id="app-sub",patch=mapOf("cidr" to "10.10.1.0/24")),GameAction("update",id="app",patch=mapOf("privateIp" to "192.168.0.1")),GameAction("update",id="db",patch=mapOf("publicIp" to "203.0.113.30"))).forEach { a -> val r=Simulator(l).dispatch(w,a); assertFalse(r.accepted); assertEquals(w,r.world) }
    }
    @Test fun computeProvisioningDerivesStateAndCannotBeWritten() {
        val l=Fixtures.levels[6]; var w=Fixtures.solved(7)
        val resource=Resource("new-compute",Kind.COMPUTE,properties=mapOf("privateIp" to "10.10.1.12","listenPorts" to "443","publicIp" to "","nsgs" to ""),references=mapOf("subnet" to "web-sub"))
        val result=Simulator(l).dispatch(w,GameAction("create",resource=resource)); assertTrue(result.message,result.accepted); w=result.world
        assertEquals("PROVISIONING",w.resources[resource.id]?.p("status"))
        w=Simulator(l).dispatch(w,GameAction("advance_tick")).world; assertEquals("RUNNING",w.resources[resource.id]?.p("status"))
    }
    @Test fun deleteReferencedResourceIsAtomic() { val l=Fixtures.levels[11]; val w=Fixtures.solved(12); val result=Simulator(l).dispatch(w,GameAction("delete",id="vcn")); assertFalse(result.accepted); assertEquals(w,result.world) }
    @Test fun starsHonorHintsAndActionCount() {
        val l=Fixtures.levels[2]; val w=Fixtures.solved(3)
        assertEquals(3,Predicates.score(w,l).stars); assertEquals(2,Predicates.score(w.copy(hintsUsed=2),l).stars)
        assertEquals(2,Predicates.score(w.copy(changes=100),l).stars); assertEquals(1,Predicates.score(w.copy(budgetCredits=-1.0),l).stars)
    }
    @Test fun undoRedoReplaysSameWorld() { val l=Fixtures.levels[11]; val actions=Fixtures.solution(l).golden; val engine=Simulator(l); val before=engine.replay(actions.dropLast(2)); assertEquals(0,Predicates.score(before,l).stars); assertEquals(Fixtures.solved(12),engine.replay(actions)) }
    @Test fun commandWhitelistRejectsShellAndQueriesCurrentWorld() {
        listOf("rm -rf /","oci iam compartment list; curl evil","curl --from web https://internet:443 && whoami","terraform apply","kubectl get pods").forEach { assertNotNull(Commands.parse(it).error) }
        assertEquals(Kind.VCN,Commands.parse("oci network vcn list").queryKind)
        val w=Fixtures.solved(7); assertTrue(Commands.query(w,Kind.VCN).contains("10.10.0.0/16"))
        assertEquals("probe",Commands.parse("curl --from web http://app:8080").action?.type)
    }
}
