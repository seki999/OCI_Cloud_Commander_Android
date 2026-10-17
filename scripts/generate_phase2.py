"""Generates Phase 2 content (L13-L30): level JSON, test-only solution fixtures, zh-CN strings and the Schema registry.

Phase 1 level files are not regenerated. Rerunning is idempotent: it rewrites only L13-L30, merges locale keys,
rebuilds index.json from the chapter files present and refreshes Schema enums / predicate registry.
"""
import json, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[1]
C = ROOT / 'content'
L = {}
LEVELS = []

def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

# Mirrors commander.Predicates.parameters; SchemaRegistryTest asserts both stay equal.
PREDICATES = {
    'world.region': ['value'], 'concept.studied': ['card'], 'resource.exists': ['id', 'kind'], 'resource.absent': ['id'],
    'resource.propertyEquals': ['id', 'key', 'value'], 'resource.compartmentEquals': ['id', 'compartment'],
    'iam.permits': ['principal', 'verb', 'kind', 'compartment', 'id'], 'iam.denies': ['principal', 'verb', 'kind', 'compartment', 'id'],
    'iam.leastPrivilege': [], 'iam.hasBroadPermissions': [], 'logging.evidenceFound': ['id'], 'resource.referenceEquals': ['id', 'key', 'value'],
    'network.reachable': ['source', 'destination', 'port'], 'network.unreachable': ['source', 'destination', 'port'], 'network.nonOverlapping': [],
    'network.publicAddressable': ['id', 'value'], 'network.subnetContained': ['id', 'vcn'], 'security.privateInstances': ['ids'], 'cost.underBudget': [],
    'compute.running': ['id'], 'compute.diskHealthy': ['id'], 'ssh.allowed': ['client', 'target', 'via'], 'ssh.denied': ['client', 'target', 'via'],
    'storage.protected': ['id'], 'storage.backupRestorable': ['source', 'restored'], 'storage.objectRestricted': ['bucket', 'principal'],
    'storage.lifecycleConfigured': ['bucket', 'prefix', 'archiveDays', 'deleteDays'], 'storage.objectTier': ['id', 'tier'], 'db.connected': ['app'], 'db.dataChecksumEquals': ['app', 'checksum'], 'db.rpoWithin': ['app', 'source', 'maxTicks'],
    'db.backupFresh': ['id', 'maxAge', 'minRetention'], 'lb.backendsHealthy': ['id', 'min'], 'slo.meetsTarget': ['id', 'minSuccessPct', 'maxLatencyMs'],
    'dns.resolves': ['name', 'target'], 'tls.validHostname': ['id', 'host'], 'lb.httpRedirect': ['id'], 'portal.endToEnd': ['url', 'minBackends'],
}
KINDS = ['COMPARTMENT', 'GROUP', 'DYNAMIC_GROUP', 'POLICY', 'VCN', 'SUBNET', 'IGW', 'NAT', 'SERVICE_GATEWAY', 'ROUTE_TABLE', 'NSG', 'COMPUTE', 'BUCKET',
         'BASTION', 'BASTION_SESSION', 'VOLUME', 'VOLUME_BACKUP', 'OBJECT', 'DATABASE', 'DB_BACKUP', 'LOAD_BALANCER', 'DNS_ZONE', 'CERTIFICATE']
ACTIONS = ['select_region', 'study_card', 'create', 'update', 'delete', 'inspect', 'probe', 'advance_tick', 'hint', 'move', 'power', 'ssh', 'dig', 'http']
TUTORIAL_TAIL = '这是非官方离线教育模拟器，所有结果由本地规则推导；教学 ID、地址、价格与 OCI 实际控制台不同。'

# ---------- builders ----------
def r(id, kind, p=None, refs=None, comp='dev', region='ap-tokyo-1'):
    return dict(id=id, kind=kind, compartment=comp, region=region, properties={k: str(v) for k, v in (p or {}).items()}, references=refs or {})
def create(x): return dict(type='create', resource=x)
def update(id, p=None, refs=None): return dict(type='update', id=id, patch={k: str(v) for k, v in (p or {}).items()}, references=refs or {})
def tick(n): return dict(type='advance_tick', ticks=n)
def power(id, value): return dict(type='power', id=id, value=value)
def obj(oid, predicate, message, **params):
    assert sorted(params) == sorted(PREDICATES[predicate]), (oid, predicate)
    key = 'goals.' + oid; L[key] = message
    return dict(id=oid, predicate=predicate, params={k: str(v) for k, v in params.items()}, messageKey=key)

def compartments(): return {x: r(x, 'COMPARTMENT', dict(name=x, parent='root'), comp='root') for x in ['dev', 'prod']}
def net(web='', app='', db='', lb=None):
    w = compartments()
    for x in [r('vcn', 'VCN', dict(cidr='10.10.0.0/16')), r('igw', 'IGW', dict(enabled='true'), dict(vcn='vcn')),
              r('public-rt', 'ROUTE_TABLE', dict(routes='0.0.0.0/0>igw'), dict(vcn='vcn')),
              r('web-sub', 'SUBNET', dict(cidr='10.10.1.0/24', public='true'), dict(vcn='vcn', routeTable='public-rt')),
              r('app-sub', 'SUBNET', dict(cidr='10.10.2.0/24', public='false'), dict(vcn='vcn')),
              r('db-sub', 'SUBNET', dict(cidr='10.10.3.0/24', public='false'), dict(vcn='vcn')),
              r('web-nsg', 'NSG', dict(rules=web), dict(vcn='vcn')), r('app-nsg', 'NSG', dict(rules=app), dict(vcn='vcn')),
              r('db-nsg', 'NSG', dict(rules=db), dict(vcn='vcn'))]:
        w[x['id']] = x
    if lb is not None: w['lb-nsg'] = r('lb-nsg', 'NSG', dict(rules=lb), dict(vcn='vcn'))
    return w
def compute(id, sub, ip, nsg, ports, public='', key='ops-key', shape='VM.Standard.E4.Flex.2', status='RUNNING', **extra):
    p = dict(status=status, privateIp=ip, publicIp=public, nsgs=nsg, listenPorts=ports, sshKey=key, shape=shape, image='Oracle-Linux-9')
    p.update(extra); return r(id, 'COMPUTE', p, dict(subnet=sub))
def put(w, *items):
    for x in items: w[x['id']] = x
    return w
WEB_RULE = 'ingress|0.0.0.0/0|tcp|443|stateful'

