insert into feature_flags (key, enabled, label, description)
values (
    'auto_muellmann',
    true,
    'Auto-Müllmann',
    'Automatische Müllsortierung in markierten Bereichen'
)
on conflict (key) do nothing;
