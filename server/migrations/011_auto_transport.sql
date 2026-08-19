insert into feature_flags (key, enabled, label, description)
values (
    'auto_transport',
    true,
    'Auto-Transport',
    'Scoreboard-gesteuerte Kistenabgabe am Lieferziel'
)
on conflict (key) do nothing;
