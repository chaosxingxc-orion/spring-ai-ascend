# agent-runtime 鎺ュ彛涓庣姸鎬佷腑闂翠欢璁捐

鏈枃鍚堝苟 `agent-runtime` 鎺ュ彛璁捐璇存槑涓?2026-06-09 Agent State 涓棿浠舵彁妗堬紝浣滀负褰撳墠 L1 璁捐鍏ュ彛銆傜敓鎴愪簨瀹炰粛浠?`architecture/facts/generated/*.json` 涓哄噯锛涙湰鏂囧彧瑙ｉ噴褰撳墠浠ｇ爜杈圭晫銆佹ā鍧椾氦浜掑拰鍚庣画鎵╁睍鍘熷垯銆?
## 1. 璁捐鐩爣

`agent-runtime` 鐨勮亴璐ｆ槸鎶婁竴涓笟鍔?Agent 鍖呰鎴愬崟 Agent runtime锛屽苟閫氳繃 A2A 鍗忚鏆撮湶鎵ц鑳藉姏銆傚綋鍓嶈璁￠伒寰洓鏉¤竟鐣岋細

1. A2A 灞傚彧鍋氬崗璁ˉ鎺ャ€佷笂涓嬫枃鏋勯€犮€佺粨鏋滃彂灏勫拰浠诲姟鐘舵€佹槧灏勩€?2. `engine.spi` 鍙繚鐣欒法 Agent 妗嗘灦绋冲畾鎴愮珛鐨勭獎鎺ュ彛銆?3. 鍏蜂綋 Agent 妗嗘灦鐨勬墽琛屻€佽楗般€佺姸鎬佹仮澶嶅拰缁撴灉杞崲鐣欏湪瀵瑰簲 adapter 鍐呴儴銆?4. 妗嗘灦宸叉湁鍘熺敓 checkpoint / session / callback 鏈哄埗鏃讹紝runtime 浼樺厛妗ユ帴鍘熺敓鏈哄埗锛屼笉閲嶅瀹炵幇鐘舵€佸悗绔€?
杩欐剰鍛崇潃 runtime 涓嶆彁渚涘叏灞€ provider chain锛屼篃涓嶈姹傛墍鏈?Agent 妗嗘灦缁ф壙鍚屼竴涓娊璞″熀绫汇€傛柊鑳藉姏浼樺厛鍦ㄥ叿浣?adapter 鍐呯粍鍚堬紱鍙湁璺ㄦ鏋惰涔夌ǔ瀹氬悗锛屾墠鎻愬崌涓哄叕鍏?SPI銆?
## 2. 鏍稿績鎺ュ彛

### 2.1 `AgentRuntimeHandler`

`AgentRuntimeHandler` 鏄墽琛?SPI銆傛瘡涓?handler 琛ㄧず涓€涓?runtime 瀹炰緥鎵胯浇鐨勪竴涓笟鍔?Agent銆?
```java
public interface AgentRuntimeHandler {
    String agentId();

    boolean isHealthy();

    Stream<?> execute(AgentExecutionContext context);

    StreamAdapter resultAdapter();

    default void start() {}

    default void stop() {}

    default void cancel(String taskId) {}
}
```

