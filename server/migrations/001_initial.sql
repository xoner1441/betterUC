create table if not exists accounts (
    id uuid primary key,
    minecraft_name text not null,
    minecraft_uuid text not null default '',
    faction text not null default '',
    role text not null default 'user'
        check (role in ('user', 'vip', 'partner', 'helper', 'admin')),
    status text not null default 'active'
        check (status in ('active', 'revoked')),
    created_at timestamptz not null default now(),
    created_by text not null default 'server',
    activated_at timestamptz,
    revoked_at timestamptz,
    reset_at timestamptz,
    updated_at timestamptz,
    last_seen_at timestamptz,
    last_stats_at timestamptz,
    last_server text not null default '',
    last_channel text not null default '',
    last_version text not null default ''
);

create index if not exists accounts_minecraft_name_lower_idx
    on accounts (lower(minecraft_name));
create index if not exists accounts_minecraft_uuid_idx
    on accounts (minecraft_uuid)
    where minecraft_uuid <> '';
create index if not exists accounts_status_role_idx
    on accounts (status, role);

create table if not exists access_tokens (
    account_id uuid primary key references accounts(id) on delete cascade,
    token_hash text not null unique,
    token_prefix text not null default '',
    updated_at timestamptz not null default now()
);

create table if not exists web_credentials (
    account_id uuid primary key references accounts(id) on delete cascade,
    password_hash text,
    password_salt text,
    password_set_at timestamptz,
    password_cleared_at timestamptz,
    sessions_invalid_after timestamptz,
    last_panel_login_at timestamptz
);

create table if not exists player_stats (
    account_id uuid primary key references accounts(id) on delete cascade,
    bank_money bigint,
    cash_money bigint,
    faction_display text not null default '',
    houses text not null default '',
    loyalty_bonus bigint,
    play_time_hours bigint,
    votepoints bigint,
    warns text not null default '',
    updated_at timestamptz not null default now()
);

create table if not exists stats_history (
    account_id uuid not null references accounts(id) on delete cascade,
    recorded_at timestamptz not null,
    bank_money bigint,
    cash_money bigint,
    faction_display text not null default '',
    houses text not null default '',
    loyalty_bonus bigint,
    play_time_hours bigint,
    votepoints bigint,
    warns text not null default '',
    primary key (account_id, recorded_at)
);

create index if not exists stats_history_account_recorded_idx
    on stats_history (account_id, recorded_at desc);

create table if not exists discord_links (
    account_id uuid primary key references accounts(id) on delete cascade,
    discord_id text not null unique,
    linked_at timestamptz,
    unlinked_at timestamptz
);

create table if not exists audit_log (
    id bigint generated always as identity primary key,
    account_id uuid references accounts(id) on delete set null,
    actor text not null,
    action text not null,
    details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists audit_log_account_created_idx
    on audit_log (account_id, created_at desc);
