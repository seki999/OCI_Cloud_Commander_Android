package commander

import org.junit.Assert.*
import org.junit.Test

class PredicateContractTest {
    @Test fun eachRegisteredPredicateHasPassingAndFailingExamples() {
        val worlds=Fixtures.levels.flatMap { l -> listOf(l.initialWorld,Fixtures.solved(l.order)) }+listOf(WorldState(),WorldState(budgetCredits=-1.0))
        val examples=Fixtures.levels.flatMap { it.objectives+it.constraints+it.optionalObjectives+it.failureTriggers }.toMutableList()
        examples += Objective("budget","cost.underBudget",messageKey="test")
        // Both reachability polarities share exactly the same strict parameter schema.
        examples += examples.filter { it.predicate == "network.reachable" }.map { it.copy(predicate="network.unreachable") }
        examples += examples.filter { it.predicate == "iam.permits" }.map { it.copy(predicate="iam.denies") }
        examples += examples.filter { it.predicate == "ssh.allowed" }.map { it.copy(predicate="ssh.denied") }
        Predicates.parameters.keys.forEach { predicate ->
            val outputs=examples.filter { it.predicate == predicate }.flatMap { o -> worlds.map { Predicates.evaluate(it,o).passed } }
            assertTrue("No passing example: $predicate",true in outputs)
            assertTrue("No failing example: $predicate",false in outputs)
        }
    }
    @Test fun parameterSchemaRejectsUnknownAndMissingKeys() {
        val w=Fixtures.solved(12)
        Predicates.parameters.forEach { (name,keys) ->
            val invalid=Objective("invalid",name,keys.associateWith { "x" }+mapOf("injected" to "true"),"test")
            assertFalse(Predicates.evaluate(w,invalid).passed)
        }
    }
    @Test fun mapMovementChangesNoNetworkOrScoringStateAndReplays() {
        val l=Fixtures.levels[11]; val w=Fixtures.solved(12); val sim=Simulator(l)
        val r=sim.dispatch(w,GameAction("move",id="app",patch=mapOf("x" to "300","y" to "180")))
        assertTrue(r.accepted); assertEquals(w.resources,r.world.resources); assertEquals(w.changes,r.world.changes)
        assertEquals(Predicates.score(w,l).stars,Predicates.score(r.world,l).stars)
        assertEquals(MapPoint(300,180),r.world.layout["app"])
    }
    @Test fun routeTableCanDetachButCannotReferenceMissingResource() {
        val l=Fixtures.levels[8]; val w=Fixtures.solved(9)
        val sim=Simulator(l); val result=sim.dispatch(w,GameAction("update",id="web-sub",references=mapOf("routeTable" to "")))
        assertTrue(result.message,result.accepted); assertFalse(Network.reachable(result.world,"web","internet",443).allowed)
        val bad=sim.dispatch(w,GameAction("update",id="web-sub",references=mapOf("routeTable" to "missing")))
        assertFalse(bad.accepted); assertEquals(w,bad.world)
    }
    @Test fun shrinkingVcnCannotInvalidateChildren() {
        val l=Fixtures.levels[11]; val w=Fixtures.solved(12); val result=Simulator(l).dispatch(w,GameAction("update",id="vcn",patch=mapOf("cidr" to "10.11.0.0/16")))
        assertFalse(result.accepted); assertEquals(w,result.world)
    }
    @Test fun deletingDynamicGroupTargetOrPolicyScopeIsRejected() {
        val l=Fixtures.levels[4]; val w=Fixtures.solved(5)
        listOf("worker","bucket","dev").forEach { id -> val result=Simulator(l).dispatch(w,GameAction("delete",id=id)); assertFalse(result.accepted); assertEquals(w,result.world) }
    }
    @Test fun statelessRuleTakesPrecedenceOverOverlappingStatefulRule() {
        val l=Fixtures.levels[8]; val w=Fixtures.solved(9)
        val action=GameAction("update",id="web-nsg",patch=mapOf("rules" to "egress|0.0.0.0/0|tcp|443|stateful;egress|0.0.0.0/0|tcp|443|stateless"))
        val result=Simulator(l).dispatch(w,action); assertTrue(result.accepted); assertFalse(Network.reachable(result.world,"web","internet",443).allowed)
    }
}