璇箟锛?
- `agentId()` 杩斿洖璇?handler 鏈嶅姟鐨勪笟鍔?Agent 鏍囪瘑銆?- `isHealthy()` 琛ㄧず褰撳墠 handler 鏄惁鍙帴娴侀噺锛涚敱 runtime 鍋ュ悍闈?  锛坄boot.AgentRuntimeHealthIndicator`锛変笌灏辩华闂ㄧ娑堣垂锛圓DR-0161锛夈€?- `execute(context)` 鎵ц涓€娆?Agent 璋冪敤锛岃繑鍥炴鏋跺師鐢熺粨鏋滄祦銆?- `resultAdapter()` 鎶婃鏋跺師鐢熺粨鏋滄祦杞崲鎴?runtime 涓珛鐨?`AgentExecutionResult`銆?- `start()` 鎵撳紑 handler 鑷湁鐨勯暱鐢熷懡璧勬簮锛涚敱瀹夸富锛坄boot.AgentRuntimeLifecycle`锛?  SmartLifecycle锛宲hase 浣庝簬 web server锛夊湪鎺ユ祦閲忎箣鍓嶈皟鐢紱鎶涘紓甯稿嵆鍚姩澶辫触
  锛坒ail-fast锛屼笉鍏佽"宸叉湇鍔′絾姘歌繙涓嶅氨缁?鐨勫兊灏告€侊級銆?- `stop()` 鍦ㄥ涓诲仠姝㈡淳鍙戞柊鎵ц涔嬪悗璋冪敤锛屾寜娉ㄥ唽閫嗗簭閲婃斁 `start()` 鎵撳紑鐨勮祫婧愶紱
  鎵€鏈夋潈瑙勫垯涓?owns-vs-borrows鈥斺€斿彧閲婃斁鑷繁鍒涘缓鐨勶紝娉ㄥ叆鐨勫崗浣滃璞″綊娉ㄥ叆鏂广€?- `cancel(taskId)` 鍗忎綔寮忓彇娑堜竴娆″湪椋炴墽琛岋紱鏈夊師鐢熶腑鏂殑妗嗘灦鍦ㄦ浼犲锛屽涓?  锛坄A2aAgentExecutor`锛夊悓鏃跺叧闂鎵ц鐨勫師鐢熺粨鏋滄祦浠ユ挄寮€浼犺緭灞傘€?
鐢熷懡鍛ㄦ湡涓?scope锛圓DR-0161锛夛細handler 鏈嶅姟绾э紙涓婅堪 start/stop/health锛夈€佹墽琛岀骇
锛坋xecute 缁撴灉娴?try-with-resources + cancel 璐€?+ 鏈氨缁湡 `RUNTIME_NOT_READY`
鍙噸璇曟嫆缁濓級銆佷腑闂翠欢/璧勬簮绾э紙瀹瑰櫒鎸佹湁鏈嶅姟 bean 鐢熷懡鍛ㄦ湡锛宧andler 鍙粍鍚堣嚜宸辨嫢鏈夌殑锛夈€?
`AgentRuntimeHandler` 涓嶆壙杞介€氱敤 before/after provider銆佺姸鎬佸瓨鍌ㄣ€佹矙绠辨垨宸ュ叿瑕嗙洊閫昏緫銆傝繖浜涜兘鍔涘湪涓嶅悓 Agent 妗嗘灦涓€氬父鏈夊師鐢熸墿灞曠偣锛屽己琛岀粺涓€浼氳 runtime 鍙嶅悜渚濊禆鍏蜂綋妗嗘灦璇箟銆?
### 2.2 `StreamAdapter`

`StreamAdapter` 鏄粨鏋滆浆鎹?SPI銆?
```java
@FunctionalInterface
public interface StreamAdapter {
    Stream<AgentExecutionResult> adapt(Stream<?> rawResults);
}
```

A2A 灞傚彧娑堣垂 `AgentExecutionResult`锛屼笉鐞嗚В OpenJiuwen 鎴栧叾浠栨鏋剁殑鍐呴儴缁撴灉缁撴瀯銆?
### 2.3 `AgentCardProvider`

`AgentCardProvider` 鏄彲閫夌殑 A2A Agent Card 鍏冩暟鎹?provider銆?
```java
public interface AgentCardProvider {
    AgentCard agentCard();
}
```

瀹冧笌 `AgentRuntimeHandler` 鍒嗙锛?
- `AgentRuntimeHandler` 璐熻矗鎵ц Agent銆?- `AgentCardProvider` 璐熻矗澹版槑瀵瑰鏆撮湶鐨?A2A 鍏冩暟鎹€?
涓氬姟鏂瑰彲浠ュ彧鎻愪緵 handler 浣跨敤榛樿 Agent Card锛屼篃鍙互棰濆鎻愪緵鐙珛 `AgentCardProvider` Bean銆傜畝鍗?Agent 涓嶉渶瑕佷负浜嗚嚜瀹氫箟鎵ц鑰岀户鎵?Agent Card 鍩虹被銆?
### 2.4 `AgentExecutionContext`

`AgentExecutionContext` 鏄?A2A bridge 涓庢鏋?adapter 涔嬮棿鐨勮交閲?carrier锛屽寘鍚細

- `RuntimeIdentity scope`锛歚tenantId`銆乣userId`銆乣sessionId`銆乣taskId`銆乣agentId`銆?- `inputType` 涓?A2A message 鍒楄〃銆?- `variables`锛氳皟鐢ㄤ晶浼犲叆鐨勮交閲忓彉閲忋€?- `agentStateKey`锛氫笟鍔″彲鎺х殑绋冲畾鐘舵€?key銆?- 鍙€?`agentState` map锛氱粰娌℃湁鍘熺敓 checkpoint 鐨勬鏋跺仛杞婚噺鐘舵€佹ˉ鎺ャ€?
`agentStateKey` 瑙ｆ瀽椤哄簭锛?
```text
variables["agentStateKey"]
  -> variables["stateKey"]
  -> fallback taskId
```

