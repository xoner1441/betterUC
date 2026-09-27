delete from feature_flags
where key = 'auto_winzer';

update feature_flags
set label = 'Gärtner-Hilfe',
    description = 'Automatische /dropblumen-Abgabe und passive Topf-Markierung; Einsammeln bleibt manuell',
    updated_at = now(),
    updated_by = 'migration:014'
where key = 'auto_gaertner';
