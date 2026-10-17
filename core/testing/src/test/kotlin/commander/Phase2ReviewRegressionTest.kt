package commander

import org.junit.Assert.*
import org.junit.Test

class Phase2ReviewRegressionTest {
    private fun level(n: Int) = Fixtures.levels.first { it.order == n }
    private fun withProperty(w: WorldState,id: String,key: String,value: String): WorldState {
        val r=w.resources.getValue(id)
        return w.copy(resources=w.resources+(id to r.copy(properties=r.properties+(key to value))))
    }
    @Test fun loadBalancerMustHaveAnEgressRuleToReachBackends() {
        val w=Fixtures.solved(28)
        val denied=withProperty(w,"lb-nsg","rules","ingress|0.0.0.0/0|tcp|80|stateful;ingress|0.0.0.0/0|tcp|443|stateful")
        assertEquals("lb.egress",Network.reachable(denied,"lb","web1",8080).blockedAt)
        assertFalse(Lb.health(denied,denied.resources.getValue("lb")).any { it.healthy })
        assertFalse(Portal.check(denied,"https://www.starport.example/").healthy)
        assertEquals(0,Predicates.score(denied,level(28)).stars)
    }
    @Test fun httpsProbeUsesExplicitPortInsteadOfSilentlyUsing443() {
        val w=Fixtures.solved(28)
        assertTrue(Portal.check(w,"https://www.starport.example:443/").healthy)
        assertFalse(Portal.check(w,"https://www.starport.example:8443/").healthy)
        assertFalse(Portal.check(w,"https://www.starport.example:0/").healthy)
        assertFalse(Portal.check(w,"https://www.starport.example:65536/").healthy)
    }
    @Test fun phaseTwoComputeCannotStartWithMissingShapeImageOrKey() {
        val l=level(13); val original=Fixtures.solution(l).golden.first().resource!!
        listOf("shape","image","sshKey").forEach { key ->
            val r=Simulator(l).dispatch(l.initialWorld,GameAction("create",resource=original.copy(properties=original.properties-key)))
            assertFalse("missing $key was accepted",r.accepted); assertEquals(l.initialWorld,r.world)
        }
    }
    @Test fun clearingShapeCannotBuyUndeclaredDefaultCapacity() {
        val l=level(29); val w=Fixtures.solved(29)
        val result=Simulator(l).dispatch(w,GameAction("update",id="web3",patch=mapOf("shape" to "")))
        assertFalse(result.accepted); assertEquals(w,result.world)
    }
    @Test fun backupOfRestoredDatabaseMustPreserveItsActualRecoveryPoint() {
        val l=level(23); val w=Fixtures.solved(23); val sim=Simulator(l)
        val backup=Resource("restore-backup",Kind.DB_BACKUP,properties=mapOf("type" to "FULL"),references=mapOf("database" to "orders-restore"))
        val result=sim.dispatch(w,GameAction("create",resource=backup)); assertTrue(result.message,result.accepted)
        assertEquals("70",result.world.resources.getValue("restore-backup").p("pointInTime"))
        val restored=w.resources.getValue("orders-restore")
        val copy=restored.copy(id="second-restore",properties=restored.properties.filterKeys { it in Validation.fields.getValue(Kind.DATABASE) }+("privateIp" to "10.10.3.22"),references=restored.references+("sourceBackup" to "restore-backup"))
        val second=sim.dispatch(result.world,GameAction("create",resource=copy)); assertTrue(second.message,second.accepted)
        assertEquals("70",second.world.resources.getValue("second-restore").p("pointInTime"))
    }
    @Test fun httpRedirectGoalFailsWhenPort80IngressIsBlocked() {
        val l=level(28); val w=Fixtures.solved(28)
        val broken=withProperty(w,"lb-nsg","rules","ingress|0.0.0.0/0|tcp|443|stateful;egress|10.10.1.0/24|tcp|8080|stateful")
        val goal=l.objectives.first { it.predicate == "lb.httpRedirect" }
        assertFalse(Predicates.evaluate(broken,goal).passed)
        assertEquals(0,Predicates.score(broken,l).stars)
    }
    @Test fun bucketPrivacyDefaultBelongsToEngineRatherThanOnlyTheForm() {
        val l=level(19); val result=Simulator(l).dispatch(l.initialWorld,GameAction("create",resource=Resource("empty-bucket",Kind.BUCKET)))
        assertTrue(result.message,result.accepted)
        assertEquals("private",result.world.resources.getValue("empty-bucket").p("visibility"))
    }
    @Test fun trainingDatabaseMustUseAPrivateSubnet() {
        val l=level(22); val original=Fixtures.solution(l).golden.first().resource!!
        val publicDb=original.copy(properties=original.properties+("privateIp" to "10.10.1.20"),references=original.references+("subnet" to "web-sub"))
        val result=Simulator(l).dispatch(l.initialWorld,GameAction("create",resource=publicDb))
        assertFalse(result.accepted); assertEquals(l.initialWorld,result.world)
    }

    @Test fun wrongLifecycleThresholdCannotPassJustBecauseTheSampleAgesHappenToFit() {
        val l=level(21)
        val wrong=Fixtures.solution(l).golden.map { a -> if(a.type == "update" && a.id == "logs-bkt") a.copy(patch=mapOf("lifecycle" to "logs/|archive:20|delete:90")) else a }
        val w=Fixtures.run(l,wrong)
        assertEquals(0,Predicates.score(w,l).stars)
    }
    @Test fun terminalPreservesExplicitHttpsPortForEngineValidation() {
        val parsed=Commands.parse("curl https://www.starport.example:8443/")
        assertNull(parsed.error); assertEquals("https://www.starport.example:8443/",parsed.action?.value)
    }

    @Test fun eventReplayDoesNotDependOnDeviceLocale() {
        val previous=java.util.Locale.getDefault()
        try {
            val l=level(28); val w=Fixtures.solved(28)
            java.util.Locale.setDefault(java.util.Locale.US)
            val a=Simulator(l).dispatch(w,GameAction("inspect",id="lb"))
            java.util.Locale.setDefault(java.util.Locale.FRANCE)
            val b=Simulator(l).dispatch(w,GameAction("inspect",id="lb"))
            assertEquals(a.world,b.world)
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            ContentLoader.validate(l,Fixtures.strings)
        } finally { java.util.Locale.setDefault(previous) }
    }

    @Test fun freshlyRepackingOldRestoredDataDoesNotSatisfyBackupFreshness() {
        val l=level(23); val w=Fixtures.solved(23).copy(tick=200)
        val result=Simulator(l).dispatch(w,GameAction("create",resource=Resource("old-data-new-backup",Kind.DB_BACKUP,properties=mapOf("type" to "FULL"),references=mapOf("database" to "orders-restore"))))
        assertTrue(result.message,result.accepted)
        val fresh=Objective("fresh","db.backupFresh",mapOf("id" to "orders-restore","maxAge" to "24","minRetention" to "7"),"test")
        assertFalse(Predicates.evaluate(result.world,fresh).passed)
    }

}
