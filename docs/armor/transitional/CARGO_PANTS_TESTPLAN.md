# 多袋工装裤 测试计划

| # | 场景 | 预期 |
|---|---|---|
| 1 | 穿戴，连续放置 20 个 torch | 约 3 个被返还（±RNG），日志可见 roll 值 |
| 2 | 穿戴，触发返还后 10s 内再放 | CD 中不触发（日志无输出） |
| 3 | 穿戴，CD 过后再放 | 可再次触发 |
| 4 | 穿戴，放置 soul_torch | 不触发（不在 target 范围） |
| 5 | 不穿戴，放置 torch | 不触发 |
| 6 | 创造模式穿戴放置 torch | 不触发 |
| 7 | 放置最后 1 个 torch 且命中返还 | stack 恢复为 1（不会凭空消失） |
| 8 | 防火 + 铁砧 | 同通用 |
| 9 | DEBUG=true 日志格式 | 字段完整：player, itemId, rawCount, outCount, roll, cdRemaining |
| 10 | ./gradlew build | 编译通过 |
