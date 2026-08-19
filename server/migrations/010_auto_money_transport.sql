insert into feature_flags (key, enabled, label, description)
values (
    'auto_money_transport',
    true,
    'Auto-Geldtransport',
    'Automatische Geldabgabe am erkannten Einzahlungsziel'
)
on conflict (key) do nothing;