fallback 鍒?`taskId` 鏄湁鎰忚璁★細褰撶敤鎴疯烦鍑哄師浠诲姟骞朵骇鐢熸柊 task 鏃讹紝鏂?task 澶╃劧闅旂鐘舵€侊紱濡傛灉涓氬姟瑕佽法澶氳疆澶嶇敤鍚屼竴浠界姸鎬侊紝搴旀樉寮忎紶鍏ョǔ瀹?`agentStateKey`銆?
### 2.5 `MemoryProvider`

`MemoryProvider` 鏄鐣欑殑璁板繂鍒濆鍖栥€佹绱笌鍐欏洖绐?SPI銆?
```java
public interface MemoryProvider {
    default void init(AgentExecutionContext context) {
    }

    List<MemoryHit> search(AgentExecutionContext context, String query, int limit);

    default void save(AgentExecutionContext context, List<MemoryRecord> records) {
    }
}
```

瀹冨彧瀹氫箟 `init` / `search` / `save` 涓変釜鍩虹璇箟锛屼笉璐熻矗 compact銆乥udget銆佸悜閲忕储寮曘€侀暱鏈熻蹇嗘不鐞嗘垨鍏蜂綋鍚庣銆傚悗缁?Mem 涓棿浠跺彲浠ュ湪璇ユ帴鍙ｅ熀纭€涓婃墿灞曪紝涔熷彲浠ョ敱鍏蜂綋妗嗘灦 adapter 鐩存帴缁勫悎鑷繁鐨?Mem 鑳藉姏銆?
榛樿 scope 浼氫紶鍏?`user_id`銆乣scope_id`銆乣session_id`锛屼絾杩欐槸 adapter 鐨勯粯璁ゆ槧灏勶紝涓嶆槸骞冲彴寮哄埗鏍煎紡锛涗笟鍔″彲浠ラ€氳繃 `ScopeMapper` 鎸夎嚜宸辩殑 tenant銆佺敤鎴枫€丄gent 鍜屼細璇濊鍒欑敓鎴?OpenJiuwen 鎵€闇€鍙傛暟銆?
`MemoryRecord` 鏄?runtime 涓珛鐨?message-like 璁板綍锛?
```java
record MemoryRecord(String id, String role, String content, Map<String, Object> metadata) {
}
```

OpenJiuwen adapter 浼氬湪鑷繁鐨勫寘鍐呮妸 OpenJiuwen `BaseMessage` 杞崲鎴?`MemoryRecord`銆傝浆鎹㈣鍒欎笉鏀惧叆鍏叡 SPI锛岄伩鍏?AgentScope銆丱penJiuwen 鍜屽悗缁鏋朵簰鐩告薄鏌撱€?
## 3. A2A 鎵ц閾捐矾

