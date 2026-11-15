# fakeScreen

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/Xposed%20API-Modern%20API%20102-brightgreen)](https://github.com/libxposed/api)
[![Platform](https://img.shields.io/badge/Platform-Android%209%2B-orange)](https://developer.android.com/)

**FakeScreen** 是一款基于Xposed 架构开发的 Android 模块，实现熄屏挂机功能。

---

## 核心特性
模块激活并开启功能后按下电源键仅会熄灭屏幕。

---
## 兼容性
已在以下设备上进行验证

| 设备         | 系统                | Android版本  |
|:-----------|:------------------|:-----------|
| Pixel 6    | GrapheneOS        | Android 16 |
| MiPad6 Pro | HyperOS 2.0.213.0 | Android 15 |

⚠️警告：在多屏幕设备上，模块可能无法正常工作。如果导致屏幕无法正常开启，重启设备后即可恢复，预计不会添加对多屏幕设备的兼容。
---
## 使用说明

1. **安装与激活**：
    * 下载安装 APK 后，在 LSPosed 管理器中启用 **FakeScreen** 模块。
    * **作用域说明**：选择 **系统框架**。
    * 重启设备。

2. **状态开关**：
    * 打开 **FakeScreen** App，切换主界面开关状态。
---

## 🛠️ 项目构建 (Building)

你可以使用 Android Studio 打开项目并直接构建，或者通过 Gradle 命令行构建 release APK：

```bash
git clone https://github.com/chuyb-re/fakeScreen.git
cd fakeScreen
./gradlew assembleRelease