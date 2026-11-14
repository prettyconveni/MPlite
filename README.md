# MPlite

基于开源项目 [MP-Manager](https://github.com/AbdurazaaqMohammed/MP-Manager) 深度定制与增强的 Android 双栏文件管理器与脚本终端工具。

- **应用名称**：`MPlite`
- **应用包名**：`com.system.power`

## 主要特性与增强

### 1. 交互式终端与 `.sh` 脚本运行器
- **完整 PTY 终端引擎**：集成 `termux-terminal-emulator` 与 `termux-terminal-view`，支持交互式命令输入、ANSI 控制与 Root / 普通 Shell 切换。
- **脚本实时交互体验**：
  - 执行 `.sh` 脚本时实时输出运行流程，字体大小统一清晰，可自由复制文本。
  - 底部自带输入栏（发送 / 回车 `↵` / 中断 `Ctrl+C`）与两排快捷按键栏，弹出输入法时自动避让不被遮挡。
- **多类型脚本兼容**：
  - 自动识别文本脚本（`sh` / `bash` / `zsh`）与二进制编译/加密型 `.sh`（如 `shc` 打包的 ELF 可执行文件），在 `noexec` 分区自动临时提权运行。
  - 支持直接运行压缩包内的 `.sh` 脚本，无需手动提前解压。

### 2. 压缩包浏览与解压增强
- 集成 `sevenzipjbinding-android` 与多编码文件名兼容支持（UTF-8 / GBK 等），支持常见压缩格式浏览与解压。
- 简化压缩包内文件操作菜单，聚焦解压流程。
- 完善 `/data` 等 Root 目录下的压缩包自动识别、直接打开与解压目标目录写入。

### 3. Root 自动检测与高性能文件操作
- 启动时自动检测设备 Root 权限，可用时自动提权并开启相关 Root 文件操作设置，无需手动反复配置。
- 同步集成上游高性能原生 `mpio` Root 守护进程，提升大文件复制与权限处理效率。

### 4. 双栏文件管理与实用工具
- 保留原版双栏浏览、书签与历史记录、高级搜索、批量重命名、APK 提取与签名/反编译工具、文本/十六进制/SQLite/PDF/媒体查看等核心能力。

## 从源码构建

```bash
./gradlew :app:assembleFullRelease
```

构建产物位于：
`app/build/outputs/apk/full/release/app-full-release.apk`

## 致谢

- 上游项目：[AbdurazaaqMohammed/MP-Manager](https://github.com/AbdurazaaqMohammed/MP-Manager)
