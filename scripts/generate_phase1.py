import json, pathlib, re
ROOT=pathlib.Path(__file__).resolve().parents[1]
C=ROOT/'content'; L={}; levels=[]
def dump(path,data):
 path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def r(id,kind,p=None,refs=None,comp='dev',region='ap-tokyo-1'):
 return dict(id=id,kind=kind,compartment=comp,region=region,properties=p or {},references=refs or {})
def base(): return {x:r(x,'COMPARTMENT',dict(name=x,parent='root'),comp='root') for x in ['dev','prod']}
def create(x): return dict(type='create',resource=x)
def update(id,p=None,refs=None): return dict(type='update',id=id,patch=p or {},references=refs or {})
def region(): return dict(type='select_region',value='ap-tokyo-1')
def obj(oid,predicate,message,**params):
 key='goals.'+oid; L[key]=message; return dict(id=oid,predicate=predicate,params=params,messageKey=key)
def prop(id,key,value,msg=None): return obj(id+'-'+key,'resource.propertyEquals',msg or f'{id}: {key} = {value}',id=id,key=key,value=value)
def exists(id,kind): return obj(id+'-exists','resource.exists',f'建立 {id} ({kind})',id=id,kind=kind)
def permits(id,principal='newcomer',verb='read',kind='COMPUTE',compartment='dev',target='sample',deny=False):
 return obj(id,'iam.denies' if deny else 'iam.permits',f'{principal} {"禁止" if deny else "允许"} {verb} {compartment}/{target}',principal=principal,verb=verb,kind=kind,compartment=compartment,id=target)
def reach(source,dest,port,deny=False): return obj(f'{source}-{dest}-{port}-'+str(deny),'network.unreachable' if deny else 'network.reachable',f'{source} → {dest}:{port} {"拒绝" if deny else "连通"}',source=source,destination=dest,port=str(port))
def private(ids): return obj('private-'+ids.replace(',','-'),'security.privateInstances',f'{ids} 保持私网且无 Public IP',ids=ids)
def policy(id='read-dev',verb='read',kind='COMPUTE',scope='dev',group='operators',target=''):
 return r(id,'POLICY',dict(verb=verb,resourceType=kind,scope=scope,targetId=target),dict(group=group))
def sample(id='sample',comp='dev'):
 return r(id,'COMPUTE',dict(status='RUNNING',privateIp='10.10.1.'+str({'sample':40,'worker':50,'other':60,'test-server':70,'prod-server':80}.get(id,90)),publicIp='',nsgs='',listenPorts='8080'),dict(subnet='web-sub'),comp=comp)
def net():
 w=base(); resources=[r('vcn','VCN',dict(cidr='10.10.0.0/16')),r('web-sub','SUBNET',dict(cidr='10.10.1.0/24',public='true'),dict(vcn='vcn')),r('app-sub','SUBNET',dict(cidr='10.10.2.0/24',public='false'),dict(vcn='vcn')),r('db-sub','SUBNET',dict(cidr='10.10.3.0/24',public='false'),dict(vcn='vcn'))]
 for id,sub,ip,ports in [('web','web-sub','10.10.1.10','443'),('app','app-sub','10.10.2.10','8080'),('db','db-sub','10.10.3.10','1521')]:
  resources += [r(id+'-nsg','NSG',dict(rules=''),dict(vcn='vcn')),r(id,'COMPUTE',dict(status='RUNNING',privateIp=ip,publicIp='203.0.113.10' if id=='web' else '',nsgs=id+'-nsg',listenPorts=ports),dict(subnet=sub))]
 w.update({x['id']:x for x in resources}); return w