def add(n, title, brief, w, goals, golden, negative, steps, hints, tutorial, constraints=(), failures=(), principals=None,
        tick_=0, rate=0, budget=500.0, badge=''):
    prefix = f'levels.{n:03d}'
    L[prefix + '.title'] = title; L[prefix + '.brief'] = brief; L[prefix + '.tutorial'] = tutorial + TUTORIAL_TAIL
    st = []
    for i, (text, hint) in enumerate(zip(steps, hints), 1):
        k, h = f'{prefix}.step{i}', f'{prefix}.hint{i}'; L[k] = text; L[h] = hint
        st.append(dict(id=f's{i}', textKey=k, hintKeys=[h]))
    products = {a['resource']['id']: a['resource']['kind'] for a in golden if a['type'] == 'create'}
    inspection = next(iter(w))
    golden = golden + [dict(type='inspect', id=inspection)]
    optional = [obj(f'evidence-{n}', 'logging.evidenceFound', '收集至少一项世界状态证据（Inspect）', id=inspection)]
    constraints = list(constraints) + [obj(f'safe-{n}', 'iam.leastPrivilege', '权限无 tenancy / all-resources / manage 过度授权')]
    failures = list(failures) + [obj(f'broad-{n}', 'iam.hasBroadPermissions', '严重安全违例：请撤销过宽 Policy')]
    wid = f'oci-{n:03d}'; boss = n % 6 == 0
    world = dict(schemaVersion=1, levelId=wid, seed=n * 100 + 1, tick=tick_, region='ap-tokyo-1', resources=w,
                 principals=principals or [dict(id='commander'), dict(id='newcomer')], budgetCredits=budget, requestRate=rate)
    level = dict(schemaVersion=1, id=wid, chapter=(n - 1) // 6 + 1, order=n, titleKey=prefix + '.title',
                 scenario=dict(region='ap-tokyo-1', briefKey=prefix + '.brief', seed=n * 100 + 1), initialWorld=world,
                 allowedActions=ACTIONS, steps=st, objectives=goals, constraints=constraints, optionalObjectives=optional,
                 failureTriggers=failures, tutorialKeys=[prefix + '.tutorial'], optimalActionLimit=len(golden),
                 reward=dict(xp=500 if boss else min(480, 300 + (n - 13) * 12), maxStars=3, badgeId=badge),
                 dependsOn=[f'oci-{n - 1:03d}'], products=products)
    dump(C / f'chapters/ch{level["chapter"]:02d}/level-{n:03d}.json', level)
    dump(ROOT / f'core/testing/fixtures/level-{n:03d}.json', dict(levelId=wid, golden=golden, negative=negative))
    LEVELS.append(level)

def web_vm(**kw):
    p = dict(privateIp='10.10.1.10', publicIp='203.0.113.10', nsgs='web-nsg', listenPorts='22,443', sshKey='ops-key',
             shape='VM.Standard.E4.Flex.1', image='Oracle-Linux-9', logPath='', httpPaths='', dbEndpoint='', dbSecret='')
    p.update(kw); return r('web', 'COMPUTE', p, dict(subnet='web-sub'))
def running(id): return obj(f'{id}-running', 'compute.running', f'{id} RUNNING，且 Shape / Image / SSH 密钥别名齐全', id=id)
def ssh_ok(client, target, via, deny=False):
    who = '值班工程师' if client == 'operator' else '未登记来源'
    return obj(f'ssh-{client}-{target}-{via}-{"deny" if deny else "ok"}', 'ssh.denied' if deny else 'ssh.allowed',
               f'{who} {"无法" if deny else "可以"}经 {"公网直连" if via == "direct" else "Bastion " + via} SSH 到 {target}', client=client, target=target, via=via)
def reach(source, dest, port, deny=False):
    return obj(f'{source}-{dest}-{port}-{"deny" if deny else "ok"}', 'network.unreachable' if deny else 'network.reachable',
               f'{source} → {dest}:{port} {"被拒绝" if deny else "连通"}', source=source, destination=dest, port=port)
def private(ids): return obj('private-' + ids.replace(',', '-'), 'security.privateInstances', f'{ids} 保持私网且无 Public IP', ids=ids)

# ================= Chapter 3 计算引擎 =================
w = net(web='ingress|198.51.100.0/24|tcp|22|stateful;' + WEB_RULE)
add(13, '启动首台实例',
    '林岚把第一台 Web 服务器交给你。网络、Route 与 NSG 已就绪：选择教学 Shape 与 Image、放入 web-sub、绑定 SSH 密钥别名 ops-key，等待实例从 PROVISIONING 进入 RUNNING。',
    w, [running('web'), ssh_ok('operator', 'web', 'direct')],
    [create(web_vm()), tick(2)],
    [create(web_vm(sshKey='laptop-key')), tick(2)],
    ['在 web-sub 创建 Compute', '等待初始化并验证 SSH'],
    ['Kind=COMPUTE，ID=web，subnet=web-sub，privateIp=10.10.1.10，publicIp=203.0.113.10，nsgs=web-nsg，listenPorts=22,443，shape=VM.Standard.E4.Flex.1，image=Oracle-Linux-9，sshKey=ops-key。',
     '实例创建后为 PROVISIONING：在 Incident 或 Terminal 推进时间（advance），再用 ssh operator@web 验证。'],
    'Compute 生命周期：PROVISIONING → RUNNING ↔ STOPPED → TERMINATED。Shape 决定 OCPU/吞吐与成本，Image 决定操作系统；SSH 只接受公钥，本游戏只用密钥别名，绝不输入真实私钥。',
    constraints=[ssh_ok('intruder', 'web', 'direct', True)])

w = put(net(web=WEB_RULE), web_vm(publicIp='', sshKey='old-key', listenPorts='443', status='RUNNING'))
add(14, '不能 SSH',
    '佐藤美咲报告：ssh operator@web 一直超时。The Glitch 在交接中留下了多处问题。请沿 地址 → 路由 → NSG → 监听 → 密钥 的顺序逐层排查，并保证未登记来源仍被拒绝。',
    w, [ssh_ok('operator', 'web', 'direct'), ssh_ok('intruder', 'web', 'direct', True), reach('internet', 'web', 443)],
    [update('web', dict(publicIp='203.0.113.10')), update('web-nsg', dict(rules=WEB_RULE + ';ingress|198.51.100.0/24|tcp|22|stateful')),
     update('web', dict(listenPorts='443,22')), update('web', dict(sshKey='ops-key'))],
    [update('web', dict(publicIp='203.0.113.10')), update('web-nsg', dict(rules=WEB_RULE + ';ingress|0.0.0.0/0|tcp|22|stateful')),
     update('web', dict(listenPorts='443,22')), update('web', dict(sshKey='ops-key'))],
    ['恢复目标地址', '只为工作站网段开放 22', '启动 sshd 并修正授权密钥'],
    ['先执行 ssh operator@web 查看 blockedAt。web 需要 publicIp=203.0.113.10。',
     'web-nsg 追加 ingress|198.51.100.0/24|tcp|22|stateful；不要使用 0.0.0.0/0。',
     'listenPorts 加入 22（模拟 sshd 已启动）；sshKey 改为 ops-key（authorized_keys 别名）。'],
    'SSH 失败要分层定位：公网地址与 IGW 路由 → NSG 入站 22 → 实例是否监听 → authorized_keys。每次 ssh 都会给出 blockedAt。来源限制与密钥是两道独立防线。')

