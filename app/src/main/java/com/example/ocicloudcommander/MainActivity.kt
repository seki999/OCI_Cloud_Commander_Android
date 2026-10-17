package com.example.ocicloudcommander

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import commander.*
import java.util.Locale
import kotlin.math.*

class MainActivity: ComponentActivity() {
    private var tts: TextToSpeech? = null; private var speechReady=false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts=TextToSpeech(this) { status -> if(status == TextToSpeech.SUCCESS) { tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    val voice=tts?.voices?.firstOrNull { it.locale.language == "zh" && !it.isNetworkConnectionRequired }
                    speechReady=voice != null
                    if(voice != null) tts?.voice=voice } }
        setContent {
            val vm: GameViewModel=viewModel(); val s by vm.state.collectAsStateWithLifecycle()
            val lifecycle=LocalLifecycleOwner.current.lifecycle
            var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
            DisposableEffect(lifecycle) { val observer=LifecycleEventObserver { _,_ -> foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }; lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) } }
            val nav=rememberNavController(); val entry by nav.currentBackStackEntryAsState(); val inGame=entry?.destination?.route == "game"
            LaunchedEffect(s.preferences.keepAwake,foreground,inGame) { if(s.preferences.keepAwake && foreground && inGame) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val scheme=if(s.preferences.dark) darkColorScheme(primary=if(s.preferences.highContrast) Color.Yellow else Color(0xff6de1cf),background=Color(0xff101b29),surface=Color(0xff172636)) else lightColorScheme(primary=Color(0xff006b61))
            MaterialTheme(colorScheme=scheme) { Surface(Modifier.fillMaxSize()) {
                NavHost(navController=nav,startDestination="home") {
                    composable("home") { Home(s,onContinue={ nav.navigate("game") },onChapters={ nav.navigate("chapters") },onSettings={ nav.navigate("settings") }) }
                    composable("chapters") { Chapters(s,vm,onBack={ nav.popBackStack() },onPlay={ vm.start(it); nav.navigate("game") }) }
                    composable("game") { Game(s,vm,onBack={ nav.navigate("home") { popUpTo("home") { inclusive=true } } },speak={ text -> if(s.preferences.speech && speechReady) tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"brief") }) }
                    composable("settings") { Settings(s,vm) { nav.popBackStack() } }
                }
            } }
        }
    }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

