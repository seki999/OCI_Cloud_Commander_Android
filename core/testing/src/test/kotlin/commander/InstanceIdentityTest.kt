package commander

import org.junit.Assert.*
import org.junit.Test

class InstanceIdentityTest {
    @Test fun instancePrincipalCannotBecomeHumanGroupMember() {
        val l=Fixtures.levels[4]; val w=l.initialWorld
        val result=Simulator(l).dispatch(w,GameAction("create",resource=Resource("human-group",Kind.GROUP,properties=mapOf("members" to "workload"))))
        assertFalse(result.accepted); assertEquals(w,result.world)
    }
    @Test fun matchingPolicyDoesNotTurnMissingOrWronglyScopedResourceIntoSuccess() {
        val w=Fixtures.solved(3); val goal=Fixtures.levels[2].objectives.first { it.predicate == "iam.permits" }
        assertTrue(Predicates.evaluate(w,goal).passed)
        assertFalse(Predicates.evaluate(w.copy(resources=w.resources-"sample"),goal).passed)
        val sample=w.resources.getValue("sample"); val wrong=w.copy(resources=w.resources+("sample" to sample.copy(compartment="prod")))
        assertFalse(Predicates.evaluate(wrong,goal).passed)
    }
}
