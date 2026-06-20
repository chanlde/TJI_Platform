# 产品线文档

这里放每个具体产品线自己的需求、协议和落地说明。新增产品线时先复制 `_template/`，再按产品代码命名目录。

## 当前产品线

| 产品线 | 文档 |
|--------|------|
| 文档模板 | [_template/README.md](_template/README.md) |
| 消防吊桶 | [firebucket/README.md](firebucket/README.md) |
| 光伏清洗 | [solarclean/README.md](solarclean/README.md) |
| 六段抛投 | [droppersixstage/README.md](droppersixstage/README.md) |
| 喊话器 | [speaker/README.md](speaker/README.md) |
| 无线电侦测 | [radiodetection/README.md](radiodetection/README.md) |

## 已在 ProductCatalog 登记但暂未建独立代码目录

| 产品线 | ProductCatalog productCode | 计划代码目录 | 当前状态 |
|--------|----------------------------|--------------|----------|
| 破窗弹 | `GlassBreaker` | `product/breakwindowprojectile` | 已在 `ProductCatalog` 预留，待建产品代码目录后补文档 |
| 探照灯 | `Searchlight` | `product/searchlight` | 已在 `ProductCatalog` 预留，待建产品代码目录后补文档 |

## 新增产品线步骤

```bash
cp -R Doc/product-lines/_template Doc/product-lines/{productCode}
```

复制后至少填写：

- `README.md`：产品定位、当前状态、边界和清单。
- `protocol.md`：MQTT topic、payload、ACK 和 retain 规则。
- `mcu.md`：MCU 职责、状态机和安全约束。
- `server.md`：服务器接口、字段和发布/回滚要求。
- `app.md`：App 代码落点、UI、悬浮窗和测试。

如果某一项当前不需要，也保留文件并写明“不需要”或“待定”，不要直接省略。

每份产品线文档都必须包含：

```text
## 状态

- 状态：active | draft | deprecated
- 产品代码：`{productCode}`
```

`./gradlew checkDocs` 会检查每个真实产品代码目录都有对应产品线文档，也会检查产品线五件套、状态字段、产品代码、模板占位符、本索引和 `ProductCatalog` 预留产品登记是否齐全。
