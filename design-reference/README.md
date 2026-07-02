# design-reference

GEPA 进化搜索幸存的 **P8 极小内核参考实现** + **流式 Pregel 原型**。

## 这是什么

`com.openjiuwen.reference.gepa3` 包含结论 AAC（PEV + Sealed Dispatch 极小内核模式）的代码试验产物：

- **`MinimalAgentEngine`**（<800 行纯 Java，零框架）—— 8 条独立进化线收敛到的极小内核。证明 PEV + Sealed Dispatch 可蒸馏进单个类（sealed types `RootCause` 3 态 / `ReplanAction` 3 态 + 纯函数 `diagnose` + switch dispatch）。它是生产嫁接版（`PEVAlphaStrategy` + `DefaultPregelExecutor` + `DefaultVerifier` + `DefaultPlanner`，合计 ~2000 行 + Spring/Reactor）的蒸馏源。
- **`StreamingPregelDemo`** —— 流式 Pregel 执行器原型 v1.1（BSP superstep + 背压），带 B1-B7 治本修复。

## 不是什么

- **非生产交付物**。生产用 `agent-runtime` 模块的 `PEVAlphaStrategy` / `DefaultPregelExecutor`（落地版：拆回多类 + Spring/Reactor + agent-core-java SPI）。
- **不参与 `agent-runtime` 的构建/测试**。本模块独立编译、独立跑测试。

## 为什么保留

作为**可执行设计参考**：跑测试就能看极小内核工作。若 `agent-runtime` 的生产嫁接偏离 P8 不变量（sealed dispatch / 纯函数 diagnose / 3 态映射），本模块的测试仍编码着规范行为，提供回归守门信号。

## 构建

```bash
# 只跑本模块的参考测试（-am 同时构建 agent-runtime 依赖）
./mvnw -pl design-reference -am test
```