w = put(net(app='ingress|10.10.1.0/24|tcp|8080|stateful'),
        compute('app', 'app-sub', '10.10.2.10', 'app-nsg', '8080,22'))
add(15, '跳板救援',
    'App 位于私有子网，没有公网 IP。Ravi 禁止为了排障临时开放公网 SSH。用 Bastion 建立受控会话：只允许值班网段 198.51.100.0/24，目标端口 22。',
    w, [ssh_ok('operator', 'app', 'bastion'), ssh_ok('intruder', 'app', 'bastion', True), private('app')],
    [create(r('bastion', 'BASTION', dict(privateIp='10.10.2.5', allowedCidr='198.51.100.0/24'), dict(subnet='app-sub'))),
     create(r('bastion-ssh', 'BASTION_SESSION', dict(port=22, sshKey='ops-key', ttlTicks=60), dict(bastion='bastion', target='app'))),
     update('app-nsg', dict(rules='ingress|10.10.1.0/24|tcp|8080|stateful;ingress|10.10.2.5/32|tcp|22|stateful'))],
    [create(r('bastion', 'BASTION', dict(privateIp='10.10.2.5', allowedCidr='0.0.0.0/0'), dict(subnet='app-sub'))),
     create(r('bastion-ssh', 'BASTION_SESSION', dict(port=22, sshKey='ops-key', ttlTicks=60), dict(bastion='bastion', target='app'))),
     update('app-nsg', dict(rules='ingress|10.10.1.0/24|tcp|8080|stateful;ingress|10.10.2.5/32|tcp|22|stateful'))],
    ['创建 Bastion 并限制来源', '创建 SSH Session', '只允许 Bastion 访问 22'],
    ['Kind=BASTION，ID=bastion，subnet=app-sub，privateIp=10.10.2.5，allowedCidr=198.51.100.0/24。',
     'Kind=BASTION_SESSION，ID=bastion-ssh，bastion=bastion，target=app，port=22，sshKey=ops-key，ttlTicks=60。',
     'app-nsg 追加 ingress|10.10.2.5/32|tcp|22|stateful，然后 ssh --bastion bastion operator@app。'],
    'Bastion 是托管跳板：客户端先通过允许 CIDR 校验，再凭有时限的 Session 到达私网目标。目标实例无需 Public IP，NSG 只需放行 Bastion 私网地址。',
    constraints=[reach('internet', 'app', 22, True)])

w = put(net(web='egress|10.10.2.0/24|tcp|8080|stateful', app='ingress|10.10.1.0/24|tcp|8080|stateful'),
        compute('web', 'web-sub', '10.10.1.10', 'web-nsg', '443', public='203.0.113.10'),
        compute('app', 'app-sub', '10.10.2.10', 'app-nsg', '8080', logGb=46, rootGb=50, logPath='/var/log/app'))
add(16, '磁盘告急',
    '凌晨告警：app 根卷使用率超过 100%，日志写入失败，Web→App 请求开始报错。为日志准备独立 Block Volume，挂载后迁移日志目录。',
    w, [obj('app-disk', 'compute.diskHealthy', 'app 日志所在文件系统使用率 ≤ 90%', id='app'), reach('web', 'app', 8080)],
    [create(r('logs-vol', 'VOLUME', dict(sizeGb=100, mountPoint='/data', backupPolicy='none'), dict(attachedTo='app'))),
     update('app', dict(logPath='/data/logs'))],
    [create(r('logs-vol', 'VOLUME', dict(sizeGb=50, mountPoint='/data', backupPolicy='none'), dict(attachedTo='app'))),
     update('app', dict(logPath='/data/logs'))],
    ['附加并挂载 Block Volume', '迁移日志路径并检查容量'],
    ['先 df -h app 看根卷。Kind=VOLUME，ID=logs-vol，attachedTo=app，mountPoint=/data，sizeGb 至少能容纳 46GB 日志且留 10% 余量。',
     'app 的 logPath 改为 /data/logs，然后 df -h app、curl --from web http://app:8080 验证。'],
    '根卷 = 系统 10GB + 根路径上的日志。Block Volume 需 attach 到实例并挂载到目录，写入该目录的数据才会落在卷上；只 attach 不改路径，日志仍写满根卷。教学最小卷容量 50GB。')

w = put(net(app='ingress|10.10.1.0/24|tcp|8080|stateful'),
        compute('app', 'app-sub', '10.10.2.10', 'app-nsg', '8080'),
        compute('test-vm', 'app-sub', '10.10.2.20', 'app-nsg', '8080', shape='VM.Standard.E4.Flex.1'),
        r('orders-vol', 'VOLUME', dict(sizeGb=200, mountPoint='/data', backupPolicy='none', checksum='sha256:7f3a91', dataVersion='v42'), dict(attachedTo='app')))
add(17, '备份前夜',
    '下周订单系统迁移。林岚发现 orders-vol 从未备份过。设置备份策略、立即做一次完整备份，并恢复到隔离的 test-vm 校验数据，而不是覆盖生产卷。',
    w, [obj('orders-protected', 'storage.protected', 'orders-vol 有备份策略且至少一份备份', id='orders-vol'),
        obj('restore-verified', 'storage.backupRestorable', 'restore-test 由 orders-vol 的备份恢复，checksum / 数据版本一致，且未挂回生产实例', source='orders-vol', restored='restore-test')],
    [update('orders-vol', dict(backupPolicy='silver')), create(r('orders-bk', 'VOLUME_BACKUP', dict(type='FULL'), dict(volume='orders-vol'))),
     create(r('restore-test', 'VOLUME', dict(sizeGb=200, mountPoint='/restore', backupPolicy='none'), dict(sourceBackup='orders-bk', attachedTo='test-vm')))],
    [update('orders-vol', dict(backupPolicy='silver')), create(r('orders-bk', 'VOLUME_BACKUP', dict(type='FULL'), dict(volume='orders-vol'))),
     create(r('restore-test', 'VOLUME', dict(sizeGb=200, mountPoint='/restore', backupPolicy='none'), dict(sourceBackup='orders-bk', attachedTo='app')))],
    ['设置备份策略', '执行一次完整备份', '恢复到隔离测试实例'],
    ['orders-vol 的 backupPolicy 设为 bronze / silver / gold 之一。',
     'Kind=VOLUME_BACKUP，ID=orders-bk，volume=orders-vol，type=FULL。checksum 由引擎从卷复制，不可手填。',
     'Kind=VOLUME，ID=restore-test，sourceBackup=orders-bk，sizeGb≥200，attachedTo=test-vm，mountPoint=/restore。'],
    '“有备份”不等于“能恢复”。恢复演练应在隔离环境进行，并用 checksum 与数据版本证明数据一致。备份、恢复来源在创建后不可修改。')

