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

## 实现说明（本次交付）

### 功能

- **历史输入**：`Operation`（调用时间、返回时间、进程、操作类型、参数、返回值）+ `History`。
- **线性一致性判定**：`Checker.checkLinearizable(history, spec)`，成功时返回见证排序 `witnessOrder`。
- **顺序一致性判定**：`Checker.checkSequentiallyConsistent(...)`，用于区分 SC 与 LC
  （见 `LinearizabilityCheckerTest#sequentiallyConsistentButNotLinearizable`）。
- **反例输出**：不一致时返回 `minimalConflict` —— 一个 1-最小冲突集合：
  该子集本身仍不可串行化，但移除其中任意一个操作后就可串行化。
- **统计**：`CheckStats` 含操作数、搜索节点数、剪枝命中数（区间约束 / 返回值）、
  记忆化命中数与判定耗时。
- **内置实现自动校验**：`ConcurrentCounter` / `ConcurrentQueue` 配合
  `OperationRecorder`（`RecordingCounter` / `RecordingQueue`）把真实并发执行
  记录成历史后送入检查器（见 `ConcurrentStructuresTest`）。
- **内置顺序规约**：`CounterSpec`、`QueueSpec`、`RegisterSpec`。

### 算法

采用 Wing & Gong (1993) 风格的回溯搜索：

1. **前驱约束**：线性一致性下，若 `a.response < b.invoke` 则 `a` 必须排在 `b` 前
   （实时序）；顺序一致性下，同进程内按程序序约束。
2. **区间约束剪枝**：每个搜索节点只尝试所有前驱均已线性化的操作，而不是枚举
   全部 `n!` 种排列。
3. **返回值剪枝**：候选操作在顺序规约状态机上执行，规约返回值与记录返回值
   不一致立即剪枝。
4. **记忆化**：缓存已证明失败的 `(规约状态, 已线性化集合)` 对，每对至多展开一次。
5. **最小冲突集合**：判定失败后用贪心删除做 1-最小化（`O(n)` 次子检查）。

### 复杂度

- 朴素全排列为 `O(n!)`；记忆化后上界为子集格 `O(2^n · n)` 次规约步进。
- 区间基本不重叠的历史上，前驱剪枝使每节点只剩一个候选，实际接近 `O(n)`。
- 冲突最小化额外进行 `O(n)` 次子检查。
- 工程限制：搜索节点使用 64 位位掩码，单个历史最多 64 个操作
  （`History.MAX_OPERATIONS`）。

### 代码结构

```
com.example.gsb.lin
├── Operation / History          历史数据模型
├── SequentialSpec               顺序规约接口（状态机）
├── Checker                      搜索引擎（LC / SC 两种模式）
├── CheckResult / CheckStats     结果与统计
├── ConsistencyModel             一致性模型枚举
├── ConcurrentCounter / ConcurrentQueue   内置并发实现
├── spec/                        CounterSpec / QueueSpec / RegisterSpec
└── recorder/                    OperationRecorder 及 Recording* 包装器
```