@Composable private fun Page(title: String,back: (() -> Unit)?=null,content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { if(back != null) TextButton(onClick=back) { Text("返回") }; Text(title,style=MaterialTheme.typography.titleLarge) }; content()
    }
}
@Composable private fun Home(s: GameUi,onContinue: () -> Unit,onChapters: () -> Unit,onSettings: () -> Unit) {
    Page("OCI 云端守护者 / Cloud Commander") {
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("STARPORT · 2030",style=MaterialTheme.typography.headlineMedium)
            Text("建设城市，配置基础设施，从证据中修复故障。\n非官方教育模拟器 · 完全离线 · 无真实 OCI 账号与费用")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick=onContinue,enabled=s.ready && s.world != null) { Text("继续游戏") }; OutlinedButton(onClick=onChapters,enabled=s.ready) { Text("章节星图") }; OutlinedButton(onClick=onSettings) { Text("设置") } }
            Text(s.message)
            Text("当前开发版：Phase 0–2 · 30 关（第 1–5 章）。第 6–10 章 30 关尚未开放。",style=MaterialTheme.typography.labelLarge)
            Text("自由实验室、独立故障挑战与后续章节：尚未实现。当前关卡支持无限重置、撤销和重做。")
        }
    }
}
@Composable private fun Chapters(s: GameUi,vm: GameViewModel,onBack: () -> Unit,onPlay: (Level) -> Unit) {
    Page("章节星图 · ${s.progress.size}/60",onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("新人报到","网络之城","计算引擎","数据守护","业务门户","不眠监控","消失的邮件","容器舰队","自动化革命","星港危机").forEachIndexed { i,name ->
                Text("Chapter ${i+1} · $name",style=MaterialTheme.typography.titleMedium)
                val ls=s.levels.filter { it.chapter == i+1 }
                if(ls.isEmpty()) Text("尚未实现 · L%02d–L%02d".format(i*6+1,i*6+6))
                ls.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { row.forEach { l ->
                    OutlinedButton(modifier=Modifier.weight(1f),onClick={ onPlay(l) },enabled=vm.unlocked(l)) { Text("%02d %s %s".format(l.order,s.text(l.titleKey),"★".repeat(s.progress[l.id] ?: 0))) }
                } } }
            }
        }
    }
}
@Composable private fun Game(s: GameUi,vm: GameViewModel,onBack: () -> Unit,speak: (String) -> Unit) {
    val l=s.level; val w=s.world
    if(l == null || w == null) { Page("加载世界",onBack) { Text(s.message) }; return }
    var tab by rememberSaveable { mutableIntStateOf(0) }; var selected by rememberSaveable(w.levelId) { mutableStateOf("") }
    var resetConfirm by remember { mutableStateOf(false) }
    Page("%02d · %s".format(l.order,s.text(l.titleKey)),onBack) {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            listOf("Map","Console","Terminal","Mission","Incident").forEachIndexed { i,t -> FilterChip(selected=tab==i,onClick={tab=i},label={Text(t)}) }
            TextButton(onClick=vm::undo,enabled=s.canUndo) { Text("撤销") }; TextButton(onClick=vm::redo,enabled=s.canRedo) { Text("重做") }; TextButton(onClick={resetConfirm=true}) { Text("重置") }
            TextButton(onClick={vm.dispatch(GameAction("advance_tick"))}) { Text("⏱ +1 tick") }; TextButton(onClick={vm.dispatch(GameAction("advance_tick",ticks=5))}) { Text("+5") }
            Text("t=${w.tick} · ${"%.0f".format(Cost.total(w))}/${"%.0f".format(w.budgetCredits)} Cr",style=MaterialTheme.typography.labelMedium)
        }
        Text(s.message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.fillMaxWidth().heightIn(max=60.dp).verticalScroll(rememberScrollState()))
        Box(Modifier.weight(1f).fillMaxWidth()) { when(tab) {
            0 -> WorldMap(w,selected,onSelect={selected=it;tab=1},onBuild={selected="";tab=1},onMove=vm::dispatch)
            1 -> Console(w,selected,{selected=it},vm)
            2 -> Terminal(s,vm)
            3 -> Mission(s,vm,speak)
            4 -> Incident(w)
        } }
    }
    if(resetConfirm) AlertDialog(onDismissRequest={resetConfirm=false},title={Text("重置本关？")},text={Text("当前配置和动作历史将清空，已获得星级保留。")},confirmButton={TextButton(onClick={vm.reset();resetConfirm=false}) { Text("重置") }},dismissButton={TextButton(onClick={resetConfirm=false}) { Text("取消") }})
}