w = put(net(web=WEB_RULE + ';egress|10.10.2.0/24|tcp|8080|stateful', app='ingress|10.10.1.0/24|tcp|8080|stateful'),
        compute('web', 'web-sub', '10.10.1.10', 'web-nsg', '443', public='203.0.113.10'),
        compute('app', 'app-sub', '10.10.2.10', 'app-nsg', '8080,22', status='STOPPED', logGb=46, rootGb=50, logPath='/data/logs'),
        r('data-vol', 'VOLUME', dict(sizeGb=200, mountPoint='/data', backupPolicy='silver', checksum='sha256:c0ffee', dataVersion='v77')),
        r('data-bk-old', 'VOLUME_BACKUP', dict(type='FULL', checksum='sha256:0ld601', dataVersion='v60', sizeGb=200, createdTick=3), dict(volume='data-vol')),
        r('bastion', 'BASTION', dict(privateIp='10.10.2.5', allowedCidr='198.51.100.0/24'), dict(subnet='app-sub')),
        r('bastion-ssh', 'BASTION_SESSION', dict(port=22, sshKey='ops-key', ttlTicks=180, expiresTick=500), dict(bastion='bastion', target='app')))
add(18, '服务器风暴·Boss',
    'The Glitch 发动“服务器风暴”：app 意外停机，维护脚本把 data-vol 分离，安全组变更又挡住了运维 SSH。按 卷 → 网络 → 服务 的顺序恢复，数据必须保留，不得给 app 加公网 IP。',
    w, [running('app'), obj('storm-disk', 'compute.diskHealthy', 'app 日志写入恢复', id='app'),
        obj('storm-volume', 'resource.referenceEquals', '原数据卷 data-vol 重新挂回 app（数据未丢失）', id='data-vol', key='attachedTo', value='app'),
        ssh_ok('operator', 'app', 'bastion'), reach('web', 'app', 8080), private('app')],
    [update('data-vol', refs=dict(attachedTo='app')), update('app-nsg', dict(rules='ingress|10.10.1.0/24|tcp|8080|stateful;ingress|10.10.2.5/32|tcp|22|stateful')), power('app', 'START')],
    [create(r('new-data', 'VOLUME', dict(sizeGb=200, mountPoint='/data', backupPolicy='none'), dict(sourceBackup='data-bk-old', attachedTo='app'))),
     update('app-nsg', dict(rules='ingress|10.10.1.0/24|tcp|8080|stateful;ingress|10.10.2.5/32|tcp|22|stateful')), power('app', 'START')],
    ['检查卷挂载', '修复运维通道', '启动实例并做端到端验证'],
    ['inspect data-vol：它仍有最新 checksum，只是 attachedTo 为空。重新 attach 到 app，不要用旧备份恢复。',
     'app-nsg 追加 ingress|10.10.2.5/32|tcp|22|stateful（Bastion 私网地址）。',
     'Console 选择 app → 启动（START），再 ssh --bastion bastion operator@app 与 curl --from web http://app:8080。'],
    '多因故障先止血再恢复：确认数据位置（卷）→ 管理通道（网络）→ 进程（服务）。旧备份会造成数据回退，只有在原卷不可用时才考虑恢复。',
    tick_=10, badge='server-medic')

# ================= Chapter 4 数据守护 =================
WORKLOADS = [dict(id='commander'), dict(id='newcomer'), dict(id='workload', instanceId='worker'), dict(id='other-workload', instanceId='other')]
def workload_net():
    return put(net(), compute('worker', 'app-sub', '10.10.2.10', '', '8080'), compute('other', 'app-sub', '10.10.2.11', '', '8080'),
               r('workloads', 'DYNAMIC_GROUP', dict(matchInstance='worker', matchCompartment='dev')))
w = put(workload_net(), r('assets-read', 'POLICY', dict(verb='read', resourceType='BUCKET', scope='dev', targetId=''), dict(group='workloads')))
add(19, '第一座存储仓',
    '星港市官网的静态资源需要一个对象存储仓。worker 通过 Instance Principal 已获准读取 dev 中的 Bucket。创建 Bucket 并上传 Logo，保持默认私有。',
    w, [obj('assets-exists', 'resource.exists', '建立 assets (BUCKET)', id='assets', kind='BUCKET'),
        obj('logo-exists', 'resource.exists', '上传对象 static/logo.svg', id='logo', kind='OBJECT'),
        obj('assets-restricted', 'storage.objectRestricted', '匿名读取被拒绝，worker 可读取 assets', bucket='assets', principal='workload'),
        obj('assets-other', 'iam.denies', '其他实例不能读取 assets', principal='other-workload', verb='read', kind='BUCKET', compartment='dev', id='assets')],
    [create(r('assets', 'BUCKET', dict(visibility='private', lifecycle=''))), create(r('logo', 'OBJECT', dict(name='static/logo.svg', sizeMb=2), dict(bucket='assets')))],
    [create(r('assets', 'BUCKET', dict(visibility='public', lifecycle=''))), create(r('logo', 'OBJECT', dict(name='static/logo.svg', sizeMb=2), dict(bucket='assets')))],
    ['创建私有 Bucket', '上传对象并验证访问'],
    ['Kind=BUCKET，ID=assets，visibility=private（默认值）。',
     'Kind=OBJECT，ID=logo，bucket=assets，name=static/logo.svg，sizeMb=2。'],
    'Object Storage 以 Bucket 组织对象。新 Bucket 默认私有：匿名请求被拒绝，授权主体依 IAM Policy 读取。公开访问应是明确决定，而不是默认。',
    principals=WORKLOADS)

w = put(workload_net(), r('reports', 'BUCKET', dict(visibility='public', lifecycle='')),
        r('q1', 'OBJECT', dict(name='reports/q1.pdf', sizeMb=12, tier='STANDARD', createdTick=0), dict(bucket='reports')),
        r('reports-read', 'POLICY', dict(verb='inspect', resourceType='BUCKET', scope='dev', targetId='reports'), dict(group='workloads')))
