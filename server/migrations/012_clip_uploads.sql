create table clip_uploads (
  id text primary key,
  account_id uuid references accounts(id) on delete set null,
  original_name text not null,
  byte_size bigint not null check (byte_size > 0),
  content_md5 text not null,
  state text not null default 'pending' check (state in ('pending', 'finalizing', 'ready', 'deleted')),
  duration_seconds double precision,
  width integer,
  height integer,
  created_at timestamptz not null default now(),
  upload_expires_at timestamptz not null,
  expires_at timestamptz not null,
  deleted_at timestamptz,
  purged_at timestamptz,
  staging_cleaned_at timestamptz
);
create index clip_uploads_owner_created_idx on clip_uploads(account_id, created_at desc);
create index clip_uploads_expiry_idx on clip_uploads(expires_at) where purged_at is null;
create index clip_uploads_pending_idx on clip_uploads(upload_expires_at) where purged_at is null;
create index screenshot_uploads_owner_created_idx on screenshot_uploads(account_id, created_at desc)
  where deleted_at is null;