@Composable private fun WorldMap(w: WorldState,selected: String,onSelect: (String) -> Unit,onBuild: () -> Unit,onMove: (GameAction) -> Unit) {
    val nodes=w.resources.values.filter { it.kind !in setOf(Kind.GROUP,Kind.DYNAMIC_GROUP,Kind.POLICY,Kind.COMPARTMENT) }.sortedBy { it.kind.ordinal }
    var scale by remember { mutableFloatStateOf(1f) }; var pan by remember { mutableStateOf(Offset.Zero) }
    Column {
        Row(verticalAlignment=Alignment.CenterVertically) { Button(onClick=onBuild) { Text("＋ 建设资源") }; TextButton(onClick={scale=1f;pan=Offset.Zero}) {Text("还原视角")}; Text("双指缩放/平移 · 长按拖动 · 点击编辑 · 线仅表示引用") }
        Box(Modifier.fillMaxSize()) {
            val positions=nodes.mapIndexed { i,r -> r.id to (w.layout[r.id]?.let { Offset(it.x.toFloat(),it.y.toFloat()) } ?: Offset(70f+(i%8)*140f,55f+(i/8)*115f)) }.toMap()
            var dragging by remember { mutableStateOf<String?>(null) }; var dragged by remember { mutableStateOf(Offset.Zero) }
            val primary=MaterialTheme.colorScheme.primary; val surface=MaterialTheme.colorScheme.surfaceVariant; val errorColor=MaterialTheme.colorScheme.error
            val labelColor=MaterialTheme.colorScheme.onSurface.toArgb()
            val labelPaint=android.graphics.Paint().apply { color=labelColor; textSize=22f; isAntiAlias=true; textAlign=android.graphics.Paint.Align.CENTER }
            fun at(id: String)=if(dragging == id) dragged else positions.getValue(id)
            Canvas(Modifier.fillMaxSize().semantics { contentDescription="资源城市地图，${nodes.size} 个节点；可通过 Console 资源列表访问所有节点" }
                .pointerInput(Unit) { detectTransformGestures { _,p,z,_ -> scale=(scale*z).coerceIn(.4f,2.5f); pan+=p } }
                .pointerInput(nodes,scale,pan) { detectDragGesturesAfterLongPress(onDragStart={ point ->
                    val p=(point-pan)/scale; val entry=positions.entries.firstOrNull { (it.value-p).getDistance() < 48f }; dragging=entry?.key; dragged=entry?.value ?: Offset.Zero
                },onDrag={ change,amount -> if(dragging != null) { change.consume(); dragged+=amount/scale } },onDragEnd={
                    dragging?.let { onMove(GameAction("move",id=it,patch=mapOf("x" to dragged.x.roundToInt().toString(),"y" to dragged.y.roundToInt().toString()))) }; dragging=null
                },onDragCancel={dragging=null}) }
                .pointerInput(nodes,scale,pan) { detectTapGestures { point -> val p=(point-pan)/scale; positions.entries.firstOrNull { (it.value-p).getDistance() < 48f }?.let { onSelect(it.key) } } }) {
                withTransform({ translate(pan.x,pan.y); scale(scale,scale,Offset.Zero) }) {
                    nodes.forEach { r -> val p=at(r.id); r.references.values.forEach { id -> positions[id]?.let { drawLine(primary.copy(alpha=.35f),p,it,3f) } } }
                    nodes.forEach { r -> val p=at(r.id)
                        val fill=if(r.id == selected) primary else surface
                        when(r.kind) {
                            Kind.VCN, Kind.SUBNET -> drawRect(fill,topLeft=p-Offset(40f,30f),size=androidx.compose.ui.geometry.Size(80f,60f))
                            Kind.LOAD_BALANCER, Kind.DNS_ZONE, Kind.CERTIFICATE -> drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(p.x,p.y-36f); lineTo(p.x+36f,p.y); lineTo(p.x,p.y+36f); lineTo(p.x-36f,p.y); close() },fill)
                            Kind.VOLUME, Kind.VOLUME_BACKUP, Kind.BUCKET, Kind.OBJECT, Kind.DATABASE, Kind.DB_BACKUP -> drawRoundRect(fill,topLeft=p-Offset(32f,32f),size=androidx.compose.ui.geometry.Size(64f,64f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(18f,18f))
                            else -> drawCircle(fill,32f,p)
                        }
                        // Status uses a dashed ring plus text, never colour alone.
                        if(problem(w,r)) drawCircle(errorColor,40f,p,style=androidx.compose.ui.graphics.drawscope.Stroke(4f,pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f,8f))))
                        drawContext.canvas.nativeCanvas.drawText(r.id,p.x,p.y+6f,labelPaint)
                        labelPaint.textSize=16f; drawContext.canvas.nativeCanvas.drawText(r.p("status").ifEmpty { r.kind.name }+(if(problem(w,r)) " ⚠" else ""),p.x,p.y+50f,labelPaint); labelPaint.textSize=22f
                    }
                }
            }
            // Accessible labels are separate controls, not text baked into the Canvas.
            // One horizontally scrolling strip keeps the canvas visible on short landscape phones.
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha=.94f))) {
                Row(Modifier.horizontalScroll(rememberScrollState())) { nodes.forEach { r -> TextButton(onClick={onSelect(r.id)}) {Text("${r.id}${if(problem(w,r)) " ⚠" else ""}")} } }
                if(nodes.isEmpty()) Text("城市尚无网络资源，点击「建设资源」从 VCN 开始。")
            }
        }
    }
}

