-- ONE TO MANY

CREATE TABLE IF NOT EXISTS person (
    person_id BIGINT PRIMARY KEY,
    name varchar(255)
);

CREATE TABLE IF NOT EXISTS music (
    music_id BIGINT PRIMARY KEY,
    person_id BIGINT REFERENCES person(person_id),
    music_name varchar(255)
);

insert into person(person_id, name) values (1, 'Bob');

insert into music(music_id, person_id, music_name) values (1, 1, 'music_1');
insert into music(music_id, person_id, music_name) values (2, 1, 'music_2');


insert into person(person_id, name) values (2, 'Alex');

insert into music(music_id, person_id, music_name) values (3, 2, 'music_3');
insert into music(music_id, person_id, music_name) values (4, 2, 'music_4');

-- drop before many to many
DROP TABLE IF EXISTS person CASCADE;
DROP TABLE IF EXISTS music CASCADE;


-- MANY TO MANY

CREATE TABLE IF NOT EXISTS person (
    person_id BIGINT PRIMARY KEY,
    name varchar(255)
);

CREATE TABLE IF NOT EXISTS music (
    music_id BIGINT PRIMARY KEY,
    music_name varchar(255)
);

CREATE TABLE IF NOT EXISTS person_music_ref (
    id BIGINT PRIMARY KEY,
    person_id BIGINT references person(person_id),
    music_id BIGINT references music(music_id)
);

insert into person(person_id, name) values (1, 'Bob');
insert into person(person_id, name) values (2, 'Alex');


insert into music(music_id, music_name) values (1, 'music_1');
insert into music(music_id, music_name) values (2, 'music_2');

insert into person_music_ref(id, person_id, music_id) values (1, 1, 1);
insert into person_music_ref(id, person_id, music_id) values (2, 1, 2);

insert into person_music_ref(id, person_id, music_id) values (3, 2, 1);
insert into person_music_ref(id, person_id, music_id) values (4, 2, 2);

DROP TABLE IF EXISTS person CASCADE;
DROP TABLE IF EXISTS music CASCADE;
DROP TABLE IF EXISTS person_music_ref cascade;