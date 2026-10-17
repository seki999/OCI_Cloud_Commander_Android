package com.example.ocicloudcommander

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import commander.*

@Composable
private fun Choice(value: String,options: List<String>,onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick={open=true}) {Text(value)}; DropdownMenu(expanded=open,onDismissRequest={open=false}) { options.forEach { option -> DropdownMenuItem(text={Text(option)},onClick={onChange(option);open=false}) } } }
}

@Composable
internal fun SecurityRulesField(value: String,onChange: (String) -> Unit) {
    var direction by rememberSaveable { mutableStateOf("ingress") }
    var cidr by rememberSaveable { mutableStateOf("10.10.0.0/16") }
    var protocol by rememberSaveable { mutableStateOf("tcp") }
    var port by rememberSaveable { mutableStateOf("443") }
    var stateful by rememberSaveable { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    val rules=value.split(';').filter(String::isNotBlank)
    Card { Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("NSG rules · 默认拒绝",style=MaterialTheme.typography.titleMedium)
        rules.forEachIndexed { i,text ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(text,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={
                    runCatching { Network.parseRule(text) }.onSuccess { rule ->
                        direction=rule.direction; cidr=text.split('|')[1]; protocol=rule.protocol;port=rule.port.toString();stateful=rule.stateful
                    }
                }) {Text("载入")}
                TextButton(onClick={onChange(rules.filterIndexed { j,_ -> i!=j }.joinToString(";"))}) {Text("移除")}
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Choice(direction,listOf("ingress","egress"),{direction=it}); Choice(protocol,listOf("tcp","udp","icmp","all"),{protocol=it})
            Text("Stateful"); Switch(checked=stateful,onCheckedChange={stateful=it})
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value=cidr,onValueChange={cidr=it},label={Text("源 / 目标 CIDR")},singleLine=true,modifier=Modifier.weight(2f))
            OutlinedTextField(value=port,onValueChange={port=it},label={Text("目标端口")},singleLine=true,modifier=Modifier.weight(1f))
        }
        Button(onClick={
            val rule="$direction|$cidr|$protocol|$port|${if(stateful) "stateful" else "stateless"}"
            runCatching { Network.parseRule(rule) }.onSuccess { if(rule !in rules) onChange((rules+rule).joinToString(";")); message="已加入草稿；点击应用配置提交" }.onFailure { message=it.message.orEmpty() }
        }) {Text("添加规则到草稿")}
        if(message.isNotEmpty()) Text(message,style=MaterialTheme.typography.bodySmall)
        Text("Stateful 自动允许响应；Stateless 需明确回程规则（本教学模型固定临时端口 49152）。载入后修改可添加新规则，再移除原规则。",style=MaterialTheme.typography.bodySmall)
    } }
}

@Composable
internal fun RoutesField(w: WorldState,value: String,onChange: (String) -> Unit) {
    var destination by rememberSaveable { mutableStateOf("0.0.0.0/0") }
    var gateway by rememberSaveable { mutableStateOf("") }; var message by remember { mutableStateOf("") }
    val routes=value.split(';').filter(String::isNotBlank)
    val gateways=w.resources.values.filter { it.kind in setOf(Kind.IGW,Kind.NAT,Kind.SERVICE_GATEWAY) }.map { it.id }
    Card { Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Route Table · Longest Prefix Match",style=MaterialTheme.typography.titleMedium)
        routes.forEachIndexed { i,text -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(text,Modifier.weight(1f)); TextButton(onClick={onChange(routes.filterIndexed { j,_ -> i!=j }.joinToString(";"))}) {Text("移除")} } }
        OutlinedTextField(value=destination,onValueChange={destination=it},label={Text("目的 CIDR / oci-services")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Gateway"); Choice(gateway.ifEmpty { "选择 Gateway" },gateways,{gateway=it})
            Button(onClick={
                runCatching { require(destination == "oci-services" || Cidr.parse(destination).prefix in 0..32); require(gateway in gateways) { "先建立并选择 Gateway" } }.onSuccess {
                    val retained=routes.filter { it.substringBefore('>') != destination }; onChange((retained+"$destination>$gateway").joinToString(";")); message="已加入草稿；点击应用配置提交"
                }.onFailure { message=it.message ?: "目的 CIDR 无效" }
            }) {Text("添加 Route 到草稿")}
        }
        if(message.isNotEmpty()) Text(message,style=MaterialTheme.typography.bodySmall)
        Text("0.0.0.0/0 表示默认路由；oci-services 是受支持 OCI 服务教学标签。Gateway 必须属于相同 VCN/Region。",style=MaterialTheme.typography.bodySmall)
    } }
}