@Composable private fun Picker(label: String,value: String,options: List<String>,onPick: (String) -> Unit,modifier: Modifier=Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) { OutlinedButton(modifier=Modifier.testTag("picker-$label"),onClick={expanded=true}) { Text("$label: ${value.ifEmpty { "未选择" }}") }; DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { options.forEach { option -> DropdownMenuItem(text={Text(option)},onClick={onPick(option);expanded=false}) } } }
}
@Composable private fun Console(w: WorldState,selected: String,onSelect: (String) -> Unit,vm: GameViewModel) {
    val current=w.resources[selected]
    Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.widthIn(min=130.dp,max=190.dp).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Picker("Region",w.region,Catalog.regions.toList(),{vm.dispatch(GameAction("select_region",value=it))})
            OutlinedButton(onClick={onSelect("")}) { Text("＋ 创建资源") }
            TextButton(onClick={vm.dispatch(GameAction("inspect",id="root"))}) {Text("Inspect Tenancy")}
            w.resources.values.sortedBy { it.kind.ordinal }.forEach { r -> TextButton(onClick={onSelect(r.id)}) { Text("${r.id}\n${r.kind}",color=if(selected == r.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) } }
        }
        ResourceForm(w,current,Modifier.weight(1f),onSubmit=vm::dispatch,onCreated={onSelect(it)})
    }
}
@Composable private fun ResourceForm(w: WorldState,current: Resource?,modifier: Modifier,onSubmit: (GameAction) -> Unit,onCreated: (String) -> Unit) {
    var kind by rememberSaveable(current?.id) { mutableStateOf(current?.kind?.name ?: "VCN") }
    val k=Kind.valueOf(kind)
    var id by rememberSaveable(current?.id) { mutableStateOf(current?.id ?: "") }
    var compartment by rememberSaveable(current?.id) { mutableStateOf(current?.compartment ?: if("dev" in w.resources) "dev" else "root") }
    var region by rememberSaveable(current?.id) { mutableStateOf(current?.region ?: w.region.ifBlank { "ap-tokyo-1" }) }
    val props=remember(current?.id,kind,current) { mutableStateMapOf<String,String>().also { m -> Validation.fields.getValue(k).sorted().forEach { m[it]=current?.properties?.get(it) ?: Validation.defaults[it].orEmpty() } } }
    val refs=remember(current?.id,kind,current) { mutableStateMapOf<String,String>().also { m -> Validation.refs[k].orEmpty().sorted().forEach { m[it]=current?.references?.get(it).orEmpty() } } }
    var deleteConfirm by remember { mutableStateOf(false) }
    Column(modifier.fillMaxHeight().verticalScroll(rememberScrollState()).imePadding(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(current == null) "新建资源" else "配置 ${current.id}",style=MaterialTheme.typography.titleMedium)
        if(current == null) { Picker("Kind",kind,Kind.entries.map { it.name },{kind=it}); OutlinedTextField(value=id,onValueChange={id=it},label={Text("资源 ID（短英文）")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("resource-id")) }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Picker("Compartment",compartment,listOf("root")+w.resources.values.filter { it.kind == Kind.COMPARTMENT }.map { it.id },{compartment=it}); if(current == null) Picker("Region",region,Catalog.regions.toList(),{region=it}) }
        if(current != null) {
            val derived=current.properties.filterKeys { it in Validation.derived[current.kind].orEmpty() }
            Text("Region=${current.region}"+if(derived.isEmpty()) "" else derived.entries.joinToString("") { " · ${it.key}=${it.value}" }+" （派生状态，只读）")
            ServiceStatus(w,current,onSubmit)
        }
        val locked=current != null && current.kind in Validation.immutable
        if(locked) Text("${current!!.kind} 创建后不可修改；需要变更时新建资源并替换引用。",color=MaterialTheme.colorScheme.error)
        props.forEach { (field,value) ->
            when(field) {
                "rules" -> SecurityRulesField(value) { props[field]=it }
                "routes" -> RoutesField(w,value) { props[field]=it }
                else -> OutlinedTextField(value=value,onValueChange={props[field]=it},label={Text(field)},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("property-$field"),supportingText={Text(fieldHelp(field))})
            }
        }
        refs.forEach { (field,value) ->
            val allowed=Validation.refKinds(k,field)
            Picker(field,value,listOf("")+w.resources.values.filter { it.kind in allowed }.map { it.id },{refs[field]=it})
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(modifier=Modifier.testTag("resource-submit"),enabled=!locked,onClick={
                if(current == null) { onSubmit(GameAction("create",resource=Resource(id,k,compartment,region,props.toMap(),refs.filterValues { it.isNotEmpty() }))); onCreated(id) }
                else onSubmit(GameAction("update",id=current.id,patch=props.toMap()+("compartment" to compartment),references=refs.toMap()))
            }) {Text(if(current == null) "创建" else "应用配置")}
            if(current != null) { OutlinedButton(onClick={onSubmit(GameAction("inspect",id=current.id))}) {Text("Inspect")}; TextButton(onClick={deleteConfirm=true}) {Text("删除")} }
        }
        Spacer(Modifier.height(24.dp))
    }
    if(deleteConfirm && current != null) AlertDialog(onDismissRequest={deleteConfirm=false},title={Text("删除 ${current.id}？")},text={Text("若存在依赖，模拟引擎会拒绝删除。此操作可撤销。")},confirmButton={TextButton(onClick={onSubmit(GameAction("delete",id=current.id));deleteConfirm=false}) {Text("删除")}},dismissButton={TextButton(onClick={deleteConfirm=false}) {Text("取消")}})
}
/** Derived problem marker for the map; recomputed from rules, never stored. */
internal fun problem(w: WorldState,r: Resource): Boolean = runCatching { when(r.kind) {
    Kind.COMPUTE -> r.p("status") != "RUNNING" || !Disk.check(w,r).healthy
    Kind.DATABASE -> r.p("status") != "AVAILABLE"
    Kind.LOAD_BALANCER -> Lb.health(w,r).any { !it.healthy } || r.ref("certificate").isNotEmpty() && w.tick >= (w.resources[r.ref("certificate")]?.p("notAfterTick")?.toLongOrNull() ?: 0L)
    Kind.CERTIFICATE -> w.tick >= (r.p("notAfterTick").toLongOrNull() ?: 0L)
    Kind.BUCKET -> r.p("visibility") == "public"
    else -> false
} }.getOrDefault(true)

