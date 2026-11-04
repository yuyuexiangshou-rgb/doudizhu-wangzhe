# 浮记 Float Counter

浮记是安卓斗地主记牌悬浮工具。它在用户授权屏幕采集后，于本地识别公开画面中的牌面并显示余牌。工程不申请联网权限。

- Codex skill 入口为 [SKILL.md](SKILL.md)。将整个 `float-counter` 目录作为 skill 使用，源码随 skill 存放。
- Android 工程位于 [assets/android-counter](assets/android-counter)。需要 Android 8.0 或更新版本。构建和测试步骤见 skill 入口。

当前源码对应 v0.5.0。两段可见对局录屏的回放匹配了人工标注的 9 手出牌。安卓实机仍需对照验证。

识牌资源中的文本模板是从样本画面及系统字体提取的二值字形。原始截图、录屏、字体文件、调试签名密钥和本地构建缓存均未纳入发布预览。

项目自有源码和文档采用 [MIT 许可证](LICENSE)。
