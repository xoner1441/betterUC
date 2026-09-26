alter table waste_drop_areas
    drop constraint if exists waste_drop_areas_waste_type_check;

alter table waste_drop_areas
    add column if not exists y1 integer,
    add column if not exists y2 integer;

alter table waste_drop_areas
    add constraint waste_drop_areas_waste_type_check
        check (waste_type in ('glas', 'metall', 'abfall', 'holz', 'dropdrink'));
