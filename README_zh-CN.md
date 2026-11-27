# Material Files（双栏离线修改版）

[English](README.md)

[![Android CI & Release](https://github.com/MangTianYa/MaterialFiles/actions/workflows/android.yml/badge.svg)](https://github.com/MangTianYa/MaterialFiles/actions/workflows/android.yml) [![GitHub 发行版](https://img.shields.io/github/v/release/MangTianYa/MaterialFiles)](https://github.com/MangTianYa/MaterialFiles/releases) [![许可证](https://img.shields.io/github/license/MangTianYa/MaterialFiles?color=blue)](LICENSE)

> **GPL-3.0 开源协议修改声明（第 5 条）**：
> 本仓库为基于 [Hai Zhang](https://github.com/zhanghai) 原版开源项目 [Material Files](https://github.com/zhanghai/MaterialFiles) 的非官方修改分支（Fork，2026 年 11 月修订），专注于 **100% 纯本地离线双栏文件管理**、手势高效多选、批量文件工具与增强型代码文本编辑器。
> 本分支严格遵守原项目 [GNU General Public License v3.0](LICENSE) 开源协议，所有修改部分继续以 **GPL-3.0** 协议开源分发。

[<img alt="从 GitHub Releases 下载" src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" width="240">](https://github.com/MangTianYa/MaterialFiles/releases)

## 本分支新增与修改特性

相较于原版上游仓库 [zhanghai/MaterialFiles](https://github.com/zhanghai/MaterialFiles)，本分支主要进行了以下调整与增强：

- **纯离线零联网隐私强化**：
  - 移除 Firebase Analytics / Crashlytics 与 Google Play 服务相关依赖。
  - 在清单中移除 `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE` 权限，并精简移除 FTP 服务及 SMB / SFTP / WebDAV 网络客户端模块，仅专注本地存储、压缩包与 Root 文件管理。
- **双栏并排高效文件管理**：
  - 左右双窗口同步浏览，支持拖动分隔条调整比例（双击恢复 50/50）与当前激活栏指示条。
  - 统一顶部搜索/选区胶囊栏，支持冷启动自动恢复左右栏路径及当前激活窗口状态。
  - 双栏互传带明确方向提示：激活左栏时显示 `复制 →` / `移动 →` / `链接 →`，激活右栏时自动变为 `← 复制` / `← 移动` / `← 链接`；当双栏处于同一目录或对侧只读时自动回退为剪贴板复制/移动，确保按钮随时可用。
- **手势交互与批量功能增强**：
  - **列表项左滑触发多选**：左滑文件或文件夹伴随弹性回弹与震动反馈进入多选模式，不遮挡列表可继续连续勾选。
  - **多选后长按触发菜单**：处于多选状态时，长按任一已选中项目（或点击底部选区胶囊条）即可打开居中双列操作面板。
  - **底部上滑最近访问**：从底部边缘上滑（或点击底部“最近访问”胶囊条）弹出最近访问记录面板，支持 `全部 / 文件 / 文件夹` 筛选，点击文件夹直接跳转目录，点击可编辑文件直接进入内置编辑器。
  - **批量重命名与多选属性**：内置三种批量改名模式（统一编号模板 `{name}`/`{num}`、查找替换、添加前后缀），支持扩展名保留、实时预览及重名冲突校验；多选点击“属性”支持异步递归统计所选文件夹、文件数量及总字节大小。
- **内置代码与文本编辑器增强**：
  - 支持主流格式语法高亮（JSON、YAML、XML/HTML、Kotlin/Java、Python、JS/TS、C/C++、Go、Rust、Shell、SQL、INI/TOML 等）。
  - 新增一键格式化 JSON / YAML 功能，支持等宽行号联动并修复文本编辑器相关崩溃。
- **Root 适配与 Material You 视觉优化**：
  - 修复 `libsu` / KernelSU（`ksud`）在“自动”和“仅 Root 访问”模式下打开 `/` 与内部存储时的管道异常。
  - 支持 Android 12+ 莫奈动态取色图标与动态主题切换，并内置轻量本地音频播放器。

## 上游核心基础

本修改版完整保留了原作者 Hai Zhang 在上游项目中的底层设计：

- 基于 [Java NIO2 文件 API](https://docs.oracle.com/javase/8/docs/api/java/nio/file/package-summary.html) 与 Linux 系统调用实现，原生支持符号链接、POSIX 权限、SELinux 上下文与非 UTF-8 文件名。
- 支持压缩包查看/解压/创建、面包屑导航、Root 及 Shizuku 授权，以及 `ViewModel` / `LiveData` 现代生命周期架构。

> 原版上游项目：https://github.com/zhanghai/MaterialFiles  
> 本分支与 GitHub Actions 构建发布页：https://github.com/MangTianYa/MaterialFiles/releases

## 使用说明

- 请勿使用本应用替换系统 AOSP DocumentsUI（外置存储授权依赖系统 DocumentsUI）。
- 本分支使用独立签名密钥构建，与原版 Play / F-Droid 安装包签名不同，不能覆盖原版直接升级，需卸载原版后安装。

## 许可证

    Copyright (C) 2018 Hai Zhang
    Modifications Copyright (C) 2026 MangTianYa

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
