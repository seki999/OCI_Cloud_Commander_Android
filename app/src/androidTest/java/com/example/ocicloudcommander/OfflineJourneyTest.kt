package com.example.ocicloudcommander

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class OfflineJourneyTest {
    @get:Rule val ui=createEmptyComposeRule()
    @Test fun firstLevelThroughRealControlsThenRecreate() {
        var scenario=ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: GameViewModel
        scenario.onActivity { vm=ViewModelProvider(it)[GameViewModel::class.java] }
        ui.waitUntil(15000) { vm.state.value.ready && vm.state.value.level != null }
        ui.runOnIdle { vm.start(vm.state.value.levels.first()) }
        ui.waitUntil { vm.state.value.world?.changes == 0 }
        ui.onNodeWithText("继续游戏").performClick()
        ui.onNodeWithText("Console",useUnmergedTree=true).performClick()
        ui.onNodeWithText("Region: 未选择").performClick()
        ui.onNodeWithText("ap-tokyo-1",useUnmergedTree=true).performClick()
        ui.waitUntil { vm.state.value.world?.region == "ap-tokyo-1" }
        ui.onNodeWithText("Mission",useUnmergedTree=true).performClick()
        listOf("Tenancy","Region","AD","FD").forEach { card -> ui.onNodeWithText("○ $card：",substring=true).performScrollTo().performClick() }
        // Optional evidence is gathered through the Console's Inspect action.
        ui.onNodeWithText("Console",useUnmergedTree=true).performClick()
        ui.onNodeWithText("Inspect Tenancy").performClick()
        ui.waitUntil(10000) { vm.state.value.score?.stars == 3 && vm.state.value.progress["oci-001"] == 3 }
        val snapshot=vm.state.value.world
        // Configuration change keeps the navigation back stack: the player stays in the level with the same world.
        scenario.recreate()
        ui.onNodeWithText("Mission",useUnmergedTree=true).assertExists()
        scenario.onActivity { assertEquals(snapshot,ViewModelProvider(it)[GameViewModel::class.java].state.value.world) }
        scenario.close()
        scenario=ActivityScenario.launch(MainActivity::class.java)
        lateinit var restored: GameViewModel
        scenario.onActivity { restored=ViewModelProvider(it)[GameViewModel::class.java] }
        ui.waitUntil(10000) { restored.state.value.ready && restored.state.value.world == snapshot }
        assertEquals(3,restored.state.value.score?.stars)
        scenario.close()
    }
}