add(20, '权限之锁',
    'Ravi 的外部扫描发现 reports 桶可被匿名读取，同时 worker 自己却读不到报表。修正 Bucket 可见性与 Policy 动词，验证匿名拒绝、应用允许。',
    w, [obj('reports-restricted', 'storage.objectRestricted', '匿名读取被拒绝，worker 可读取 reports', bucket='reports', principal='workload'),
        obj('reports-other', 'iam.denies', '其他实例不能读取 reports', principal='other-workload', verb='read', kind='BUCKET', compartment='dev', id='reports')],
    [update('reports', dict(visibility='private')), update('reports-read', dict(verb='read'))],
    [update('reports', dict(visibility='private')), update('reports-read', dict(verb='manage'))],
    ['关闭公共访问', '修正读取动词'],
    ['reports 的 visibility 改为 private。', 'reports-read 的 verb 由 inspect 改为 read：inspect 只能列出，读取对象内容需要 read。不要用 manage。'],
    'inspect 只能看到元数据；read 才能读取对象内容；manage 包含删除和改策略。匿名访问与 IAM 授权是两条独立判断。',
    principals=WORKLOADS)

w = put(net(), r('logs-bkt', 'BUCKET', dict(visibility='private', lifecycle='')),
        r('old-log', 'OBJECT', dict(name='logs/2030-01.log', sizeMb=40000, tier='STANDARD', createdTick=0), dict(bucket='logs-bkt')),
        r('mid-log', 'OBJECT', dict(name='logs/2030-03.log', sizeMb=30000, tier='STANDARD', createdTick=50), dict(bucket='logs-bkt')),
        r('new-log', 'OBJECT', dict(name='logs/2030-04.log', sizeMb=5000, tier='STANDARD', createdTick=95), dict(bucket='logs-bkt')),
        r('report', 'OBJECT', dict(name='reports/annual-2029.pdf', sizeMb=500, tier='STANDARD', createdTick=0), dict(bucket='logs-bkt')))
add(21, '生命周期工程',
    '财务预警：旧日志让存储成本远超 60 Credits 预算。为 logs/ 前缀设定生命周期：30 天后归档、90 天后删除；年度报告必须保留。设置后快进一天让生命周期作业运行。',
    w, [obj('logs-policy', 'storage.lifecycleConfigured', 'logs/ 生命周期按 30 天归档、90 天删除，且无冲突规则', bucket='logs-bkt', prefix='logs/', archiveDays=30, deleteDays=90), obj('old-deleted', 'resource.absent', '超过 90 天的 logs/2030-01.log 被删除', id='old-log'),
        obj('mid-archived', 'storage.objectTier', '30–90 天的 logs/2030-03.log 转为 ARCHIVE', id='mid-log', tier='ARCHIVE'),
        obj('new-standard', 'storage.objectTier', '新日志 logs/2030-04.log 保持 STANDARD', id='new-log', tier='STANDARD'),
        obj('lifecycle-budget', 'cost.underBudget', '模拟月度成本不超过预算')],
    [update('logs-bkt', dict(lifecycle='logs/|archive:30|delete:90')), tick(1)],
    [update('logs-bkt', dict(lifecycle='logs/|archive:30|delete:40')), tick(1)],
    ['编写生命周期规则', '快进时间并核对结果'],
    ['logs-bkt 的 lifecycle 填 logs/|archive:30|delete:90（前缀|动作:天数）。',
     '生命周期每个模拟日运行一次：推进 1 tick，然后 oci os object list 检查 tier。'],
    '生命周期规则按对象前缀与年龄执行归档/删除。没有前缀的规则会误伤业务数据。本关 1 tick = 1 个模拟日，价格为教学 Credits。',
    failures=[obj('report-deleted', 'resource.absent', '严重违例：年度报告被生命周期误删', id='report')], tick_=100, budget=60.0)

w = put(net(app='ingress|10.10.1.0/24|tcp|8080|stateful'), compute('app', 'app-sub', '10.10.2.10', 'app-nsg', '8080', dbEndpoint='', dbSecret=''))
add(22, '数据库上线',
    '订单业务需要数据库。在私有 db-sub 中创建训练模式数据库，管理员凭证只能引用 Vault 中的 vault:orders-admin，并打通 App→DB 1521，同时保证公网无法直连。',
    w, [obj('db-exists', 'resource.exists', '建立 orders-db (DATABASE)', id='orders-db', kind='DATABASE'),
        obj('app-db', 'db.connected', 'app 使用正确凭证引用连接 orders-db', app='app'), reach('internet', 'orders-db', 1521, True)],
    [create(r('orders-db', 'DATABASE', dict(privateIp='10.10.3.20', nsgs='db-nsg', port=1521, adminSecret='vault:orders-admin', autoBackup='true', retentionDays=30), dict(subnet='db-sub'))),
     update('db-nsg', dict(rules='ingress|10.10.2.0/24|tcp|1521|stateful')),
     update('app-nsg', dict(rules='ingress|10.10.1.0/24|tcp|8080|stateful;egress|10.10.3.0/24|tcp|1521|stateful')),
     update('app', dict(dbEndpoint='orders-db', dbSecret='vault:orders-admin')), tick(5)],
    [create(r('orders-db', 'DATABASE', dict(privateIp='10.10.3.20', nsgs='db-nsg', port=1521, adminSecret='vault:orders-admin', autoBackup='true', retentionDays=30), dict(subnet='db-sub'))),
     update('db-nsg', dict(rules='ingress|10.10.2.0/24|tcp|1521|stateful')),
     update('app', dict(dbEndpoint='orders-db', dbSecret='vault:orders-admin')), tick(5)],
    ['创建私网数据库', '打通 App→DB 安全规则', '配置应用连接并等待 AVAILABLE'],
    ['Kind=DATABASE，ID=orders-db，subnet=db-sub，privateIp=10.10.3.20，nsgs=db-nsg，port=1521，adminSecret=vault:orders-admin，autoBackup=true，retentionDays=30。',
     'db-nsg：ingress|10.10.2.0/24|tcp|1521|stateful；app-nsg 追加 egress|10.10.3.0/24|tcp|1521|stateful。',
     'app 的 dbEndpoint=orders-db，dbSecret=vault:orders-admin。数据库需约 5 tick 完成 PROVISIONING。'],
    '训练模式数据库简化了 OCI DB System / Autonomous Database：只模拟私网端点、端口、凭证引用、备份与数据校验。真实系统不应把密码写进配置，本游戏也拒绝明文。')

DBNET = dict(app='ingress|10.10.1.0/24|tcp|8080|stateful;egress|10.10.3.0/24|tcp|1521|stateful', db='ingress|10.10.2.0/24|tcp|1521|stateful')
def orders_db(**kw):
    p = dict(status='AVAILABLE', privateIp='10.10.3.20', nsgs='db-nsg', port=1521, adminSecret='vault:orders-admin', autoBackup='true', retentionDays=30)
    p.update(kw); return r('orders-db', 'DATABASE', p, dict(subnet='db-sub'))
def db_backup(id, t, checksum, records):
    return r(id, 'DB_BACKUP', dict(type='FULL', checksum=checksum, records=records, createdTick=t), dict(database='orders-db'))
