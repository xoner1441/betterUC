create table if not exists cloud_settings_history (
    id bigint generated always as identity primary key,
    account_id uuid not null references accounts(id) on delete cascade,
    revision bigint not null check (revision > 0),
    schema_version integer not null check (schema_version > 0),
    settings jsonb not null default '{}'::jsonb,
    recorded_at timestamptz not null default now(),
    updated_by_version text not null default '',
    action text not null default 'update'
        check (action in ('update', 'restore', 'reset')),
    restored_from_revision bigint
);

create index if not exists cloud_settings_history_account_recorded_idx
    on cloud_settings_history (account_id, recorded_at desc, id desc);

insert into cloud_settings_history (
    account_id, revision, schema_version, settings, recorded_at, updated_by_version, action
)
select
    account_id, revision, schema_version, settings, updated_at, updated_by_version, 'update'
from cloud_settings current_settings
where not exists (
    select 1
    from cloud_settings_history history
    where history.account_id = current_settings.account_id
      and history.revision = current_settings.revision
      and history.action = 'update'
);