`A2aAgentExecutor` 鏄?A2A SDK 鐨?`AgentExecutor` 瀹炵幇锛岃礋璐ｆ妸 A2A 璇锋眰妗ユ帴鍒?runtime handler銆?
```text
A2A RequestContext
  -> A2aAgentExecutor.execute(...)
  -> AgentExecutionContext
  -> AgentRuntimeHandler.execute(context)
  -> StreamAdapter.adapt(rawResults)
  -> AgentEmitter task state / message output
```

`A2aAgentExecutor` 鍋氾細

- 浠?A2A `RequestContext` 鎻愬彇 `taskId`銆乣contextId`銆佹秷鎭枃鏈笌 metadata銆?- 鏋勯€?`AgentExecutionContext`銆?- 璋冪敤 `handler.execute(context)`銆?- 浣跨敤 `handler.resultAdapter()` 杞崲缁撴灉銆?- 灏?`OUTPUT`銆乣COMPLETED`銆乣FAILED`銆乣INTERRUPTED` 鏄犲皠涓?A2A emitter 琛屼负銆?
`A2aAgentExecutor` 涓嶅仛锛?
- 涓嶅垱寤哄叿浣?Agent銆?- 涓嶅畨瑁?OpenJiuwen Rail銆?- 涓嶇悊瑙?OpenJiuwen checkpointer銆?- 涓嶆寔鏈夌姸鎬佸瓨鍌ㄣ€?- 涓嶆壙杞介€氱敤 provider chain銆?
杩欐牱鍙互閬垮厤 A2A 鍗忚妗ュ彉鎴愭墍鏈夋鏋惰兘鍔涚殑闆嗕腑鐐广€?
## 4. OpenJiuwen adapter

OpenJiuwen 鐨勬鏋堕€傞厤鏀舵暃鍦?`runtime.engine.openjiuwen` 鍖呭唴銆?
### 4.1 `OpenJiuwenAgentRuntimeHandler`

`OpenJiuwenAgentRuntimeHandler` 瀹炵幇 `AgentRuntimeHandler`锛屽浐瀹?OpenJiuwen 鎵ц涓绘祦绋嬶細

```text
AgentExecutionContext
  -> createOpenJiuwenAgent(context)
  -> openJiuwenRails(context)
  -> BaseAgent.registerRail(...)
  -> toOpenJiuwenInput(context)
  -> Runner.runAgent(agent, input, conversationId, null)
  -> OpenJiuwenStreamAdapter
```

瀛愮被鍙渶瑕佸疄鐜板叿浣?Agent 鍒涘缓锛?
```java
protected abstract BaseAgent createOpenJiuwenAgent(AgentExecutionContext context);
```