w = put(net(**DBNET), compute('app', 'app-sub', '10.10.2.10', 'app-nsg', '8080', dbEndpoint='orders-db', dbSecret='vault:orders-admin'),
        orders_db(checksum='sha256:bad1200', records=1200, incidentTick=75),
        db_backup('bk-40', 40, 'sha256:c4200', 4200), db_backup('bk-70', 70, 'sha256:c5000', 5000), db_backup('bk-78', 78, 'sha256:bad1200', 1200))
add(23, '丢失的订单',
    'tick 75，一个调试脚本误删了 3800 条订单。生产库仍在运行但数据已残缺。选择事故前最近的恢复点，恢复到新库，再把 app 切换过去；原库保留用于取证。RPO 不得超过 10 tick。',
    w, [obj('orders-data', 'db.dataChecksumEquals', 'app 连接的数据库 checksum = sha256:c5000（事故前完整数据）', app='app', checksum='sha256:c5000'),
        obj('orders-rpo', 'db.rpoWithin', '恢复点早于事故且 RPO ≤ 10 tick', app='app', source='orders-db', maxTicks=10),
        obj('orders-connected', 'db.connected', 'app 能连接恢复后的数据库', app='app')],
    [create(r('orders-restore', 'DATABASE', dict(privateIp='10.10.3.21', nsgs='db-nsg', port=1521, adminSecret='vault:orders-admin', autoBackup='true', retentionDays=30), dict(subnet='db-sub', sourceBackup='bk-70'))),
     tick(5), update('app', dict(dbEndpoint='orders-restore'))],
    [create(r('orders-restore', 'DATABASE', dict(privateIp='10.10.3.21', nsgs='db-nsg', port=1521, adminSecret='vault:orders-admin', autoBackup='true', retentionDays=30), dict(subnet='db-sub', sourceBackup='bk-78'))),
     tick(5), update('app', dict(dbEndpoint='orders-restore'))],
    ['找出正确恢复点', '恢复到隔离新库', '切换应用连接'],
    ['oci db backup list：事故在 tick 75，bk-78 已包含删除后的数据，bk-40 太旧。',
     'Kind=DATABASE，ID=orders-restore，sourceBackup=bk-70，subnet=db-sub，privateIp=10.10.3.21，nsgs=db-nsg，adminSecret=vault:orders-admin。等待 AVAILABLE。',
     '确认 orders-restore AVAILABLE 后，把 app 的 dbEndpoint 改为 orders-restore。'],
    'RPO（恢复点目标）衡量可接受的数据丢失时间。恢复到隔离新库、验证后再切换，比直接覆盖生产更安全，也保留了取证现场。',
    constraints=[obj('orders-kept', 'resource.exists', '保留原 orders-db 用于取证', id='orders-db', kind='DATABASE')], tick_=80)

w = put(net(**DBNET), compute('app', 'app-sub', '10.10.2.10', 'app-nsg', '8080', dbEndpoint='orders-db', dbSecret='vault:orders-old'),
        orders_db(adminSecret='vault:orders-2030', autoBackup='false', retentionDays=1, checksum='sha256:d9000', records=9000),
        db_backup('bk-10', 10, 'sha256:c3000', 3000),
        r('exports', 'BUCKET', dict(visibility='public', lifecycle='')),
        r('export-csv', 'OBJECT', dict(name='exports/customers.csv', sizeMb=80, tier='STANDARD', createdTick=150), dict(bucket='exports')),
        r('workloads', 'DYNAMIC_GROUP', dict(matchInstance='app', matchCompartment='dev')),
        r('exports-read', 'POLICY', dict(verb='read', resourceType='BUCKET', scope='dev', targetId='exports'), dict(group='workloads')))
add(24, '数据保卫战·Boss',
    '三线告急：客户导出桶被公开；凭证轮换后 app 仍引用旧 Secret，订单库连接失败；数据库自动备份被关闭，最新恢复点停留在 190 tick 前。逐一修复，并让三个业务探针全部通过。',
    w, [obj('exports-restricted', 'storage.objectRestricted', '导出桶匿名拒绝，app 实例可读', bucket='exports', principal='workload'),
        obj('boss-db-connected', 'db.connected', 'app 使用当前凭证引用连接 orders-db', app='app'),
        obj('boss-backup', 'db.backupFresh', '自动备份开启、保留 ≥7 天，且有 24 tick 内的恢复点', id='orders-db', maxAge=24, minRetention=7)],
    [update('exports', dict(visibility='private')), update('app', dict(dbSecret='vault:orders-2030')),
     update('orders-db', dict(autoBackup='true', retentionDays=30)), create(r('orders-ondemand', 'DB_BACKUP', dict(type='FULL'), dict(database='orders-db')))],
    [update('exports', dict(visibility='private')), update('app', dict(dbSecret='vault:orders-2030')), update('orders-db', dict(autoBackup='true', retentionDays=30))],
    ['隔离公开数据', '修复应用凭证引用', '重建备份策略与新恢复点'],
    ['exports 的 visibility 改为 private；app 的 Instance Principal 仍有 read 授权。',
     'inspect orders-db 查看 adminSecret 引用，把 app 的 dbSecret 改成相同的 vault 引用。',
     'orders-db：autoBackup=true，retentionDays≥7；再创建 Kind=DB_BACKUP，ID=orders-ondemand，database=orders-db。'],
    '数据安全有三面：访问控制（谁能读）、可用性（应用能否连接）、可恢复性（备份是否新鲜）。只修一面，业务仍不安全。',
    principals=[dict(id='commander'), dict(id='newcomer'), dict(id='workload', instanceId='app')], tick_=200, badge='data-guardian')

# ================= Chapter 5 业务门户 =================
LB5 = 'ingress|0.0.0.0/0|tcp|80|stateful;egress|10.10.1.0/24|tcp|8080|stateful'
WEB5 = 'ingress|10.10.1.0/24|tcp|8080|stateful'
def backend(id, ip, shape='VM.Standard.E4.Flex.2', **kw):
    p = dict(status='RUNNING', privateIp=ip, publicIp='', nsgs='web-nsg', listenPorts='8080', sshKey='ops-key', shape=shape, image='Oracle-Linux-9', httpPaths='/,/health')
    p.update(kw); return r(id, 'COMPUTE', p, dict(subnet='web-sub'))
def new_backend(id, ip, shape='VM.Standard.E4.Flex.2'):
    x = backend(id, ip, shape); x['properties'].pop('status'); x['properties'].update(logPath='', dbEndpoint='', dbSecret=''); return x
def lb(**kw):
    p = dict(privateIp='10.10.1.50', publicIp='203.0.113.50', nsgs='lb-nsg', listenPorts='80', backends='web1:8080,web2:8080', healthPath='/health', healthTimeoutMs=3000, redirectHttp='false')
    refs = kw.pop('refs', {}); p.update(kw); return r('lb', 'LOAD_BALANCER', p, dict(subnet='web-sub', **refs))
