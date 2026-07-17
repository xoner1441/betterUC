create table if not exists feature_flags (
    key text primary key
        check (key ~ '^[a-z0-9_]{3,64}$'),
    enabled boolean not null default true,
    label text not null,
    description text not null default '',
    updated_at timestamptz not null default now(),
    updated_by text not null default 'migration'
);

insert into feature_flags (key, enabled, label, description)
values
    ('ping_system', true, 'Ping-System', 'Private globale und fraktionsbasierte Pings'),
    ('chat_customization', true, 'WPS/HQ Customizations', 'Kompakte WPS- und HQ-Nachrichten'),
    ('reinf_customization', true, 'Reinf Customizations', 'Kompakte Fraktions- und Bündnisrufe'),
    ('cloud_settings', true, 'Cloud-Sync', 'Synchronisierte Mod-Einstellungen'),
    ('auto_dropdrink', true, 'Auto-Dropdrink', 'Automatische Lieferjunge-Abgabe'),
    ('auto_fisher', true, 'Auto-Fischer', 'Automatische Fischer-Befehle'),
    ('auto_winzer', true, 'Auto-Winzer', 'Automatisches Leeren der Trauben-Fenster'),
    ('auto_gaertner', true, 'Auto-Gärtner', 'Automatische Blumenabgabe und Buschsammlung')
on conflict (key) do nothing;

create index if not exists feature_flags_updated_at_idx
    on feature_flags (updated_at desc);