涓氬姟渚ц礋璐ｂ€滃浣曞垱寤哄拰閰嶇疆 OpenJiuwen 鐨?`BaseAgent`鈥濓紱runtime adapter 璐熻矗鎵ц鍗忚銆丷ail 瀹夎銆佽緭鍏ヨ浆鎹€佺粨鏋滆浆鎹㈠拰閿欒鏄犲皠銆?
### 4.2 OpenJiuwen Rail 鎵╁睍鐐?
OpenJiuwen adapter 浣跨敤 OpenJiuwen 0.1.12 鐨?`BaseAgent.registerRail(...)` 涓?`AgentRail` 浣滀负妗嗘灦鏈湴鎵╁睍鐐广€傞粯璁や笉瀹夎 Rail锛涢渶瑕佹帴鍏?Mem銆佸伐鍏锋不鐞嗘垨娌欑鏃讹紝鐢卞瓙绫昏鐩?`openJiuwenRails(context)`锛岃繑鍥為渶瑕佹敞鍐屽埌 OpenJiuwen Agent 鐨?Rail銆?
褰撳墠鎻愪緵鐨勫唴缃?Rail 鏄?`OpenJiuwenAgentRuntimeHandler.MemoryRuntimeRail`銆傚畠鏄?OpenJiuwen 鏈湴妗ワ紝涓嶆槸鍏叡 runtime SPI锛?
- `beforeInvoke(...)` 璋冪敤 runtime 涓珛 `MemoryProvider.init(context)`銆?- `beforeInvoke(...)` 浣跨敤鏈€鏂扮敤鎴疯緭鍏ヨ皟鐢?`MemoryProvider.search(context, query, limit)`锛屽苟鎶婃绱㈢粨鏋滀綔涓?OpenJiuwen `SystemMessage` 娉ㄥ叆褰撳墠 `ModelContext`銆?- `afterInvoke(...)` 浠?OpenJiuwen callback context 涓彇鍑?`BaseMessage` 鍒楄〃锛岃浆鎹㈡垚 `MemoryProvider.MemoryRecord`锛屽啀璋冪敤 `MemoryProvider.save(context, records)`銆?- OpenJiuwen `BaseMessage` 涓?`MemoryRecord` 鐨勮浆鎹㈢敱 `OpenJiuwenMemoryMessageAdapter` 璐熻矗锛岀暀鍦?`runtime.engine.openjiuwen` 鍖呭唴銆?
閫傚悎鏀惧叆 Rail 鐨勮兘鍔涳細

- 妯″瀷璋冪敤鍓嶅悗鐨?trace銆佽€楁椂閲囨牱鍜屽紓甯歌娴嬨€?- 宸ュ叿璋冪敤鍓嶅悗鐨勭鎺с€佹矙绠辨牎楠屽拰瀹¤銆?- Mem 妫€绱㈠寮轰笌鎵ц鍚庡啓鍥炪€?- OpenJiuwen 鍘熺敓 callback context 鐨勮交閲忓寮恒€?
涓嶅缓璁斁鍏?Rail 鐨勮兘鍔涳細

- Agent 鏋勯€狅細浠嶇敱 `createOpenJiuwenAgent(context)` 璐熻矗銆?- `Runner.runAgent(...)` 璋冪敤锛氫粛鐢?handler 璐熻矗銆?- `conversation_id / agentStateKey` 鍐崇瓥锛氫粛鐢?handler / message adapter 璐熻矗銆?- Agent Card锛氬睘浜庡惎鍔ㄦ湡鍏冩暟鎹紝涓嶆槸鎵ц鏈熺敓鍛藉懆鏈?hook銆?- Checkpointer 閰嶇疆锛氬睘浜?OpenJiuwen runtime / sample wiring锛屼笉搴斿湪鎵ц鏈?Rail 涓垏鎹㈠叏灞€鐘舵€佸悗绔€?
> 鐗堟湰绾︽潫锛氭湰鏂囨寜 OpenJiuwen `agent-core-java:0.1.12` 鐨?API 璁捐銆?.1.12 浠嶆彁渚?`BaseAgent.registerRail(...)`銆乣AgentRail`銆乣Runner.runAgent(...)` 涓?`CheckpointerFactory`锛屽洜姝ゅ綋鍓?adapter 浠ヨ繖浜?API 涓鸿竟鐣岋紱涓嶈鎶婂叾浠栧垎鏀笂鐨勬柊杩愯鏃舵ā鍨嬪綋鎴愭湰鏂囦緷鎹€?
### 4.3 `OpenJiuwenMessageAdapter`

`OpenJiuwenMessageAdapter` 鎶?`AgentExecutionContext` 杞垚 OpenJiuwen input銆傚叧閿偣鏄細

```text
query = 鏈€鏂扮敤鎴疯緭鍏?conversation_id = context.getAgentStateKey()
```

OpenJiuwen 鑷韩閫氳繃 `conversation_id` 涓庡師鐢?checkpointer 瀹屾垚 session 淇濆瓨鍜屾仮澶嶃€俽untime 涓嶅湪姣忔璋冪敤鍚庢墜宸?`dumpState()` / `updateState(...)` 鎼繍 OpenJiuwen 鍐呴儴鐘舵€併€?
### 4.4 OpenJiuwen native checkpointer