def zone(records): return r('starport-zone', 'DNS_ZONE', dict(domain='starport.example', records=records))
def cert(id, cn='www.starport.example', valid=90, issued=None):
    p = dict(commonName=cn, validTicks=valid)
    if issued is not None: p.update(issuedTick=issued, notAfterTick=issued + valid)
    return r(id, 'CERTIFICATE', p)
URL = 'https://www.starport.example/'

w = put(net(web=WEB5 + ';ingress|0.0.0.0/0|tcp|8080|stateful', lb=LB5), backend('web1', '10.10.1.10', publicIp='203.0.113.10'))
add(25, '分担流量',
    '开业促销：900 req/s 全部打在 web1 一台实例上，利用率 150%。部署第二台后端 web2，并在公有子网创建 Load Balancer，用健康检查把流量平均分给两台。',
    w, [obj('lb-two-healthy', 'lb.backendsHealthy', 'lb 至少 2 个后端且全部健康', id='lb', min=2),
        obj('lb-slo', 'slo.meetsTarget', '成功率 ≥ 99.9% 且延迟 ≤ 200ms', id='lb', minSuccessPct=99.9, maxLatencyMs=200), reach('internet', 'lb', 80)],
    [create(new_backend('web2', '10.10.1.11')), create(lb(refs={})), tick(2)],
    [create(new_backend('web2', '10.10.1.11')), create(lb(backends='web1:8080')), tick(2)],
    ['部署第二台后端', '创建 Load Balancer、Backend Set 与健康检查'],
    ['Kind=COMPUTE，ID=web2，subnet=web-sub，privateIp=10.10.1.11，nsgs=web-nsg，listenPorts=8080，httpPaths=/,/health，shape=VM.Standard.E4.Flex.2，image=Oracle-Linux-9，sshKey=ops-key。',
     'Kind=LOAD_BALANCER，ID=lb，subnet=web-sub，privateIp=10.10.1.50，publicIp=203.0.113.50，nsgs=lb-nsg，listenPorts=80，backends=web1:8080,web2:8080，healthPath=/health。'],
    'Load Balancer = Listener（入口端口）+ Backend Set（后端与端口）+ Health Check（路径/超时）。只有健康后端接收流量；容量按教学 Shape 吞吐计算，延迟随利用率上升。',
    rate=900)

w = put(net(web=WEB5, lb=LB5), backend('web1', '10.10.1.10'), backend('web2', '10.10.1.11', listenPorts=''),
        lb(backends='web1:8080,web2:8081', healthPath='/healthz'))
add(26, '消失的后端',
    'Load Balancer 显示两个后端都是 CRITICAL。昨晚的发布改了健康检查路径，web2 的后端端口也被写错，而且 web2 上的服务进程没有启动。',
    w, [obj('lb26-healthy', 'lb.backendsHealthy', 'Backend Set 中 2 个后端全部健康', id='lb', min=2), reach('internet', 'lb', 80)],
    [update('lb', dict(healthPath='/health')), update('lb', dict(backends='web1:8080,web2:8080')), update('web2', dict(listenPorts='8080'))],
    [update('lb', dict(healthPath='/health')), update('lb', dict(backends='web1:8080,web2:8080'))],
    ['读取后端健康原因', '修正健康检查路径与端口', '恢复服务进程'],
    ['oci lb backend-health lb 会逐个给出 HTTP 404 / timeout / listener 原因。',
     '应用暴露的是 /health；后端端口应为 8080。',
     'web2 的 listenPorts 为空表示进程未监听：设为 8080（模拟 systemctl start app）。'],
    '后端健康检查依次经过：网络可达（NSG/路由）→ 端口监听 → HTTP 路径返回 200 → 响应时间不超过超时阈值。任何一层失败都会把后端摘除。',
    rate=600)

w = put(net(web=WEB5, lb=LB5), backend('web1', '10.10.1.10'), backend('web2', '10.10.1.11'), lb())
add(27, '域名在哪里',
    '客户只能用 203.0.113.50 访问门户。为 starport.example 建立 DNS Zone：www 指向 Load Balancer 的公网地址，api 作为 www 的别名。',
    w, [obj('www-resolves', 'dns.resolves', 'www.starport.example 解析到 lb 公网 IP', name='www.starport.example', target='lb'),
        obj('api-resolves', 'dns.resolves', 'api.starport.example 经 CNAME 解析到 lb', name='api.starport.example', target='lb')],
    [create(zone('www|A|203.0.113.50|300;api|CNAME|www.starport.example|300'))],
    [create(zone('www|A|10.10.1.50|300;api|CNAME|www.starport.example|300'))],
    ['创建 DNS Zone', '用 dig 验证 A 与 CNAME'],
    ['Kind=DNS_ZONE，ID=starport-zone，domain=starport.example，records=www|A|203.0.113.50|300;api|CNAME|www.starport.example|300。',
     'dig www.starport.example 与 dig api.starport.example。A 记录必须是公网地址，10.x 是私网地址。'],
    'DNS Zone 是域名的权威数据：A 记录把名字映射到 IPv4，CNAME 指向另一个名字。教学解析器不模拟缓存与 TTL 过期，仅显示 TTL。',
    rate=600)

w = put(net(web=WEB5, lb=LB5), backend('web1', '10.10.1.10'), backend('web2', '10.10.1.11'), lb(),
        zone('www|A|203.0.113.50|300'))
add(28, '加密的入口',
    'Ravi 指出门户仍在明文 HTTP 上传输登录信息。签发 www.starport.example 证书，增加 HTTPS 443 Listener，并把 HTTP 重定向到 HTTPS。',
    w, [obj('tls-valid', 'tls.validHostname', 'www.starport.example 的 TLS 模拟握手成功', id='lb', host='www.starport.example'),
        obj('http-redirect', 'lb.httpRedirect', 'HTTP 80 自动 301 重定向到 HTTPS', id='lb'),
        obj('https-e2e', 'portal.endToEnd', 'https://www.starport.example/ 端到端 200，且 2 个健康后端', url=URL, minBackends=2)],
    [create(cert('www-cert')), update('lb', dict(listenPorts='80,443', redirectHttp='true'), dict(certificate='www-cert')),
     update('lb-nsg', dict(rules=LB5 + ';ingress|0.0.0.0/0|tcp|443|stateful'))],
    [create(cert('www-cert', 'shop.starport.example')), update('lb', dict(listenPorts='80,443', redirectHttp='true'), dict(certificate='www-cert')),
     update('lb-nsg', dict(rules=LB5 + ';ingress|0.0.0.0/0|tcp|443|stateful'))],
    ['签发证书', '添加 HTTPS Listener 与重定向', '放行 443 并端到端验证'],
    ['Kind=CERTIFICATE，ID=www-cert，commonName=www.starport.example（或 *.starport.example），validTicks=90。',
     'lb：listenPorts=80,443，certificate=www-cert，redirectHttp=true。',
     'lb-nsg 追加 ingress|0.0.0.0/0|tcp|443|stateful，然后 curl https://www.starport.example/。'],
    'TLS 校验：证书在有效期内、名称与 Host 匹配（通配符只匹配一级）。本游戏不生成密钥、不做真实加密，只模拟握手结果。',
    rate=600)

