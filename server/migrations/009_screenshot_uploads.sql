create table if not exists screenshot_uploads (
  id text primary key,
  account_id uuid references accounts(id) on delete set null,
  original_name text not null default '',
  storage_name text not null unique,
  byte_size integer not null check (byte_size > 0),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  deleted_at timestamptz
);

create index if not exists screenshot_uploads_expiry_idx
  on screenshot_uploads(expires_at)
  where deleted_at is null;
