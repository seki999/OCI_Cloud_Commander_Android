package commander

import org.junit.Assert.*
import org.junit.Test

class RelationshipTest {
    @Test fun movingAttachedNsgAcrossVcnIsRejectedAtomically() {
        val l=Fixtures.levels[11]; val original=Fixtures.solved(12); val sim=Simulator(l)
        val create=sim.dispatch(original,GameAction("create",resource=Resource("foreign-vcn",Kind.VCN,properties=mapOf("cidr" to "10.20.0.0/16"))))
        assertTrue(create.accepted)
        val result=sim.dispatch(create.world,GameAction("update",id="app-nsg",references=mapOf("vcn" to "foreign-vcn")))
        assertFalse(result.accepted); assertEquals(create.world,result.world)
    }
    @Test fun invalidProbeIsRejectedWithoutAdvancingClockOrWritingEvent() {
        val l=Fixtures.levels[11]; val w=Fixtures.solved(12)
        val result=Simulator(l).dispatch(w,GameAction("probe",patch=mapOf("source" to "web","destination" to "app","port" to "0")))
        assertFalse(result.accepted); assertEquals(w,result.world)
    }
    @Test fun publicIpCannotBeDuplicated() {
        val l=Fixtures.levels[11]; val w=Fixtures.solved(12)
        val updatedSub=Simulator(l).dispatch(w,GameAction("update",id="db-sub",patch=mapOf("public" to "true"))).world
        val result=Simulator(l).dispatch(updatedSub,GameAction("update",id="db",patch=mapOf("publicIp" to "203.0.113.10")))
        assertFalse(result.accepted); assertEquals(updatedSub,result.world)
    }
}
