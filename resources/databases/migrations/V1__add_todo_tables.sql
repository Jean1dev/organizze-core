create table todo
(
    todo_id    uuid primary key default gen_random_uuid(),
    created_at timestamp not null default current_timestamp,
    title      text      not null
);

create table todo_item
(
    todo_item_id uuid primary key default gen_random_uuid(),
    todo_id      uuid references todo (todo_id),
    created_at   timestamp not null default current_timestamp,
    title        text      not null
);

create table events
(
    id uuid primary key default gen_random_uuid(),
    type text not null,
    aggregate_id uuid not null,
    aggregate_type text not null,
    payload jsonb not null,
    created_at timestamp not null default current_timestamp
);