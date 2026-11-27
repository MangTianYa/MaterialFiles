# Privacy Policy (Modified Offline Fork)

> **GPL-3.0 Modification Notice**:
> This document applies to the unofficial modified fork [MangTianYa/MaterialFiles](https://github.com/MangTianYa/MaterialFiles) (modified November 2026), derived from [Material Files](https://github.com/zhanghai/MaterialFiles) originally created by Hai Zhang under GNU GPL v3.0.

## 100% Offline Operation & Zero Data Collection

In this modified fork:

- **No Network Permissions**: `android.permission.INTERNET`, `android.permission.ACCESS_NETWORK_STATE`, and `android.permission.ACCESS_WIFI_STATE` are explicitly removed in `AndroidManifest.xml`.
- **No Analytics or Crash Reporting SDKs**: Google Play Services, Google Analytics for Firebase, and Firebase Crashlytics were completely removed from the build configuration and source code.
- **No NAS / Remote Server Code**: FTP server, SMB, SFTP, and WebDAV modules were removed; the app operates solely on local storage, archives, and user-authorized Root/SAF providers.

## On-Device Storage Only

Settings, bookmarks, pane paths, and recent access history (`key_recent_access_entries`) are stored locally on your device via Android `SharedPreferences` and are never transmitted outside your device. You can clear recent access history at any time inside the app's Recent Access panel.

## Permissions Used

Permissions declared by the app (such as `MANAGE_EXTERNAL_STORAGE` / storage access, optional `POST_NOTIFICATIONS` for file jobs, `REQUEST_INSTALL_PACKAGES` when opening APK installers, and optional Root / Shizuku integration) are used strictly on-device for user-initiated file operations.

## Contact

For questions specific to this fork, please open an issue at https://github.com/MangTianYa/MaterialFiles/issues.  
For the original upstream project by Hai Zhang, see https://github.com/zhanghai/MaterialFiles.
