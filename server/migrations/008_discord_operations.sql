alter table accounts
    add column if not exists last_game_version text not null default '';

create table if not exists discord_tickets (
    id bigint generated always as identity primary key,
    guild_id text not null,
    channel_id text not null unique,
    opener_discord_id text not null,
    account_id uuid references accounts(id) on delete set null,
    topic text not null,
    status text not null default 'open'
        check (status in ('open', 'closed')),
    claimed_by_discord_id text,
    close_reason text,
    transcript_path text,
    opened_at timestamptz not null default now(),
    claimed_at timestamptz,
    closed_at timestamptz
);

create index if not exists discord_tickets_opened_at_idx
    on discord_tickets (opened_at desc);
create index if not exists discord_tickets_account_idx
    on discord_tickets (account_id, opened_at desc);

create table if not exists discord_suggestions (
    id bigint generated always as identity primary key,
    guild_id text not null,
    channel_id text,
    message_id text unique,
    author_discord_id text not null,
    account_id uuid references accounts(id) on delete set null,
    title text not null,
    description text not null,
    status text not null default 'open'
        check (status in ('open', 'planned', 'in_progress', 'implemented', 'rejected')),
    status_note text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists discord_suggestions_created_at_idx
    on discord_suggestions (created_at desc);
create index if not exists discord_suggestions_status_idx
    on discord_suggestions (status, created_at desc);

create table if not exists discord_suggestion_votes (
    suggestion_id bigint not null references discord_suggestions(id) on delete cascade,
    discord_id text not null,
    vote smallint not null check (vote in (-1, 1)),
    voted_at timestamptz not null default now(),
    primary key (suggestion_id, discord_id)
);

create table if not exists discord_activity_events (
    id bigint generated always as identity primary key,
    event_type text not null,
    account_id uuid references accounts(id) on delete set null,
    details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists discord_activity_events_type_created_idx
    on discord_activity_events (event_type, created_at desc);
create index if not exists discord_activity_events_account_created_idx
    on discord_activity_events (account_id, created_at desc);
