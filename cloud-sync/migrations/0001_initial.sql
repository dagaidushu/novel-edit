CREATE TABLE IF NOT EXISTS vaults (
  id TEXT PRIMARY KEY,
  secret_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS projects (
  vault_id TEXT NOT NULL,
  project_id TEXT NOT NULL,
  revision INTEGER NOT NULL,
  payload_hash TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (vault_id, project_id),
  FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS projects_vault_updated ON projects(vault_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS project_chunks (
  vault_id TEXT NOT NULL,
  project_id TEXT NOT NULL,
  revision INTEGER NOT NULL,
  ordinal INTEGER NOT NULL,
  payload BLOB NOT NULL,
  PRIMARY KEY (vault_id, project_id, revision, ordinal),
  FOREIGN KEY (vault_id, project_id) REFERENCES projects(vault_id, project_id) ON DELETE CASCADE
);
