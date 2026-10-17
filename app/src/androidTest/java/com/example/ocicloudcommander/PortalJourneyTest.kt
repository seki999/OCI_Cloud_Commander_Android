package com.example.ocicloudcommander

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ocicloudcommander.persistence.ProgressRepository
import commander.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** L25: prerequisite setup via repository; all gameplay repairs use Console/Terminal controls. */
class PortalJourneyTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun capture(name: String) {
        val file=File(context.filesDir,"screens/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun loadBalancerLevelThroughRealConsoleAndTerminal() {
        val strings=ContentLoader.json.decodeFromString<Map<String,String>>(context.assets.open("locales/zh-CN.json").bufferedReader().readText())
        val levels=ContentLoader.json.decodeFromString<List<String>>(context.assets.open("index.json").bufferedReader().readText()).map { ContentLoader.level(context.assets.open(it).bufferedReader().readText(),strings) }
        assertEquals(30,levels.size)
        // Unlock prerequisites only: progress rows, never a solved world.
        runBlocking { val repo=ProgressRepository(context); levels.take(24).forEach { repo.save(SaveGame(levelId=it.id,actions=emptyList(),cursor=0,snapshot=it.initialWorld),1) } }
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        lateinit var vm: GameViewModel
        scenario.onActivity { vm=ViewModelProvider(it)[GameViewModel::class.java] }
        ui.waitUntil(15000) { vm.state.value.ready && vm.state.value.progress.size >= 24 }
        val l25=levels.first { it.order == 25 }
        ui.runOnIdle { vm.start(l25) }
        ui.waitUntil(10000) { vm.state.value.level?.id == "oci-025" && vm.state.value.world?.changes == 0 }
        ui.onNodeWithText("继续游戏").performClick()
        ui.onNodeWithText("Map",useUnmergedTree=true).performClick()
        capture("l25-map-before")
        fun closeSoftKeyboard() { scenario.onActivity { activity ->
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(activity.window.decorView.windowToken,0)
        } }
        // Only prerequisite progress / level selection use test setup. Every repair is entered through real UI controls.
        ui.onNodeWithText("Console",useUnmergedTree=true).performClick()
        fun field(key: String,value: String) {
            ui.onNodeWithTag("property-$key").performScrollTo().performTextReplacement(value)
            closeSoftKeyboard()
        }
        fun pick(key: String,value: String) {
            ui.onNodeWithTag("picker-$key").performScrollTo().performClick()
            ui.onNodeWithText(value,useUnmergedTree=true).performScrollTo().performClick()
        }
        fun createResource(kind: String,id: String,fields: Map<String,String>,refs: Map<String,String>) {
            ui.onNodeWithText("＋ 创建资源").performScrollTo().performClick()
            pick("Kind",kind)
            ui.onNodeWithTag("resource-id").performScrollTo().performTextReplacement(id)
            closeSoftKeyboard()
            fields.forEach { (key,value) -> field(key,value) }
            refs.forEach { (key,value) -> pick(key,value) }
            ui.onNodeWithTag("resource-submit").performScrollTo().performClick()
            ui.waitUntil(10000) { id in vm.state.value.world!!.resources }
        }
        createResource("COMPUTE","web2",mapOf(
            "privateIp" to "10.10.1.11", "nsgs" to "web-nsg", "listenPorts" to "8080", "sshKey" to "ops-key",
            "shape" to "VM.Standard.E4.Flex.2", "image" to "Oracle-Linux-9", "httpPaths" to "/,/health"
        ),mapOf("subnet" to "web-sub"))
        createResource("LOAD_BALANCER","lb",mapOf(
            "privateIp" to "10.10.1.50", "publicIp" to "203.0.113.50", "nsgs" to "lb-nsg", "listenPorts" to "80",
            "backends" to "web1:8080,web2:8080"
        ),mapOf("subnet" to "web-sub"))
        ui.onNodeWithText("Inspect Tenancy").performScrollTo().performClick()
        ui.onNodeWithText("dev\nCOMPARTMENT").performScrollTo().performClick()
        ui.onNodeWithText("Inspect",useUnmergedTree=true).performScrollTo().performClick()
        ui.waitUntil(10000) { vm.state.value.score?.stars == 3 && vm.state.value.progress["oci-025"] == 3 }
        ui.onNodeWithText("Map",useUnmergedTree=true).performClick()
        capture("l25-map-after")
        ui.onNodeWithText("Console",useUnmergedTree=true).performClick()
        ui.onNodeWithText("lb\nLOAD_BALANCER").performScrollTo().performClick()
        ui.onNodeWithText("运行状态 · 由规则实时推导").assertExists()
        capture("l25-console-lb")
        ui.onNodeWithText("Terminal",useUnmergedTree=true).performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("oci lb backend-health lb")
        closeSoftKeyboard()
        ui.onNodeWithText("执行").performClick()
        ui.waitUntil(5000) { vm.state.value.terminal.any { it.contains("backend-health") } }
        capture("l25-terminal")
        ui.onNodeWithText("Mission",useUnmergedTree=true).performClick()
        capture("l25-mission")
        scenario.close()
    }
}
