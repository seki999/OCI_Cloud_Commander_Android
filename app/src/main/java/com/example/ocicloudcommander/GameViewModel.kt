package com.example.ocicloudcommander

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import commander.*
import com.example.ocicloudcommander.persistence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString

data class GameUi(
    val levels: List<Level> = emptyList(), val strings: Map<String,String> = emptyMap(), val level: Level? = null,
    val world: WorldState? = null, val score: Score? = null, val progress: Map<String,Int> = emptyMap(),
    val preferences: Preferences = Preferences(), val message: String = "正在加载离线世界…", val ready: Boolean = false,
    val terminal: List<String> = listOf("OCI Cloud Commander · SIMULATED · 完全离线"), val canUndo: Boolean = false, val canRedo: Boolean = false
) { fun text(key: String)=strings[key] ?: key }
class GameViewModel(app: Application): AndroidViewModel(app) {
    private val repo=ProgressRepository(app)
    private val mutex=Mutex()
    private val _state=MutableStateFlow(GameUi()); val state=_state.asStateFlow()
    private var actions=listOf<GameAction>(); private var cursor=0
    init {
        viewModelScope.launch { repo.preferences.collect { _state.value=_state.value.copy(preferences=it) } }
        viewModelScope.launch { mutex.withLock {
            try {
                val content=withContext(Dispatchers.IO) {
                    fun asset(path: String)=app.assets.open(path).bufferedReader().use { it.readText() }
                    val strings=ContentLoader.json.decodeFromString<Map<String,String>>(asset("locales/zh-CN.json"))
                    val index=ContentLoader.json.decodeFromString<List<String>>(asset("index.json"))
                    val levels=index.map { ContentLoader.level(asset(it),strings) }; ContentLoader.validateCollection(levels)
                    levels to strings
                }
                _state.value=_state.value.copy(levels=content.first,strings=content.second,progress=repo.progress())
                val save=repo.load()
                if(save != null) {
                    require(save.schemaVersion == 1 && save.cursor in 0..save.actions.size) { "存档版本或历史游标无效" }
                    val level=content.first.first { it.id == save.levelId }
                    val replay=Simulator(level).replay(save.actions.take(save.cursor)); require(replay == save.snapshot) { "存档快照与事件重放不一致" }
                    actions=save.actions; cursor=save.cursor; publish(level,replay,"已从 Room 恢复；事件重放校验一致")
                } else { val first=content.first.first(); publish(first,first.initialWorld,"欢迎加入星港市运维团队") }
            } catch(e: Exception) { _state.value=_state.value.copy(ready=true,message="加载失败：${e.message}。可从章节重新开始。") }
        }}
    }
    fun unlocked(level: Level) = level.dependsOn.all { (_state.value.progress[it] ?: 0) >= 1 }
    private fun publish(l: Level,w: WorldState,msg: String) { _state.value=_state.value.copy(level=l,world=w,score=Predicates.score(w,l),message=msg,ready=true,canUndo=cursor>0,canRedo=cursor<actions.size) }
    private suspend fun persist() {
        val s=_state.value; val l=s.level ?: return; val w=s.world ?: return
        repo.save(SaveGame(levelId=l.id,actions=actions,cursor=cursor,snapshot=w),s.score?.stars ?: 0)
        _state.value=_state.value.copy(progress=repo.progress())
    }
    private fun work(block: suspend () -> Unit) { viewModelScope.launch { mutex.withLock { try { block() } catch(e: Exception) { _state.value=_state.value.copy(message="操作失败：${e.message}") } } } }
    fun start(level: Level) = work { require(unlocked(level)) { "先完成前一关" }; actions=emptyList(); cursor=0; publish(level,level.initialWorld,"已载入 ${_state.value.text(level.titleKey)}"); persist() }
    fun dispatch(action: GameAction) = work { apply(action) }
    private suspend fun apply(action: GameAction) {
        val l=_state.value.level ?: return; val w=_state.value.world ?: return
        val result=Simulator(l).dispatch(w,action)
        if(result.accepted) { actions=actions.take(cursor)+action; cursor++; publish(l,result.world,result.message); persist() }
        else _state.value=_state.value.copy(message=result.message)
    }
    fun undo() = work { if(cursor>0) { cursor--; val l=_state.value.level!!; publish(l,Simulator(l).replay(actions.take(cursor)),"已撤销并重新计算全部目标"); persist() } }
    fun redo() = work { if(cursor<actions.size) { cursor++; val l=_state.value.level!!; publish(l,Simulator(l).replay(actions.take(cursor)),"已重做"); persist() } }
    fun reset() { _state.value.level?.let(::start) }
    fun command(input: String) = work {
        val parsed=Commands.parse(input); val w=_state.value.world ?: return@work
        val output=when { parsed.error != null -> "SIMULATED: ${parsed.error}"; parsed.queryKind != null -> Commands.query(w,parsed.queryKind!!); parsed.report != null -> parsed.report!!(w); parsed.action != null -> { apply(parsed.action!!); _state.value.message }; else -> "SIMULATED: unsupported" }
        _state.value=_state.value.copy(terminal=(_state.value.terminal+"> $input"+output).takeLast(100))
    }
    fun setting(key: String,value: Boolean)=work { repo.setting(key,value) }
}
