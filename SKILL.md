---
name: float-counter
description: Build, troubleshoot, or adapt 浮记, an Android screen-reading overlay for tracking played and remaining Dou Dizhu cards. Use for APK builds, recognition failures, duplicate plays, or regression checks against gameplay recordings.
---

# 浮记

从 [assets/android-counter](assets/android-counter) 取出 Android 工程。需要构建或修改时，将该目录复制到任务工作区。保留相同包名和签名才能覆盖安装旧版，签名密钥由使用者本地保管。

## 构建

在工程目录使用 Python 3.9+ 与 JDK 17+ 执行 `python build.py`。脚本会获取 Android SDK Platform 35 与 Build Tools 35.0.0，将 APK 输出到 `build/`。只验证 Java 核心时执行 `python build.py --test-only`。

## 排查识别与记账

- `PokerVision.java` 负责从公开画面读取手牌、底牌、出牌和张数。先聚合同排牌，再归属玩家。遇到长牌组被拆开、大小王漏识别或画面变化，优先用实际帧定位。
- `PokerSession.java` 负责牌局状态。各玩家初始 17 张，3 张底牌归地主。自己的出牌不二次扣减，对手一手牌只记一次。区分动画中的半手、已记入牌组淡出的子集，以及清场后新出现的同点数出牌。
- `CounterService.java` 负责采集、排队和悬浮条。用单帧处理耗时、排队延迟和溢出计数区分识别错误与设备吞吐不足。保留对局内自动更新和下一局手动开始的交互。

有录屏时，以原始时间顺序调用与 Android 应用相同的识别和记账代码。按实际画面核对每手的玩家、完整牌点、顺序和最后余牌，另外检查结束状态。将截图回归、录屏回放与手机实测分别报告。未观察到完整牌面时，保留不确定性。
