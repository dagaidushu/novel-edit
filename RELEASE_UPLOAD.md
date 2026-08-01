# GitHub Upload Guide

Commit and push this `noveledit` directory as the Git repository. It contains the Windows source, Cloudflare Worker source, build scripts, licenses, tests, and the update manifest example.

Do not commit the MSI or portable ZIP. GitHub blocks normal Git uploads larger than 100 MB. Upload these files from `D:\Codex\NovelCraft-master\release` as GitHub Release assets instead:

- `NovelEdit-1.0.1.msi`
- `NovelEdit-Windows-x64-portable.zip`
- `SHA256SUMS.txt`

After creating a release tag, run:

```powershell
cd windows
.\packaging\New-UpdateManifest.ps1 -Version 1.0.1 -Repository YOUR_ACCOUNT/YOUR_REPOSITORY
```

Commit the generated root-level `update.json`, or host it through GitHub Pages. Its direct HTTPS URL is what users put in NovelEdit's automatic-update setting.
