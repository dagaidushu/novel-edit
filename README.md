# NovelEdit Windows 1.0.2

GitHub 上传说明见 `RELEASE_UPLOAD.md`。安装包和便携版请作为 GitHub Release 附件上传，不要提交进 Git 历史。

Windows 桌面版源码位于 `windows`，可直接使用的安装包和便携包位于 `release`。

## 使用

- 安装版：运行 `release\NovelEdit-1.0.2.msi`。
- 便携版：解压 `release\NovelEdit-Windows-x64-portable.zip`，运行 `NovelEdit-Portable.cmd`。
- 安装版数据保存在 `%APPDATA%\NovelEdit`；便携版数据保存在解压目录的 `data` 文件夹。

## 云同步

在应用的“设置 - 云同步”中点击“新建云同步”，保存显示的恢复码。恢复码是访问加密保险箱的唯一凭证，丢失后无法找回云端内容。

- 同步内容会在本机先使用恢复码派生的 AES-GCM 密钥加密，再上传到 Cloudflare D1；服务端不保存正文的明文或 API Key。
- 打开作品后点击“同步当前作品”上传；另一台电脑输入恢复码后点击“恢复缺失作品”。
- 两台设备同时改同一部作品时，应用拒绝覆盖，并把云端版本导入为“云端冲突副本”，保留本地原作。

## 自动更新

打包后执行 `windows\packaging\New-UpdateManifest.ps1 -Version 1.0.2 -Repository YOUR_ACCOUNT/YOUR_REPOSITORY`，即可从正式 MSI/ZIP 自动生成 GitHub Release 使用的 `update.json`。上传发布包和该 JSON 后，将其 HTTPS 地址填入应用“设置 - 自动更新”；网络受限用户可选填自己本机的 HTTP 代理地址。

`update.json` 必须同时提供 MSI 和便携 ZIP 各自的校验值，示例见 `release\update.json.example`。应用下载后会校验 SHA-256，校验失败的文件不会保留或打开。

## 从源码构建

项目内置 JDK 17，执行：

```powershell
cd windows
.\build-windows.ps1
```

构建脚本会运行测试，并重新生成 MSI、便携 ZIP 和 SHA-256 校验文件。

## 数据与许可

- 作品保存于本地 SQLite 数据库，API Key 和云同步恢复码均使用 Windows DPAPI 加密保存。
- 支持旧 Android v1 备份和 NovelEdit 跨平台 v2 备份。
- 支持 TXT、Markdown、DOCX、EPUB、PDF 导入，以及 Markdown、DOCX、EPUB、PDF 导出。
- 本项目遵循随附的 Apache License 2.0；第三方组件声明见 `windows\THIRD_PARTY_NOTICES.md`。
