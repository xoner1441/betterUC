create table if not exists waste_drop_areas (
    waste_type text primary key
        check (waste_type in ('glas', 'metall', 'abfall', 'holz')),
    x1 integer,
    z1 integer,
    x2 integer,
    z2 integer,
    dimension text not null default '',
    updated_at timestamptz not null default now(),
    updated_by text not null default 'admin:mod'
);

create index if not exists waste_drop_areas_updated_at_idx
    on waste_drop_areas (updated_at desc);

insert into waste_drop_areas(
    waste_type, x1, z1, x2, z2, dimension, updated_by
)
values
    ('glas',   -671, 331, -679, 334, 'minecraft:overworld', 'migration:local-import'),
    ('holz',   -664, 307, -667, 302, 'minecraft:overworld', 'migration:local-import'),
    ('metall', -689, 291, -691, 286, 'minecraft:overworld', 'migration:local-import'),
    ('abfall', -696, 318, -700, 323, 'minecraft:overworld', 'migration:local-import')
on conflict (waste_type) do nothing;