@Composable private fun ServiceStatus(w: WorldState,r: Resource,onSubmit: (GameAction) -> Unit) {
    val lines=runCatching { when(r.kind) {
        Kind.COMPUTE -> listOf("磁盘：${Disk.check(w,r).reason}")+(if(r.p("dbEndpoint").isNotEmpty()) listOf("数据库：${Db.connected(w,r.id).reason}") else emptyList())
        Kind.LOAD_BALANCER -> Lb.report(w,r).lines()+listOfNotNull(w.resources[r.ref("certificate")]?.let { "证书 ${it.id} 有效至 tick ${it.p("notAfterTick")}（当前 ${w.tick}）" })
        Kind.DATABASE -> listOf("状态 ${r.p("status")} · 最新备份 tick ${Db.latestBackupTick(w,r.id) ?: "无"}")
        Kind.BASTION_SESSION -> listOf(if(w.tick >= r.p("expiresTick").toLong()) "Session 已过期" else "Session 有效至 tick ${r.p("expiresTick")}")
        Kind.CERTIFICATE -> listOf(if(w.tick >= r.p("notAfterTick").toLong()) "已过期" else "有效至 tick ${r.p("notAfterTick")}")
        Kind.BUCKET -> listOf(if(r.p("visibility") == "public") "⚠ 匿名可读" else "匿名拒绝（私有）")
        else -> emptyList()
    } }.getOrElse { listOf("状态无法计算：${it.message}") }
    if(lines.isEmpty() && r.kind != Kind.COMPUTE) return
    Card { Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text("运行状态 · 由规则实时推导",style=MaterialTheme.typography.titleSmall)
        lines.forEach { Text(it,style=MaterialTheme.typography.bodySmall) }
        if(r.kind == Kind.COMPUTE) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled=r.p("status") == "STOPPED",onClick={onSubmit(GameAction("power",id=r.id,value="START"))}) { Text("启动 START") }
            OutlinedButton(enabled=r.p("status") == "RUNNING",onClick={onSubmit(GameAction("power",id=r.id,value="STOP"))}) { Text("停止 STOP") }
        }
    } }
}
private fun fieldHelp(field: String)=when(field) {
    "rules" -> "多条用 ; 分隔：ingress|10.10.1.0/24|tcp|8080|stateful。默认拒绝；stateless 需回程 49152。"
    "routes" -> "多条用 ; 分隔：0.0.0.0/0>igw 或 oci-services>sgw；Gateway 必须先创建。"
    "members" -> "用户 ID，逗号分隔，如 newcomer"
    "nsgs" -> "NSG ID，逗号分隔；NSG 需与 Subnet 同一 VCN"
    "resourceType" -> "资源 Kind，如 COMPUTE、BUCKET；all-resources 属于高危授权"
    "scope" -> "Compartment ID，如 dev；tenancy 属于高危授权"
    "verb" -> "inspect < read < use < manage"
    "targetId" -> "可选：将权限进一步限定为单个资源 ID"
    "matchInstance" -> "精确匹配的 Compute ID（教学 Dynamic Group 子集）"
    "matchCompartment" -> "匹配实例的 Compartment ID"
    "publicIp" -> "清空表示没有公网地址；Private Subnet 必须留空"
    "public", "enabled" -> "true / false"
    "listenPorts" -> "监听端口（Compute 进程 / LB Listener），逗号分隔，范围 1–65535"
    "cidr" -> "规范 IPv4 网络 CIDR，如 10.10.0.0/16"
    "service" -> "当前支持 oci-services，教学服务地址 192.0.2.80"
    "sshKey" -> "只输入模拟密钥别名（如 ops-key）；禁止真实密钥"
    "shape" -> Catalog.shapes.entries.joinToString("；") { "${it.key}=${it.value.rps}req/s·${it.value.credits.toInt()}Cr" }
    "image" -> Catalog.images.joinToString(" / ")
    "logPath" -> "应用日志目录，如 /data/logs；落在哪个挂载点决定写入哪块卷"
    "httpPaths" -> "应用提供的 HTTP 路径，逗号分隔，如 /,/health"
    "dbEndpoint" -> "应用连接的 DATABASE ID"
    "dbSecret", "adminSecret" -> "只接受 vault:<name> 引用，禁止明文密码"
    "allowedCidr" -> "允许连接 Bastion 的客户端 CIDR，如 198.51.100.0/24"
    "ttlTicks" -> "Session 有效期 1–180 tick"
    "port" -> "端口 1–65535"
    "sizeGb" -> "Block Volume 容量 50–32768 GB"
    "mountPoint" -> "挂载目录，如 /data；留空表示仅 attach 未挂载"
    "backupPolicy" -> "none / bronze / silver / gold"
    "type" -> "FULL / INCREMENTAL（教学中均复制完整校验值）"
    "name" -> "对象名，如 logs/app.log"
    "sizeMb" -> "对象大小 MB，影响模拟存储成本"
    "lifecycle" -> "prefix|archive:30|delete:90，多条用 ; 分隔；1 tick = 1 模拟日"
    "autoBackup" -> "true / false"
    "retentionDays" -> "备份保留天数 1–60"
    "backends" -> "compute-id:port，逗号分隔，如 web1:8080,web2:8080"
    "healthPath" -> "健康检查 HTTP 路径，后端须在 httpPaths 提供"
    "healthTimeoutMs" -> "健康检查超时 50–10000ms；过低会在高负载时误摘后端"
    "redirectHttp" -> "true 时 80 → 301 → 443；需同时监听 80 与 443"
    "domain" -> "Zone 域名，如 starport.example"
    "records" -> "name|A|IPv4|ttl 或 name|CNAME|fqdn|ttl，多条用 ; 分隔"
    "commonName" -> "证书名称，如 www.starport.example 或 *.starport.example"
    "validTicks" -> "证书有效期 1–397 tick；证书不可修改，需新签发替换"
    else -> "本地模拟配置字段"
}

