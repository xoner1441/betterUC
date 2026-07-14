create table if not exists cloud_settings (
    account_id uuid primary key references accounts(id) on delete cascade,
    schema_version integer not null default 1
        check (schema_version > 0),
    revision bigint not null default 1
        check (revision > 0),
    settings jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now(),
    updated_by_version text not null default ''
);

create index if not exists cloud_settings_updated_at_idx
    on cloud_settings (updated_at desc);
