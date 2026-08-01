export interface Env { DB: D1Database; }

type ProjectRow = { project_id: string; revision: number; payload_hash: string; size_bytes: number; updated_at: number };
type ChunkRow = { payload: unknown };
const CHUNK_SIZE = 512 * 1024;

const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), {
  status,
  headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
});
const sha256 = async (value: ArrayBuffer | string) => {
  const data = typeof value === "string" ? new TextEncoder().encode(value) : value;
  return [...new Uint8Array(await crypto.subtle.digest("SHA-256", data))].map((byte) => byte.toString(16).padStart(2, "0")).join("");
};
const validId = (value: string) => /^[A-Za-z0-9_-]{16,128}$/.test(value);

async function authenticate(request: Request, env: Env, vaultId: string): Promise<Response | null> {
  const token = request.headers.get("authorization")?.replace(/^Bearer\s+/i, "").trim();
  if (!token) return json({ error: "missing_sync_key" }, 401);
  const vault = await env.DB.prepare("SELECT secret_hash FROM vaults WHERE id=?").bind(vaultId).first<{ secret_hash: string }>();
  if (!vault || vault.secret_hash !== await sha256(token)) return json({ error: "invalid_sync_key" }, 403);
  return null;
}

async function createVault(request: Request, env: Env): Promise<Response> {
  const body = await request.json<{ vaultId?: string }>().catch(() => ({}));
  const vaultId = body.vaultId?.trim() ?? "";
  const token = request.headers.get("authorization")?.replace(/^Bearer\s+/i, "").trim() ?? "";
  if (!validId(vaultId) || token.length < 32) return json({ error: "invalid_vault_credentials" }, 400);
  const existing = await env.DB.prepare("SELECT id FROM vaults WHERE id=?").bind(vaultId).first();
  if (existing) return json({ error: "vault_exists" }, 409);
  await env.DB.prepare("INSERT INTO vaults(id,secret_hash,created_at) VALUES(?,?,?)").bind(vaultId, await sha256(token), Date.now()).run();
  return json({ vaultId, created: true }, 201);
}

async function listProjects(request: Request, env: Env, vaultId: string): Promise<Response> {
  const denied = await authenticate(request, env, vaultId);
  if (denied) return denied;
  const rows = await env.DB.prepare("SELECT project_id,revision,payload_hash,size_bytes,updated_at FROM projects WHERE vault_id=? ORDER BY updated_at DESC").bind(vaultId).all<ProjectRow>();
  return json({ projects: rows.results ?? [] });
}

async function putProject(request: Request, env: Env, vaultId: string, projectId: string): Promise<Response> {
  const denied = await authenticate(request, env, vaultId);
  if (denied) return denied;
  if (!validId(projectId)) return json({ error: "invalid_project_id" }, 400);
  const expected = Number(request.headers.get("if-match") ?? "0");
  if (!Number.isInteger(expected) || expected < 0) return json({ error: "invalid_revision" }, 400);
  const existing = await env.DB.prepare("SELECT revision,payload_hash FROM projects WHERE vault_id=? AND project_id=?").bind(vaultId, projectId).first<{ revision: number; payload_hash: string }>();
  const actual = existing?.revision ?? 0;
  if (actual !== expected) return json({ error: "revision_conflict", revision: actual, payloadHash: existing?.payload_hash ?? "" }, 409);
  const payload = await request.arrayBuffer();
  if (payload.byteLength === 0) return json({ error: "empty_payload" }, 400);
  const revision = actual + 1;
  const payloadHash = await sha256(payload);
  const updatedAt = Date.now();
  const chunks: ArrayBuffer[] = [];
  for (let offset = 0; offset < payload.byteLength; offset += CHUNK_SIZE) chunks.push(payload.slice(offset, Math.min(offset + CHUNK_SIZE, payload.byteLength)));
  const statements: D1PreparedStatement[] = [
    env.DB.prepare("DELETE FROM project_chunks WHERE vault_id=? AND project_id=?").bind(vaultId, projectId),
    env.DB.prepare("INSERT INTO projects(vault_id,project_id,revision,payload_hash,size_bytes,updated_at) VALUES(?,?,?,?,?,?) ON CONFLICT(vault_id,project_id) DO UPDATE SET revision=excluded.revision,payload_hash=excluded.payload_hash,size_bytes=excluded.size_bytes,updated_at=excluded.updated_at").bind(vaultId, projectId, revision, payloadHash, payload.byteLength, updatedAt),
    ...chunks.map((chunk, ordinal) => env.DB.prepare("INSERT INTO project_chunks(vault_id,project_id,revision,ordinal,payload) VALUES(?,?,?,?,?)").bind(vaultId, projectId, revision, ordinal, chunk)),
  ];
  await env.DB.batch(statements);
  return json({ projectId, revision, payloadHash, updatedAt });
}

async function getProject(request: Request, env: Env, vaultId: string, projectId: string): Promise<Response> {
  const denied = await authenticate(request, env, vaultId);
  if (denied) return denied;
  const project = await env.DB.prepare("SELECT revision,payload_hash,updated_at FROM projects WHERE vault_id=? AND project_id=?").bind(vaultId, projectId).first<{ revision: number; payload_hash: string; updated_at: number }>();
  if (!project) return json({ error: "project_not_found" }, 404);
  const chunks = await env.DB.prepare("SELECT payload FROM project_chunks WHERE vault_id=? AND project_id=? AND revision=? ORDER BY ordinal").bind(vaultId, projectId, project.revision).all<ChunkRow>();
  if (!chunks.results?.length) return json({ error: "payload_not_found" }, 500);
  const payload = new Blob(chunks.results.map((chunk) => {
    if (chunk.payload instanceof ArrayBuffer) return new Uint8Array(chunk.payload);
    if (ArrayBuffer.isView(chunk.payload)) return new Uint8Array(chunk.payload.buffer, chunk.payload.byteOffset, chunk.payload.byteLength);
    return Uint8Array.from(chunk.payload as number[]);
  }));
  return new Response(payload, { headers: { "content-type": "application/octet-stream", "cache-control": "no-store", "etag": `\"${project.payload_hash}\"`, "x-noveledit-revision": String(project.revision), "x-noveledit-updated-at": String(project.updated_at) } });
}

export default {
  async fetch(request, env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") return json({ status: "ok" });
    if (request.method === "POST" && url.pathname === "/v1/vaults") return createVault(request, env);
    const match = url.pathname.match(/^\/v1\/vaults\/([A-Za-z0-9_-]+)\/projects(?:\/([A-Za-z0-9_-]+))?$/);
    if (!match) return json({ error: "not_found" }, 404);
    const [, vaultId, projectId] = match;
    if (!projectId && request.method === "GET") return listProjects(request, env, vaultId);
    if (projectId && request.method === "PUT") return putProject(request, env, vaultId, projectId);
    if (projectId && request.method === "GET") return getProject(request, env, vaultId, projectId);
    return json({ error: "method_not_allowed" }, 405);
  },
} satisfies ExportedHandler<Env>;
