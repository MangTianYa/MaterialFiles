# Material Files (Dual-Pane Offline Fork)

[本文中文版](README_zh-CN.md)

[![Android CI & Release](https://github.com/MangTianYa/MaterialFiles/actions/workflows/android.yml/badge.svg)](https://github.com/MangTianYa/MaterialFiles/actions/workflows/android.yml) [![GitHub release](https://img.shields.io/github/v/release/MangTianYa/MaterialFiles)](https://github.com/MangTianYa/MaterialFiles/releases) [![License](https://img.shields.io/github/license/MangTianYa/MaterialFiles?color=blue)](LICENSE)

> **GPL-3.0 Modification Notice (Section 5)**:
> This repository is an unofficial, modified fork of [Material Files](https://github.com/zhanghai/MaterialFiles) originally created by [Hai Zhang](https://github.com/zhanghai). Modified in November 2026 to focus on **100% offline local/root file management**, side-by-side **dual-pane workflows**, gesture-first multi-selection, batch utilities, and an enhanced built-in code editor.
> In compliance with the [GNU General Public License v3.0](LICENSE), all source code and modifications in this fork remain licensed under **GPL-3.0**.

[<img alt="Get it on GitHub Releases" src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" width="240">](https://github.com/MangTianYa/MaterialFiles/releases)

## Fork Changes & Features

Compared with upstream [zhanghai/MaterialFiles](https://github.com/zhanghai/MaterialFiles), this fork introduces the following changes:

- **100% Offline & Privacy-Hardened**:
  - Removed Firebase Analytics / Crashlytics (`google-services.json`, Play Services dependencies).
  - Removed `INTERNET`, `ACCESS_NETWORK_STATE`, and `ACCESS_WIFI_STATE` permissions (`tools:node="remove"`), as well as FTP server / SMB / SFTP / WebDAV network modules.
- **Dual-Pane Side-by-Side Architecture**:
  - Resizable left/right panes with draggable divider (double-tap to reset to 50/50) and active-pane indicator.
  - Unified top search/selection header pills and persistent pane directory + active-pane restoration across cold restarts.
  - Opposite-pane directional actions (`Copy →` / `Move →` / `Link →` when left pane is active, `← Copy` / `← Move` / `← Link` when right pane is active), plus seamless clipboard fallback when both panes share the same directory or the opposite pane is read-only.
- **Gesture & Multi-Selection Enhancements**:
  - **Swipe left** on any file or folder item to enter non-blocking continuous multi-selection with elastic slide & haptic feedback.
  - **Long-press** any selected item (or tap the bottom selection pill) to open the centered 2-column action menu.
  - **Swipe up** from the bottom edge (or tap the bottom "Recent" pill) to open the **Recent Access History** sheet with `All` / `Files` / `Folders` filters, one-tap directory navigation, and direct launch into the built-in editor.
  - **Batch Rename**: Numbered templates (`{name}`, `{num}`), Find & Replace, and Prefix/Suffix modes with live conflict/extension preview and atomic swap handling.
  - **Multi-Selection Properties**: Asynchronous recursive calculation of total folders, files, byte size, and modified time range.
- **Enhanced Built-in Code / Text Editor**:
  - Multi-language syntax highlighting covering JSON, YAML, XML/HTML, Kotlin/Java, Python, JS/TS, C/C++, Go, Rust, Shell, SQL, INI/TOML, etc.
  - One-click **JSON / YAML formatting** and synchronized monospace line numbers.
- **Root (`libsu`) & Material You Polish**:
  - Fixed `libsu` / KernelSU (`ksud`) `Broken pipe` issues under Automatic and Root-only modes.
  - Added Android 12+ Material You dynamic colors (`DynamicColorPreference`), themed launcher icon, dynamic light/dark navigation drawer, and built-in local audio player.

## Core Upstream Foundations

This fork builds directly upon the clean engineering of [Material Files](https://github.com/zhanghai/MaterialFiles) by Hai Zhang:

- Open source, lightweight, and clean Material Design.
- Built upon [Java NIO2 File API](https://docs.oracle.com/javase/8/docs/api/java/nio/file/package-summary.html) and Linux syscalls (preserving symbolic links, Linux permissions, SELinux context, and non-UTF-8 paths) rather than parsing `ls` output.
- Breadcrumbs, archive viewing/extraction/creation, Root & Shizuku support, and modern `ViewModel` / `LiveData` lifecycle architecture.

> Upstream repository & official Play / F-Droid builds: https://github.com/zhanghai/MaterialFiles
> Fork source & GitHub CI releases: https://github.com/MangTianYa/MaterialFiles

## Inclusion Notice

- Do not replace AOSP DocumentsUI with this app (external storage/SAF permissions rely on DocumentsUI).
- Because this fork is signed with a separate release key from upstream Play/F-Droid builds, it cannot update in-place over upstream installations without uninstalling first.

## License

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
