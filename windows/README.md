# NovelEdit Windows 1.0.3

NovelEdit 是本地优先的 Windows 10/11 x64 长篇小说创作工具。安装版数据保存在 `%APPDATA%\NovelEdit`；便携版数据保存在解压目录的 `data`。

## 云同步

在应用“设置 - 云同步”中创建云同步并保存恢复码。作品会先在本机使用 AES-GCM 加密，再上传到 Cloudflare D1；服务端不保存明文正文或 API Key。另一台电脑输入恢复码后可恢复缺失作品。

同时编辑同一作品时，云端版本会被导入为“云端冲突副本”，原有本地作品不会被覆盖。

## 自动更新

打包后执行下方命令，可从正式 MSI/ZIP 自动生成 GitHub Release 使用的 `update.json`：

```powershell
.\packaging\New-UpdateManifest.ps1 -Version 1.0.2 -Repository YOUR_ACCOUNT/YOUR_REPOSITORY -Notes "更新说明"
```

上传两个发布包和生成的 `update.json` 到 GitHub Release、Pages 或其他 HTTPS 静态地址，并在应用“设置 - 自动更新”中填入该 JSON 地址。MSI 与便携 ZIP 都有独立 SHA-256 校验值；校验失败的更新包不会打开。

## 构建

项目包含 JDK 17。执行：

```powershell
cd windows
.\build-windows.ps1
```

脚本会运行测试并在 `..\release` 生成 MSI、便携 ZIP、校验文件和更新清单示例。

## 数据与许可

API Key 与云同步恢复码均使用 Windows DPAPI 加密保存。支持 Android v1 与 NovelEdit v2 项目备份，支持 TXT、Markdown、DOCX、EPUB、PDF 导入及 Markdown、DOCX、EPUB、PDF 导出。

本项目遵循 Apache License 2.0；第三方组件声明见 `THIRD_PARTY_NOTICES.md`。