TLS_LB = dict(listenPorts='80,443', redirectHttp='true', refs=dict(certificate='www-cert'))
w = put(net(web=WEB5, lb=LB5 + ';ingress|0.0.0.0/0|tcp|443|stateful'), backend('web1', '10.10.1.10'), backend('web2', '10.10.1.11'),
        cert('www-cert', issued=0), lb(healthTimeoutMs=100, **TLS_LB), zone('www|A|203.0.113.50|300'))
add(29, '突发大流量',
    '直播带货开始，请求骤增到 1500 req/s。两台后端过载，响应超过 100ms 的健康检查超时，被 LB 全部摘除。在 120 Credits 预算内扩容并调优健康阈值。',
    w, [obj('surge-slo', 'slo.meetsTarget', '成功率 ≥ 99.9% 且延迟 ≤ 200ms', id='lb', minSuccessPct=99.9, maxLatencyMs=200),
        obj('surge-backends', 'lb.backendsHealthy', '至少 3 个后端且全部健康', id='lb', min=3),
        obj('surge-budget', 'cost.underBudget', '模拟月度成本 ≤ 120 Credits')],
    [create(new_backend('web3', '10.10.1.12')), create(new_backend('web4', '10.10.1.13', 'VM.Standard.E4.Flex.1')),
     update('lb', dict(backends='web1:8080,web2:8080,web3:8080,web4:8080')), update('lb', dict(healthTimeoutMs=500)), tick(2)],
    [create(new_backend('web3', '10.10.1.12', 'VM.Standard.E4.Flex.4')),
     update('lb', dict(backends='web1:8080,web2:8080,web3:8080')), update('lb', dict(healthTimeoutMs=500)), tick(2)],
    ['估算所需容量与成本', '扩容后端', '调优健康检查超时'],
    ['目标利用率 ≤ 80%：1500 ÷ 0.8 ≈ 1875 req/s。Flex.1=300/10cr，Flex.2=600/25cr，Flex.4=1200/60cr，LB=15cr。',
     '新增 web3（Flex.2，10.10.1.12）与 web4（Flex.1，10.10.1.13），加入 lb 的 backends。',
     '100ms 超时低于正常负载延迟，健康检查会误摘后端：把 healthTimeoutMs 调到 500。'],
    '容量规划要同时看性能与成本：最大的 Shape 不一定最优。过严的健康检查超时会在高负载时摘除全部后端，引发雪崩。',
    rate=1500, budget=120.0)

w = put(net(web=WEB5, lb=LB5 + ';ingress|0.0.0.0/0|tcp|443|stateful'), backend('web1', '10.10.1.10'), backend('web2', '10.10.1.11', httpPaths='/'),
        cert('old-cert', issued=10), lb(listenPorts='80,443', redirectHttp='true', refs=dict(certificate='old-cert')),
        zone('www|A|203.0.113.60|300'))
add(30, '门户停摆·Boss',
    '凌晨 3 点门户全面停摆。The Glitch 留下三重陷阱：DNS 仍指向已下线的旧 LB 地址，证书在 tick 100 过期，昨晚发布后 web2 丢失健康检查路径。按 解析 → 证书 → 健康检查 恢复，并保持双后端冗余。',
    w, [obj('boss-dns', 'dns.resolves', 'www.starport.example 解析到当前 lb', name='www.starport.example', target='lb'),
        obj('boss-tls', 'tls.validHostname', '证书有效且匹配 www.starport.example', id='lb', host='www.starport.example'),
        obj('boss-backends', 'lb.backendsHealthy', '2 个后端全部健康', id='lb', min=2),
        obj('boss-e2e', 'portal.endToEnd', 'https://www.starport.example/ 端到端恢复且冗余', url=URL, minBackends=2)],
    [update('starport-zone', dict(records='www|A|203.0.113.50|300')), create(cert('www-2031', valid=365)),
     update('lb', refs=dict(certificate='www-2031')), update('web2', dict(httpPaths='/,/health'))],
    [update('starport-zone', dict(records='www|A|203.0.113.50|300')), create(cert('www-2031', valid=365)), update('lb', refs=dict(certificate='www-2031'))],
    ['修复 DNS 解析', '更换过期证书', '恢复后端健康并端到端验证'],
    ['dig www.starport.example 返回 203.0.113.60，而 lb 的公网地址是 203.0.113.50：更新 starport-zone 记录。',
     '证书不可修改：新建 Kind=CERTIFICATE，ID=www-2031，validTicks=365，再把 lb 的 certificate 改为 www-2031。',
     'oci lb backend-health lb：web2 HTTP 404。web2 的 httpPaths 恢复为 /,/health，最后 curl https://www.starport.example/。'],
    '端到端排查沿用户路径进行：DNS → 公网入口 → TLS → 后端健康。每层修复后都重新验证，避免同时改多处而失去因果判断。',
    tick_=120, rate=900, badge='portal-keeper')

# ---------- locale / index / schema ----------
loc_path = C / 'locales/zh-CN.json'
strings = json.loads(loc_path.read_text(encoding='utf-8'))
strings.update(L)
dump(loc_path, strings)
files = sorted(str(p.relative_to(C)).replace('\\', '/') for p in (C / 'chapters').glob('ch*/level-*.json'))
dump(C / 'index.json', files)

schema_path = C / 'schemas/level.schema.json'
schema = json.loads(schema_path.read_text(encoding='utf-8'))
schema['$defs']['resource']['properties']['kind'] = {'enum': KINDS}
schema['properties']['products']['additionalProperties'] = {'enum': KINDS}
schema['properties']['allowedActions']['items'] = {'enum': ACTIONS}
schema['properties']['initialWorld']['properties']['requestRate'] = {'type': 'integer', 'minimum': 0}
def object_(properties, required=None):
    return dict(type='object', additionalProperties=False, properties=properties, required=required or list(properties))
string = {'type': 'string'}
schema['$defs']['objective'] = {'oneOf': [object_(dict(id=string, predicate={'const': name}, params=object_({p: string for p in sorted(keys)}), messageKey=string))
                                          for name, keys in sorted(PREDICATES.items())]}
dump(schema_path, schema)
print(f'Generated {len(LEVELS)} Phase 2 levels; index now lists {len(files)} playable levels.')
