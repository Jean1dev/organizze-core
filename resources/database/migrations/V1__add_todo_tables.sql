create table todo
(
    todo_id    VARCHAR(36) primary key default (UUID()),
    created_at timestamp not null default current_timestamp,
    title      TEXT      not null
);

create table todo_item
(
    todo_item_id VARCHAR(36) primary key default (UUID()),
    todo_id      VARCHAR(36),
    created_at   timestamp not null default current_timestamp,
    title        TEXT      not null,
    FOREIGN KEY (todo_id) REFERENCES todo (todo_id)
);

create table events
(
    id VARCHAR(36) primary key default (UUID()),
    type TEXT not null,
    aggregate_id VARCHAR(36) not null,
    aggregate_type TEXT not null,
    payload JSON not null,
    created_at timestamp not null default current_timestamp
);