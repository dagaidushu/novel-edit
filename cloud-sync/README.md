# NovelEdit Cloud Sync

Cloudflare Worker for encrypted, single-user NovelEdit project synchronization using the free Workers/D1 plan.

The Worker never receives plaintext project content. The desktop application encrypts each project backup before upload. D1 keeps revision metadata, SHA-256 hashes and encrypted 512 KB payload chunks. This avoids R2 billing account setup while remaining suitable for personal multi-device synchronization.

Deployment requires only the `noveledit-sync` D1 database configured in `wrangler.jsonc`.