OpenJiuwen 鐨勭姸鎬佷富璺緞鏄師鐢?Runner / Checkpointer锛?
```text
AgentExecutionContext.getAgentStateKey()
  -> OpenJiuwen input["conversation_id"]
  -> Runner.runAgent(..., conversationId, ...)
  -> OpenJiuwen Checkpointer restore/save
```

涓氬姟鏂瑰彧闇€瑕佷繚璇?`agentStateKey` 绋冲畾銆侽penJiuwen 鍐呴儴淇濆瓨鍝簺瀛楁銆佸浣曞簭鍒楀寲銆佷綍鏃舵仮澶嶏紝浜ょ粰 OpenJiuwen `Runner` / `Checkpointer` 澶勭悊銆?
褰撳墠 sample 閲囩敤 OpenJiuwen 鏍囧噯鏂瑰紡閰嶇疆 checkpointer锛氶粯璁や娇鐢?`InMemoryCheckpointer`锛屽彲閫氳繃閰嶇疆鍒囨崲鍒?`RedisCheckpointer`銆侽penJiuwen adapter 涓嶉渶瑕佸洜涓?checkpointer 鍚庣鍙樺寲鑰屼慨鏀广€?
### 4.5 `OpenJiuwenStreamAdapter`

`OpenJiuwenStreamAdapter` 鎶?OpenJiuwen 杩斿洖鐨?map 缁撴瀯杞崲涓?`AgentExecutionResult`锛?
- answer / output -> `OUTPUT` 鎴?`COMPLETED`
- error -> `FAILED`
- interrupt / input required -> `INTERRUPTED`

A2A 灞傚彧澶勭悊杞崲鍚庣殑 `AgentExecutionResult`锛屼笉鐩存帴鐞嗚В OpenJiuwen 鍘熷缁撴灉銆?
## 5. 鐘舵€佷笌璁板繂鍘熷垯

褰撳墠鐘舵€佽璁″垎涓轰袱绫伙細