all_actions=['select_region','study_card','create','update','delete','inspect','probe','advance_tick','hint','move']
def add(n,title,brief,w,goals,golden,negative,steps,hints,constraints=None,principals=None,tutorial=None,region_value='ap-tokyo-1'):
 # Persist explicit task products for semantic reference validation; fixtures stay outside assets.
 products={a['resource']['id']:a['resource']['kind'] for a in golden if a['type']=='create'}
 inspection=next(iter(w),'root')
 golden=golden+[dict(type='inspect',id=inspection)]
 optional=[obj(f'evidence-{n}','logging.evidenceFound','收集至少一项世界状态证据',id=inspection)]
 constraints=(constraints or [])+[obj(f'safe-{n}','iam.leastPrivilege','权限无 tenancy / all-resources / manage 过度授权')]
 failures=[obj(f'broad-{n}','iam.hasBroadPermissions','严重安全违例：请撤销过宽 Policy')]
 if n==8:
  goals += [obj(f'{i}-subnet','resource.referenceEquals',f'{i} 位于独立的私有 Subnet',id=i,key='subnet',value=i+'-sub') for i in ['app','db']]
 prefix=f'levels.{n:03d}'; L[prefix+'.title']=title; L[prefix+'.brief']=brief
 st=[]
 for i,(text,hint) in enumerate(zip(steps,hints),1):
  k=f'{prefix}.step{i}'; h=f'{prefix}.hint{i}'; L[k]=text; L[h]=hint; st.append(dict(id=f's{i}',textKey=k,hintKeys=[h]))
 L[prefix+'.tutorial']=tutorial or '这是非官方离线教育模拟器。所有动作改变共享 WorldState；从 Mission 的即时原因和 Terminal 探测验证配置。教学 ID 不是真实 OCID。'
 wid=f'oci-{n:03d}'
 world=dict(schemaVersion=1,levelId=wid,seed=n*100+1,region=region_value,resources=w,principals=principals or [dict(id='commander'),dict(id='newcomer')])
 level=dict(schemaVersion=1,id=wid,chapter=(n-1)//6+1,order=n,titleKey=prefix+'.title',scenario=dict(region='ap-tokyo-1',briefKey=prefix+'.brief',seed=n*100+1),initialWorld=world,allowedActions=all_actions,steps=st,objectives=goals,constraints=constraints,optionalObjectives=optional,failureTriggers=failures,tutorialKeys=[prefix+'.tutorial'],optimalActionLimit=max(2,len(golden)),reward=dict(xp=100+(n-1)*25,maxStars=3,badgeId='iam-guardian' if n==6 else 'network-detective' if n==12 else ''),dependsOn=[] if n==1 else [f'oci-{n-1:03d}'],products=products)
 dump(C/f'chapters/ch{level["chapter"]:02d}/level-{n:03d}.json',level); dump(ROOT/f'core/testing/fixtures/level-{n:03d}.json',dict(levelId=wid,golden=golden,negative=negative)); levels.append(level)

w={}
add(1,'登录云端','2030 年，林岚欢迎你加入 Astra Cloud Operations。先为东京实验项目选择 Region，再阅读 Tenancy / Region / AD / FD 概念卡。',w,[obj('tokyo','world.region','当前 Region 为东京',value='ap-tokyo-1')]+[obj('card-'+x,'concept.studied','阅读 '+x,card=x) for x in ['Tenancy','Region','AD','FD']],[region()]+[dict(type='study_card',id=x) for x in ['Tenancy','Region','AD','FD']],[dict(type='select_region',value='ap-osaka-1')]+[dict(type='study_card',id=x) for x in ['Tenancy','Region','AD','FD']],['选择 Region','逐一阅读四张概念卡'],['Console 顶部 Region 选择 ap-tokyo-1。','打开 Mission 中四张概念卡，理解层次关系。'],region_value='')
w=net(); w.pop('dev'); w.pop('prod'); [x.update(compartment='root') for x in w.values()]; w['test-server']=sample('test-server','root'); w['prod-server']=sample('prod-server','root')
add(2,'项目隔离','测试与生产资源混在默认 root。林岚要求建立 dev/prod Compartment，并分别归类 test-server 与 prod-server。',w,[exists('dev','COMPARTMENT'),exists('prod','COMPARTMENT'),obj('test-comp','resource.compartmentEquals','测试资源归入 dev',id='test-server',compartment='dev'),obj('prod-comp','resource.compartmentEquals','生产资源归入 prod',id='prod-server',compartment='prod')],[create(r(x,'COMPARTMENT',dict(name=x,parent='root'),comp='root')) for x in ['dev','prod']]+[update('test-server',dict(compartment='dev')),update('prod-server',dict(compartment='prod'))],[create(r('dev','COMPARTMENT',dict(name='dev',parent='root'),comp='root')),create(r('prod','COMPARTMENT',dict(name='prod',parent='root'),comp='root')),update('prod-server',dict(compartment='dev'))],['建立 Compartment','将资源移动到正确环境'],['创建 dev 与 prod，parent=root，资源 Compartment=root。','编辑资源 Compartment；不要仅改名字。'])
w=net(); w['sample']=sample()
add(3,'谁能看见它','新人 newcomer 被拒绝查看 dev。Ravi 要求创建 operators、加入 newcomer，授予 read COMPUTE in dev，同时保留删除拒绝。',w,[exists('operators','GROUP'),prop('operators','members','newcomer'),permits('new-read'),permits('new-delete',verb='manage',deny=True),permits('prod-deny',compartment='prod',deny=True)],[create(r('operators','GROUP',dict(members='newcomer'))),create(policy())],[create(r('operators','GROUP',dict(members='newcomer'))),create(policy(verb='inspect'))],['创建 Group 并加入身份','建立限定 dev 的 Policy'],['Group ID=operators，members=newcomer。','Policy group=operators，verb=read，resourceType=COMPUTE，scope=dev。'])
w=net(); w['sample']=sample(); w['operators']=r('operators','GROUP',dict(members='newcomer')); w['read-dev']=policy(verb='manage',kind='all-resources',scope='tenancy')
add(4,'只给必要权限','Ravi 发现 manage all-resources in tenancy。新人只需 read dev 的 Compute，不能读取生产或删除任何实例。',w,[permits('read-needed'),permits('deny-prod',compartment='prod',deny=True),permits('deny-manage',verb='manage',deny=True),obj('least','iam.leastPrivilege','不存在 manage、tenancy 或 all-resources 过宽策略')],[update('read-dev',dict(verb='read',resourceType='COMPUTE',scope='dev'))],[update('read-dev',dict(verb='read'))],['检查过宽 Policy','收敛资源类型、动词和范围'],['inspect 不足以读内容；manage 超出需求。','将 read-dev 改为 read / COMPUTE / dev。'])
w=net(); w['worker']=sample('worker'); w['other']=sample('other'); w['bucket']=r('bucket','BUCKET',dict(visibility='private'))
principals=[dict(id='commander'),dict(id='newcomer'),dict(id='workload',instanceId='worker'),dict(id='other-workload',instanceId='other')]
add(5,'实例身份','worker 应通过 Instance Principal 读取 bucket。不能把用户密钥嵌入实例；其他实例必须被拒绝。',w,[exists('workloads','DYNAMIC_GROUP'),permits('worker-read','workload',kind='BUCKET',target='bucket'),permits('other-denied','other-workload',kind='BUCKET',target='bucket',deny=True),permits('worker-no-delete','workload',verb='manage',kind='BUCKET',target='bucket',deny=True)],[create(r('workloads','DYNAMIC_GROUP',dict(matchInstance='worker',matchCompartment='dev'))),create(policy('bucket-read',kind='BUCKET',group='workloads',target='bucket'))],[create(r('workloads','DYNAMIC_GROUP',dict(matchInstance='other',matchCompartment='dev'))),create(policy('bucket-read',kind='BUCKET',group='workloads',target='bucket'))],['创建精确的 Dynamic Group','给匹配实例最小权限'],['matchInstance=worker，matchCompartment=dev。','Policy group=workloads，verb=read，resourceType=BUCKET，scope=dev，targetId=bucket。'],principals=principals)
w=net(); w['sample']=sample(comp='prod'); w['worker']=sample('worker'); w['other']=sample('other'); w['bucket']=r('bucket','BUCKET',dict(visibility='private')); w['operators']=r('operators','GROUP',dict(members='')); w['read-dev']=policy(verb='manage',kind='all-resources',scope='tenancy'); w['workloads']=r('workloads','DYNAMIC_GROUP',dict(matchInstance='other',matchCompartment='dev')); w['bucket-read']=policy('bucket-read',kind='BUCKET',group='workloads',target='bucket')
golden=[region(),update('sample',dict(compartment='dev')),update('operators',dict(members='newcomer')),update('read-dev',dict(verb='read',resourceType='COMPUTE',scope='dev')),update('workloads',dict(matchInstance='worker'))]
add(6,'权限迷宫·Boss','东京交接失败：Region、Compartment、用户组、过宽 Policy 与 Dynamic Group 都需核查。Ravi 禁止 tenancy 管理员捷径。',w,[obj('boss-region','world.region','选择东京区域',value='ap-tokyo-1'),obj('boss-comp','resource.compartmentEquals','sample 属于 dev',id='sample',compartment='dev'),permits('boss-user-read'),permits('boss-no-delete',verb='manage',deny=True),permits('boss-no-prod',compartment='prod',deny=True),permits('boss-instance','workload',kind='BUCKET',target='bucket'),permits('boss-other','other-workload',kind='BUCKET',target='bucket',deny=True),obj('boss-least','iam.leastPrivilege','全部 Policy 符合最小权限')],golden,golden[:3],['核查 Region 和资源隔离','修复 Group / Policy','验证 Instance Principal 及越权拒绝'],['东京 Region；sample 必须迁到 dev。','operators 加 newcomer；read-dev 收敛为 read COMPUTE dev。','workloads 匹配 worker；检查其他实例读取被拒绝。'],principals=principals,region_value='ap-osaka-1')
w=base(); golden=[create(r('vcn','VCN',dict(cidr='10.10.0.0/16'))),create(r('web-sub','SUBNET',dict(cidr='10.10.1.0/24',public='true'),dict(vcn='vcn')))]
add(7,'画出网络','星港市第一张网络蓝图：建立 VCN 10.10.0.0/16 与 Subnet 10.10.1.0/24。地图位置不决定网络连通性。',w,[prop('vcn','cidr','10.10.0.0/16'),prop('web-sub','cidr','10.10.1.0/24'),obj('contained','network.subnetContained','Subnet 必须包含于 VCN',id='web-sub',vcn='vcn'),obj('no-overlap','network.nonOverlapping','Subnet 不重叠')],golden,[golden[0],create(r('web-sub','SUBNET',dict(cidr='10.11.1.0/24',public='true'),dict(vcn='vcn')))],['创建 VCN','在 VCN 内创建 Subnet'],['VCN ID=vcn，cidr=10.10.0.0/16。','SUBNET ID=web-sub，vcn=vcn，cidr=10.10.1.0/24，public=true。'])
w=net(); w.pop('app-sub'); w.pop('db-sub')
for id,ip in [('app','10.10.1.20'),('db','10.10.1.30')]: w[id]['references']['subnet']='web-sub'; w[id]['properties'].update(privateIp=ip,publicIp='203.0.113.'+('20' if id=='app' else '30'))
golden=[create(r('app-sub','SUBNET',dict(cidr='10.10.2.0/24',public='false'),dict(vcn='vcn'))),create(r('db-sub','SUBNET',dict(cidr='10.10.3.0/24',public='false'),dict(vcn='vcn'))),update('app',dict(privateIp='10.10.2.10',publicIp=''),dict(subnet='app-sub')),update('db',dict(privateIp='10.10.3.10',publicIp=''),dict(subnet='db-sub'))]
add(8,'公共与私有','Web/App/DB 全在公有网段。保留 Web 的公网地址，将 App 与 DB 迁入不同私有 Subnet 并去掉 Public IP。',w,[private('app,db'),obj('web-public','network.publicAddressable','仅 Web 可公网寻址',id='web',value='true'),obj('separate-subnets','network.nonOverlapping','所有 Subnet 不重叠'),exists('app-sub','SUBNET'),exists('db-sub','SUBNET')],golden,golden[:3],['建立私有 App/DB Subnet','迁移 VNIC 并移除 Public IP'],['app-sub=10.10.2.0/24；db-sub=10.10.3.0/24；public=false。','编辑实例的 subnet、privateIp、publicIp（清空），一次提交。'])
w=net(); golden=[create(r('igw','IGW',dict(enabled='true'),dict(vcn='vcn'))),create(r('public-rt','ROUTE_TABLE',dict(routes='0.0.0.0/0>igw'),dict(vcn='vcn'))),update('web-sub',refs=dict(routeTable='public-rt')),update('web-nsg',dict(rules='egress|0.0.0.0/0|tcp|443|stateful'))]
add(9,'打开城门','Web 无法下载公网依赖。需 IGW、默认 Route、Public IP 与 NSG 出站。用 curl --from web https://internet:443 验证。',w,[reach('web','internet',443)],golden,golden[:-1],['建立 Internet Gateway 与 Route Table','关联 Subnet 并允许必要出站'],['igw enabled=true；public-rt routes=0.0.0.0/0>igw，二者 vcn=vcn。','web-sub routeTable=public-rt；web-nsg rules=egress|0.0.0.0/0|tcp|443|stateful。'])
w=net(); golden=[create(r('nat','NAT',dict(enabled='true'),dict(vcn='vcn'))),create(r('private-rt','ROUTE_TABLE',dict(routes='0.0.0.0/0>nat'),dict(vcn='vcn'))),update('app-sub',refs=dict(routeTable='private-rt')),update('app-nsg',dict(rules='egress|0.0.0.0/0|tcp|443|stateful'))]
add(10,'私有上网','私网 App 要下载补丁，但不允许公网主动连接。使用 NAT，保持 App 无 Public IP。',w,[reach('app','internet',443),reach('internet','app',8080,True),private('app')],golden,golden[:-1],['建立 NAT 出站路径','验证私网出站和入站隔离'],['nat + private-rt: 0.0.0.0/0>nat；关联 app-sub。','app-nsg 仅放行 egress TCP 443，保留 app-sub public=false。'])
w=net(); w['app-nsg']['properties']['rules']='ingress|0.0.0.0/0|tcp|8080|stateful'
golden=[create(r('sgw','SERVICE_GATEWAY',dict(enabled='true',service='oci-services'),dict(vcn='vcn'))),create(r('service-rt','ROUTE_TABLE',dict(routes='oci-services>sgw'),dict(vcn='vcn'))),update('app-sub',refs=dict(routeTable='service-rt')),update('app-nsg',dict(rules='egress|192.0.2.0/24|tcp|443|stateful'))]
add(11,'服务专线与防火墙','App 需要私下访问受支持 OCI 公共服务；当前 NSG 方向错误。建立 Service Gateway 专用路径，避免多余暴露。',w,[reach('app','oci-service',443),reach('internet','app',8080,True),private('app'),prop('app-nsg','rules','egress|192.0.2.0/24|tcp|443|stateful','仅允许教学服务 CIDR 的 443 出站')],golden,golden[:-1],['配置 Service Gateway 和服务路由','修正 NSG 并验证回程'],['sgw service=oci-services；service-rt routes=oci-services>sgw，关联 app-sub。','教学服务地址 192.0.2.80；允许该 CIDR 的 stateful TCP 443 egress。'])
w=net(); w['collision']=r('collision','SUBNET',dict(cidr='10.10.2.0/24',public='false'),dict(vcn='vcn')); w['nat']=r('nat','NAT',dict(enabled='true'),dict(vcn='vcn')); w['igw']=r('igw','IGW',dict(enabled='true'),dict(vcn='vcn')); w['public-rt']=r('public-rt','ROUTE_TABLE',dict(routes='0.0.0.0/0>nat'),dict(vcn='vcn')); w['web-sub']['references']['routeTable']='public-rt'; w['db-sub']['properties']['public']='true'; w['db']['properties']['publicIp']='203.0.113.30'; w['app-nsg']['properties']['rules']='ingress|10.10.1.0/24|tcp|8081|stateful'
golden=[update('collision',dict(cidr='10.10.4.0/24')),update('public-rt',dict(routes='0.0.0.0/0>igw')),update('web-nsg',dict(rules='ingress|0.0.0.0/0|tcp|443|stateful;egress|10.10.2.0/24|tcp|8080|stateful')),update('app-nsg',dict(rules='ingress|10.10.1.0/24|tcp|8080|stateful;egress|10.10.3.0/24|tcp|1521|stateful')),update('db-nsg',dict(rules='ingress|10.10.2.0/24|tcp|1521|stateful')),update('db',dict(publicIp='')),update('db-sub',dict(public='false'))]
add(12,'网络黑洞·Boss','佐藤美咲报告门户中断：CIDR 冲突、错误公网路由、App/DB 端口错漏与 DB 公网暴露并存。恢复 Internet→Web→App→DB，保持数据层私有。',w,[obj('boss-nonoverlap','network.nonOverlapping','消除 Subnet CIDR 冲突'),reach('internet','web',443),reach('web','app',8080),reach('app','db',1521),reach('internet','db',1521,True),private('db')],golden,golden[:4],['排查 Subnet 与 Gateway','修复 Web→App→DB 安全规则','隔离 DB 并执行全链路验证'],['collision 改为 10.10.4.0/24；public-rt 下一跳改为 igw。','Web ingress 443 + egress 8080；App ingress 8080 + egress 1521；DB ingress 1521，限制源 CIDR。','先清空 DB Public IP，再将 db-sub public=false。'])

dump(C/'locales/zh-CN.json',L)
dump(C/'locales/en-US.json',{'_status':'Reserved; Chinese fallback. English translation is not implemented.'})
dump(C/'locales/ja-JP.json',{'_status':'Reserved; Chinese fallback. Japanese translation is not implemented.'})
dump(C/'index.json',[f'chapters/ch{l["chapter"]:02d}/level-{l["order"]:03d}.json' for l in levels])
# Strict structural Schema; semantic validation lives in ContentLoader and tests.
def object_(properties,required=None): return dict(type='object',additionalProperties=False,properties=properties,required=required or list(properties))
string={'type':'string'}; strings={'type':'array','items':string}; smap={'type':'object','additionalProperties':string}
resource=object_(dict(id=dict(type='string',pattern='^[a-z][a-z0-9-]{0,39}$'),kind=dict(enum=['COMPARTMENT','GROUP','DYNAMIC_GROUP','POLICY','VCN','SUBNET','IGW','NAT','SERVICE_GATEWAY','ROUTE_TABLE','NSG','COMPUTE','BUCKET']),compartment=string,region=dict(enum=['ap-tokyo-1','ap-osaka-1','us-ashburn-1']),properties=smap,references=smap))
world=object_(dict(schemaVersion={'const':1},levelId=string,seed={'type':'integer'},tick={'type':'integer','minimum':0},region=string,resources={'type':'object','additionalProperties':{'$ref':'#/$defs/resource'}},principals={'type':'array','items':object_(dict(id=string,instanceId=string),['id'])},studiedCards=strings,evidence=strings,events={'type':'array','items':object_(dict(sequence={'type':'integer'},tick={'type':'integer'},actor=string,action=string,detail=string))},changes={'type':'integer','minimum':0},hintsUsed={'type':'integer','minimum':0},budgetCredits={'type':'number','minimum':0},layout={'type':'object','additionalProperties':object_(dict(x={'type':'integer'},y={'type':'integer'}))}),['schemaVersion','levelId','seed','region','resources','principals'])
params={}
for l in levels:
 for o in l['objectives']+l['constraints']+l['optionalObjectives']+l['failureTriggers']:
  params[o['predicate']]=set(o['params'])
objective={'oneOf':[object_(dict(id=string,predicate={'const':name},params=object_({p:string for p in sorted(keys)}),messageKey=string)) for name,keys in sorted(params.items())]}
# Include the complete Phase 1 predicate registry, even predicates not used by a level yet.
for name,keys in [('cost.underBudget',set())]:
 if name not in params: objective['oneOf'].append(object_(dict(id=string,predicate={'const':name},params=object_({}),messageKey=string)))
schema=object_(dict(schemaVersion={'const':1},id=dict(type='string',pattern='^oci-[0-9]{3}$'),chapter=dict(type='integer',minimum=1,maximum=10),order=dict(type='integer',minimum=1,maximum=60),titleKey=string,scenario=object_(dict(region=dict(enum=['ap-tokyo-1','ap-osaka-1','us-ashburn-1']),briefKey=string,seed={'type':'integer'})),initialWorld=world,allowedActions=dict(type='array',minItems=1,uniqueItems=True,items={'enum':all_actions}),steps=dict(type='array',minItems=1,items=object_(dict(id=string,textKey=string,hintKeys=strings))),objectives=dict(type='array',minItems=1,items={'$ref':'#/$defs/objective'}),constraints={'type':'array','items':{'$ref':'#/$defs/objective'}},optionalObjectives={'type':'array','items':{'$ref':'#/$defs/objective'}},failureTriggers={'type':'array','items':{'$ref':'#/$defs/objective'}},tutorialKeys=strings,optimalActionLimit={'type':'integer','minimum':1},reward=object_(dict(xp=dict(type='integer',minimum=100,maximum=500),maxStars={'const':3},badgeId=string)),dependsOn=strings,products={'type':'object','additionalProperties':{'enum':['COMPARTMENT','GROUP','DYNAMIC_GROUP','POLICY','VCN','SUBNET','IGW','NAT','SERVICE_GATEWAY','ROUTE_TABLE','NSG','COMPUTE','BUCKET']}}))
schema.update({'$schema':'https://json-schema.org/draft/2020-12/schema','$id':'https://example.invalid/schemas/oci-level.schema.json','$defs':dict(resource=resource,objective=objective)})
dump(C/'schemas/level.schema.json',schema)
print(f'Generated {len(levels)} playable levels; no placeholders for L13-L60.')
