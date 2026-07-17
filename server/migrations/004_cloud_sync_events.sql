create table if not exists cloud_sync_events (
    id bigint generated always as identity primary key,
    account_id uuid not null references accounts(id) on delete cascade,
    event_type text not null
        check (event_type in ('download', 'upload', 'conflict', 'error')),
    revision bigint,
    schema_version integer,
    mod_version text not null default '',
    detail text not null default '',
    created_at timestamptz not null default now()
);

create index if not exists cloud_sync_events_account_created_idx
    on cloud_sync_events (account_id, created_at desc, id desc);

create index if not exists cloud_sync_events_type_created_idx
    on cloud_sync_events (event_type, created_at desc);

create index if not exists cloud_sync_events_account_type_created_idx
    on cloud_sync_events (account_id, event_type, created_at desc);
