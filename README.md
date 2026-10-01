# 线性一致性检查器

Pair-wise GSB 标注任务仓库（第 16 批 / 241）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们的并发数据结构号称线性一致，但偶尔出现反直觉的返回，需要一个工具来判断历史记录是否可解释。请从零实现一个线性一致性检查器。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持输入并发操作历史：每个操作有调用时间、返回时间、操作类型与参数、返回值；2) 支持判定该历史是否线性一致：尝试找到一种把所有操作排序的方式，使串行执行结果与实际返回值一致；3) 支持搜索优化：按操作区间约束剪枝，避免全排列，需说明算法与复杂度；4) 不一致时必须输出反例：指出哪几个操作无法排序，并给出最小冲突集合；5) 支持顺序一致但非线性的历史识别（用于区分两种一致性），需有测试用例；6) 支持对内置的并发计数器与队列实现自动校验，把真实执行记录转换后送入检查器；7) 提供统计：操作数、搜索节点数、判定耗时与剪枝命中数；8) 测试覆盖线性一致历史通过、非一致历史报反例、顺序一致但非线性、搜索剪枝与统计；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。

---

## 实现说明（本次迭代新增）

### 模块结构

```
com.example.gsb.lincheck
├── model     Operation（调用/返回时间、方法、参数、返回值）、History
├── spec      SequentialSpec 顺序规约接口；内置 CounterSpec / QueueSpec
├── check     LinearizabilityChecker、SequentialConsistencyChecker、
│             ConflictMinimizer（ddmin）、CheckReport、Stats、Verdict
├── harness   HistoryRecorder、ExecutionDriver（真实多线程执行并记录历史）、
│             SynchronizedCounter / SynchronizedQueue（正确实现）、
│             GatedRacyCounter（用栅栏确定性制造非线性一致历史的错误实现）
└── Main      端到端演示：真实执行 → 记录 → 检查 → 打印报告
```

### 判定算法（要求 3）

**线性一致性**（Herlihy–Wing）：历史可线性化，当且仅当存在操作的全序 π，使得
(a) π 与实时偏序相容（`a.responseTime < b.invocationTime` ⟹ a 排在 b 前）；
(b) 按 π 串行重放顺序规约，每个操作的实际返回值都被接受。

不枚举 n! 全排列，而是采用 **Wing–Gong 事件扫描回溯**：

1. 把 2n 个调用/返回事件按时间排序（时间相同则调用先于返回，使零长度操作可在其唯一时刻线性化）。
2. DFS 扫描事件：遇到调用事件，操作进入"待线性化"集合 `calls`；遇到返回事件时，
   必须在该时刻之前（含）把该操作线性化——即**每个操作只能在其活跃区间
   `[invocationTime, responseTime]` 内被线性化**。这就是区间约束剪枝：
   所有违反实时偏序的排列在生成前就被剪掉，根本不会进入搜索。
3. 在返回事件点，可线性化 `calls` 中任意一个挂起操作（规约 `step` 拒绝则剪枝，
   计入 `specRejections`）。
4. **记忆化**：搜索状态以三元组 `(事件位置, calls 集合, 抽象规约状态)` 为键。
   不同的线性化前缀若收敛到同一三元组，则未来可能性完全相同，
   第二次到达直接剪枝（计入 `memoHits`）。

**复杂度**：设最大并发度（任意时刻活跃操作数）为 `c`，可达抽象状态数为 `|S|`。
记忆化键总数不超过 `2n · 2^c · |S|`，每个键至多展开一次，因此
时间复杂度为 `O(n · 2^c · |S| · step)`：低竞争历史（c 小）下接近线性；
一般问题本身 NP 难，最坏情况指数不可避免。检查器提供节点预算
（`withMaxNodes`），超限返回 `INCONCLUSIVE` 而非挂死。

**顺序一致性**（Lamport）：与线性一致性唯一的区别是偏序——只保留
**线程内程序序**（同线程操作按调用先后排序），允许跨线程操作越过实时顺序重排。
因此"线性一致 ⟹ 顺序一致"，反之不然；测试中用经典 Herlihy–Wing 队列历史
（`enq(x)` 实时先于 `enq(y)`，却 `deq()` 返回 `y`）区分两种一致性。

### 反例与最小冲突集（要求 4）

判定不一致时，用 **ddmin 增量调试**（Zeller）以检查器本身为预言机，
反复二分/补集删除操作，输出 **1-极小冲突集**：该子集本身仍不一致，
但再删掉任意一个操作就一致。报告中逐条列出这些无法排序的操作
（含线程、方法、参数、返回值与时间区间）。

### 统计（要求 7）

`CheckReport.stats()` 提供：操作数 `operations`、搜索节点数 `nodes`、
判定耗时 `elapsedNanos`、剪枝命中数（`memoHits` 记忆化命中 +
`specRejections` 规约拒绝）。`CheckReport.describe()` 输出人类可读报告。

### 使用示例

```java
History history = ...; // 手工构造，或由 ExecutionDriver 记录真实执行
CheckReport report = new LinearizabilityChecker<>(new CounterSpec()).check(history);
if (report.verdict() == Verdict.INCONSISTENT) {
    System.out.println(report.describe()); // 含最小冲突集
}

// 真实执行自动校验
SynchronizedCounter counter = new SynchronizedCounter();
History h = ExecutionDriver.run(4, (i, name, rec) -> {
    for (int k = 0; k < 25; k++) rec.record(name, "inc", null, counter::incrementAndGet);
});
assert new LinearizabilityChecker<>(new CounterSpec()).check(h).isConsistent();
```