1. 妗嗘灦鍘熺敓 checkpoint锛氫紭鍏堜娇鐢紝渚嬪 OpenJiuwen銆?2. runtime 棰勭暀绐?SPI锛氱粰娌℃湁鍘熺敓 checkpoint 鎴栭渶瑕?runtime 杈呭姪鐨勬鏋朵娇鐢ㄣ€?
涓嶈鎶婃鏂囪蹇嗐€佸ぇ payload 鎴栧畬鏁翠笟鍔＄姸鎬侀兘濉炶繘 `AgentExecutionContext`銆俙AgentExecutionContext` 鏇撮€傚悎鎵胯浇鎵ц韬唤銆佽緭鍏ユ秷鎭€乵etadata銆佺姸鎬?key 鎴栧皬鍨嬬姸鎬佸紩鐢ㄣ€傚畬鏁寸姸鎬佸悗绔€佽蹇嗗帇缂╁拰妫€绱㈢瓥鐣ュ簲鐢卞搴斾腑闂翠欢鎴栨鏋跺悗绔礋璐ｃ€?
Mem 鍚庣画鎺ュ叆寤鸿锛?
- Mem 涓嶅鐢?Agent State 鍚庣瀛樻鏂囪蹇嗐€?- Agent State 鍚庣鍙繚瀛?`memoryRef`銆乣checkpointRef`銆乣cursor` 绛夊皬瀵硅薄锛涘綋鍓嶄唬鐮佷笉鍐嶅彂甯冨崟鐙殑 `AgentStateStore` 鎺ュ彛銆?- Mem 鐨?compact銆乥udget銆乿ector retrieval銆侀暱鏈熸绱㈢敱 Mem backend 璐熻矗銆?- OpenJiuwen 鍙紭鍏堥€氳繃 Rail 鎴栧叿浣?`createOpenJiuwenAgent(context)` 鐨?agent 閰嶇疆鎺ュ叆 Mem銆?
## 6. 妯″潡鑱岃矗

| 妯″潡 / 鍖?| 褰撳墠鑱岃矗 | 涓嶆壙鎷呯殑鑱岃矗 |
|---|---|---|
| `runtime.engine.a2a` | A2A 璇锋眰鎺ュ叆銆佷笂涓嬫枃鏋勯€犮€佺粨鏋滄槧灏勫埌 emitter | 涓嶅垱寤哄叿浣?Agent锛屼笉瀹夎妗嗘灦瑁呴グ锛屼笉绠＄悊鐘舵€佸瓨鍌?|
| `runtime.engine.spi` | 瀹氫箟璺?Agent 妗嗘灦绋冲畾鎴愮珛鐨勭獎 SPI | 涓嶆斁鍏蜂綋妗嗘灦瀹炵幇锛屼笉鎵胯浇 provider chain |
| `runtime.engine.openjiuwen` | OpenJiuwen adapter銆丄gent 鍒涘缓鍏ュ彛銆丷ail 瀹夎銆丷unner 璋冪敤銆佽緭鍏?杈撳嚭杞崲 | 涓嶈姹傚叾浠栨鏋跺鐢?OpenJiuwen 鏈哄埗 |
| `runtime.engine.service` | 鐘舵€佸瓨鍌ㄦ娊璞″拰榛樿瀹炵幇 | 涓嶇悊瑙ｆ煇涓?Agent 妗嗘灦鐨勫唴閮ㄧ姸鎬佺粨鏋?|
| `examples/*` | 鎻愪緵鍏蜂綋涓氬姟 Agent 绀轰緥鍜岄厤缃?| 涓嶅畾涔?runtime 鏍稿績鎵ц杈圭晫 |

## 7. 鎺ュ叆鏂?Agent 妗嗘灦

鏂板 Agent 妗嗘灦鏃讹紝浼樺厛鎸変互涓嬮『搴忓垽鏂細

1. 妗嗘灦鏄惁宸叉湁鍘熺敓 checkpoint / session / state 鏈哄埗銆?2. 妗嗘灦鏄惁宸叉湁 rail銆乵iddleware銆乧allback銆乮nterceptor 绛夊師鐢熻楗版満鍒躲€?3. 妗嗘灦缁撴灉濡備綍鏄犲皠鍒?`AgentExecutionResult`銆?4. 鏄惁闇€瑕佽嚜瀹氫箟 Agent Card銆?5. 鏄惁闇€瑕佷娇鐢?`MemoryProvider` 杩欑被 runtime 棰勭暀绐?SPI銆?
鎺ㄨ崘褰㈡€侊細

- 瀹炵幇鏂扮殑 `AgentRuntimeHandler`銆?- 鎻愪緵瀵瑰簲 `StreamAdapter`銆?- 濡傛湁妗嗘灦鍘熺敓瑁呴グ鏈哄埗锛岃楗伴€昏緫鐣欏湪璇ユ鏋?adapter 鍐呴儴銆?- 濡傞渶鑷畾涔?A2A 鍏冩暟鎹紝棰濆鎻愪緵 `AgentCardProvider`銆?- 鍙湁褰撴煇涓兘鍔涜法澶氫釜 Agent 妗嗘灦绋冲畾鎴愮珛鏃讹紝鎵嶈€冭檻鎻愬崌涓烘柊鐨?runtime SPI銆?
## 8. 澶辫触璇箟

- state load 澶辫触锛氱敱鍏蜂綋 Provider / checkpointer fail closed锛屼笉璋冪敤 handler 鎴栬妗嗘灦杩斿洖鏄庣‘澶辫触銆?- handler 鎵ц澶辫触锛氳浆鎹㈡垚 `FAILED`锛屼繚鎸?A2A 鍗曞嚭鍙ｈ涔夈€?- 鎵ц鍚庤緟鍔╁啓鍏ュけ璐ワ細璁板綍 warn锛屼笉鎶婂凡缁忓畬鎴愮殑浠诲姟鍙嶈浆鎴愬け璐ワ紝閬垮厤鍙岀粓鎬併€?- state save 澶辫触锛氫笉瑕嗙洊涓氬姟鎵ц缁撴灉锛涚敓浜ф€佸悗缁渶瑕佸憡璀︺€侀噸璇曟垨琛ュ伩闃熷垪銆?
## 9. 褰撳墠鐗规€ф竻鍗?
| Feature | Status | Notes |
|---|---|---|
| `AgentRuntimeHandler` 鎵ц SPI | Implemented | 鍗?Agent runtime 鎵ц鍏ュ彛 |
| `StreamAdapter` 缁撴灉杞崲 SPI | Implemented | 妗嗘灦鍘熺敓缁撴灉杞?`AgentExecutionResult` |
| `AgentCardProvider` 鍙€夊厓鏁版嵁 provider | Implemented | 鎵ц鑱岃矗鍜?Agent Card 澹版槑鍒嗙 |
| 涓氬姟鑷畾涔?state key | Implemented | `agentStateKey` / `stateKey`锛宖allback `taskId` |
| `MemoryProvider` 棰勭暀 SPI | Implemented | 瀹氫箟 `init` / `search` / `save` 鍩虹璇箟 |
| OpenJiuwen 鍘熺敓 MemoryProvider adapter | Implemented | 鐩存帴鍩轰簬 OpenJiuwen 0.1.12 `MemoryProvider`锛屾ˉ鎺?`initialize` / `prefetch` / `syncTurn` |
| OpenJiuwen native checkpointer 妗ユ帴 | Implemented | 浣跨敤绋冲畾 `conversation_id` |
| OpenJiuwen Rail 鎵╁睍鐐?| Implemented | 榛樿涓嶅畨瑁?Rail锛涘彲閫?`MemoryRuntimeRail` 鏀寔 memory search 娉ㄥ叆涓庢墽琛屽悗鍐欏洖 |
| Snapshot / revision / fencing | Deferred | durable backend 鏃惰ˉ榻?|
| Mem 姝ｅ紡闆嗘垚 | Deferred | 鍚庣画鍗曠嫭璁捐鍜屽疄鐜?|

## 10. 楠岃瘉

褰撳墠鎺ュ彛杈圭晫涓昏鐢变互涓嬫祴璇曡鐩栵細

- `OpenJiuwenAgentRuntimeHandlerTest`锛氶獙璇?OpenJiuwen handler 榛樿涓嶅畨瑁?Rail銆佸瓙绫诲彲瀹夎 `MemoryRuntimeRail`銆佷娇鐢ㄧǔ瀹?`agentStateKey` 浣滀负 conversation id銆丱penJiuwen message 涓?`MemoryRecord` 杞崲锛屼互鍙婂紓甯哥粨鏋滄槧灏勩€?- `A2aJsonRpcControllerTest`锛氶獙璇?A2A JSON-RPC 鎺ュ叆璺緞銆?- `RuntimeAppTest`锛氶獙璇?runtime app 鍩虹鍚姩璺緞銆?
鎺ㄨ崘鏈€灏忛獙璇佸懡浠わ細

```bash
wsl -d Ubuntu-24.04 -- bash -lc 'cd /mnt/d/repo/spring-ai-ascend && ./mvnw -pl agent-runtime -Dtest=OpenJiuwenAgentRuntimeHandlerTest,A2aJsonRpcControllerTest,RuntimeAppTest test'
```

濡傛灉鎺ュ彛銆佸绾︽垨 architecture facts 鍙戠敓鍙樺寲锛岃繕闇€瑕佽繍琛岋細

```bash
wsl -d Ubuntu-24.04 -- bash -lc 'cd /mnt/d/repo/spring-ai-ascend && ./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts'
```