@Composable private fun Terminal(s: GameUi,vm: GameViewModel) {
    var input by rememberSaveable { mutableStateOf("") }; val scroll=rememberScrollState()
    LaunchedEffect(s.terminal.size) { scroll.animateScrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize().imePadding(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState())) { Commands.examples.forEach { example -> TextButton(onClick={input=example}) {Text(example)} } }
        Text(s.terminal.joinToString("\n"),modifier=Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment=Alignment.CenterVertically) { OutlinedTextField(value=input,onValueChange={input=it},modifier=Modifier.weight(1f),label={Text("SIMULATED command")},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={vm.command(input);input=""})); Button(onClick={vm.command(input);input=""},modifier=Modifier.padding(start=8.dp)) {Text("执行")} }
    }
}
@Composable private fun Mission(s: GameUi,vm: GameViewModel,speak: (String) -> Unit) {
    val l=s.level!!; val w=s.world!!; val score=s.score!!
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(s.text(l.scenario.briefKey),style=MaterialTheme.typography.titleMedium)
        TextButton(onClick={speak(s.text(l.scenario.briefKey))}) {Text("朗读剧情（设置中启用，语音包可选）")}
        l.tutorialKeys.forEach { Text(s.text(it)) }
        l.steps.forEachIndexed { i,step -> Text("${i+1}. ${s.text(step.textKey)}") }
        if(l.order == 1) Catalog.cards.forEach { (id,explanation) -> OutlinedButton(onClick={vm.dispatch(GameAction("study_card",id=id))}) {Text("${if(id in w.studiedCards) "✓" else "○"} $id：$explanation")} }
        Text("目标 · 即时检查",style=MaterialTheme.typography.titleMedium)
        (score.required+score.constraints+l.optionalObjectives.map { it to Predicates.evaluate(w,it) }).forEach { (o,c) -> Text("${if(c.passed) "✓" else "○"} ${s.text(o.messageKey)}\n${c.reason}") }
        l.failureTriggers.filter { Predicates.evaluate(w,it).passed }.forEach { Text("⚠ ${s.text(it.messageKey)}",color=MaterialTheme.colorScheme.error) }
        Button(onClick={vm.dispatch(GameAction("hint"))}) {Text("Nova 提示（已用 ${w.hintsUsed} 次，前三次免费；三星最多 1 次）")}
        l.steps.flatMap { it.hintKeys }.take(w.hintsUsed).forEach {Text("Nova：${s.text(it)}")}
        if(score.stars > 0) {
            Text("关卡达成 ${"★".repeat(score.stars)} · XP ${l.reward.xp}",style=MaterialTheme.typography.headlineSmall)
            Text("林岚复盘：从症状收集证据，修改根因配置，再验证目标和越权拒绝。\n变更 ${w.changes}/${l.optimalActionLimit} · 提示 ${w.hintsUsed} · 安全检查 ${score.constraints.all { it.second.passed }}")
            if(l.reward.badgeId.isNotEmpty()) Text("解锁奖章：${l.reward.badgeId}")
            s.levels.find { it.order == l.order+1 }?.let { next -> Button(onClick={vm.start(next)}) {Text("下一关：${s.text(next.titleKey)}")} }
            if(l.order == 30) Text("Phase 2 完成。第 6–10 章（Phase 3–5）尚未实现，未计入已完成内容。")
        }
    }
}
@Composable private fun Incident(w: WorldState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("事件与证据 · Tick ${w.tick}",style=MaterialTheme.typography.titleMedium)
        Text("服务摘要（规则实时推导）· 模拟成本 ${"%.1f".format(Cost.total(w))} / ${w.budgetCredits} Credits · 请求 ${w.requestRate}/s",style=MaterialTheme.typography.titleSmall)
        w.resources.values.filter { problem(w,it) }.forEach { Text("⚠ ${it.id} (${it.kind})",color=MaterialTheme.colorScheme.error) }
        w.resources.values.filter { it.kind == Kind.LOAD_BALANCER }.forEach { lb -> runCatching { Lb.slo(w,lb) }.getOrNull()?.let { Text("${lb.id}: 成功率 ${"%.1f".format(it.successPct)}% · 延迟 ${it.latencyMs}ms · 利用率 ${it.utilizationPct}%") } }
        Text("Metric / Alarm / Logging / SMTP 将在 Phase 3 实现；当前为动作与探测事件流。",style=MaterialTheme.typography.bodySmall)
        Text("已收集证据：${w.evidence.joinToString()}")
        w.events.asReversed().forEach { e -> Text("#${e.sequence} · t=${e.tick} · ${e.actor} / ${e.action}\n${e.detail}") }
    }
}
@Composable private fun Settings(s: GameUi,vm: GameViewModel,onBack: () -> Unit) {
    Page("设置",onBack) { Column(Modifier.verticalScroll(rememberScrollState())) {
        listOf(Triple("dark","暗色主题",s.preferences.dark),Triple("contrast","高对比强调色",s.preferences.highContrast),Triple("awake","关卡前台保持屏幕常亮",s.preferences.keepAwake),Triple("speech","可选中文朗读（无语音包不阻塞）",s.preferences.speech)).forEach { (key,label,value) -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f)); Switch(checked=value,onCheckedChange={vm.setting(key,it)}) } }
        Text("语言：简体中文；English / 日本語 翻译尚未实现。\n字号遵循 Android 系统设置。设备锁屏遵从系统控制。\n当前不含音效与震动。价格为教学 Credits，不是 Oracle 实价。")
    } }
}
