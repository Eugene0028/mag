-- РГЗ: База данных для фирмы, занимающейся арендой помещений

-- 1. Таблица зданий
--   - район города
--   - точный адрес
--   - число этажей
--   - общее количество помещений для аренды
--   - телефон коменданта

create table buildings (
    building_id    serial primary key,
    district       varchar(100) not null,
    address        varchar(255) not null,
    floors_count   integer      not null check (floors_count > 0),
    total_units    integer      not null check (total_units >= 0),
    commandant_phone varchar(20)
);


-- 2. Таблица помещений в зданиях
--   - номер помещения (комнаты)
--   - полезная площадь
--   - номер этажа
--   - вид отделки (обычная, улучшенная, евроремонт и т.п.)
--   - наличие телефона (есть/нет)

create table premises (
    premises_id  serial primary key,
    building_id  integer      not null references buildings(building_id) on delete cascade,
    room_number  varchar(20)  not null,
    floor        integer      not null check (floor >= 0),
    area         numeric(8,2) not null check (area > 0),
    finish_type  varchar(50)  not null,
    has_phone    boolean      not null,

    unique (building_id, room_number)
);


-- 3. Арендаторы (общая таблица для физ. и юр. лиц)
--   tenant_type: 'P' - физлицо, 'C' - юрлицо

create table tenants (
    tenant_id   serial primary key,
    tenant_type char(1) not null check (tenant_type in ('P', 'C'))
);


-- 4. Физические лица
--   - ФИО арендатора
--   - телефон
--   - паспортные данные: серия, номер, дата выдачи, кем выдан

create table tenant_persons (
    tenant_id            integer      primary key
                                   references tenants(tenant_id) on delete cascade,
    full_name            varchar(200) not null,
    phone                varchar(20),
    passport_series      varchar(10)  not null,
    passport_number      varchar(20)  not null,
    passport_issue_date  date         not null,
    passport_issued_by   varchar(255) not null
);


-- 5. Юридические лица
--   - название арендатора
--   - ФИО руководителя
--   - юридический адрес и телефон
--   - банк арендатора и расчетный счет
--   - ИНН

create table tenant_companies (
    tenant_id          integer      primary key
                                 references tenants(tenant_id) on delete cascade,
    name               varchar(255) not null,
    director_full_name varchar(200) not null,
    legal_address      varchar(255) not null,
    phone              varchar(20),
    bank_name          varchar(255) not null,
    bank_account       varchar(50)  not null,
    inn                varchar(20)  not null
);


-- 6. Договоры аренды
--   - регистрационный номер договора
--   - срок действия договора (начало и конец)
--   - периодичность оплаты (ежемесячно, поквартально и т.п.)
--   - дополнительные условия
--   - штраф за нарушение условий договора
--   - арендатор (физ/юр лицо)

create table contracts (
    contract_id          serial primary key,
    tenant_id            integer      not null
                                  references tenants(tenant_id) on delete restrict,
    start_date           date         not null,
    end_date             date,
    payment_periodicity  varchar(30)  not null,
    extra_conditions     text,
    penalty              numeric(10,2)
);


-- 7. Помещения в конкретных договорах
--   - перечень арендуемых помещений
--   - цель аренды (офис, киоск, склад и др.)
--   - срок аренды по каждому объекту
--   - размер арендной платы по каждому объекту

create table contract_premises (
    contract_premises_id serial primary key,
    contract_id          integer      not null
                                   references contracts(contract_id) on delete cascade,
    premises_id          integer      not null
                                   references premises(premises_id),
    purpose              varchar(50)  not null,
    rent_start_date      date         not null,
    rent_end_date        date,
    rent_amount          numeric(12,2) not null,

    unique (contract_id, premises_id, rent_start_date)
);

-- ===========================
-- Пример заполнения данных
-- ===========================

-- Здания

insert into buildings (building_id, district, address, floors_count, total_units, commandant_phone) values
    (1, 'Центральный', 'ул. Ленина, д. 10', 5, 8,  '111-111-11-11'),
    (2, 'Северный',    'пр. Мира, д. 5',    3, 5,  '222-222-22-22'),
    (3, 'Деловой центр','б-р Бизнеса, д. 1',10, 10, '333-333-33-33');


-- Помещения в зданиях

insert into premises (premises_id, building_id, room_number, floor, area, finish_type, has_phone) values
    -- Здание 1
    (1,  1, '101', 1, 25.0, 'обычная',     false),
    (2,  1, '102', 1, 30.0, 'улучшенная',  true),
    (3,  1, '201', 2, 35.0, 'улучшенная',  true),
    (4,  1, '202', 2, 40.0, 'евроремонт',  true),
    (5,  1, '301', 3, 28.0, 'обычная',     false),
    (6,  1, '302', 3, 32.0, 'улучшенная',  true),
    (7,  1, '401', 4, 45.0, 'евроремонт',  true),
    (8,  1, '402', 4, 50.0, 'евроремонт',  true),

    -- Здание 2
    (9,  2, '101', 1, 18.0, 'обычная',     false),
    (10, 2, '102', 1, 22.0, 'улучшенная',  true),
    (11, 2, '201', 2, 26.0, 'улучшенная',  true),
    (12, 2, '202', 2, 30.0, 'евроремонт',  true),
    (13, 2, '301', 3, 35.0, 'евроремонт',  true),

    -- Здание 3
    (14, 3, '101', 1, 40.0, 'улучшенная',  true),
    (15, 3, '201', 2, 45.0, 'евроремонт',  true),
    (16, 3, '301', 3, 55.0, 'евроремонт',  true),
    (17, 3, '401', 4, 60.0, 'евроремонт',  true),
    (18, 3, '501', 5, 70.0, 'евроремонт',  true),
    (19, 3, '601', 6, 35.0, 'улучшенная',  true),
    (20, 3, '701', 7, 38.0, 'улучшенная',  true),
    (21, 3, '801', 8, 42.0, 'улучшенная',  true),
    (22, 3, '901', 9, 48.0, 'евроремонт',  true),
    (23, 3, '1001',10, 55.0,'евроремонт',  true);


-- Арендаторы (общие записи)

insert into tenants (tenant_id, tenant_type) values
    (1, 'P'),  -- физическое лицо
    (2, 'P'),
    (3, 'C'),  -- юридическое лицо
    (4, 'C');


-- Физические лица

insert into tenant_persons (
    tenant_id, full_name, phone,
    passport_series, passport_number,
    passport_issue_date, passport_issued_by
) values
    (1, 'Иванов Иван Иванович', '8-900-111-11-11',
        '40 11', '123456',
        date '2012-05-15', 'ОВД г. Новосибирска'),
    (2, 'Петров Петр Петрович', '8-900-222-22-22',
        '51 22', '654321',
        date '2015-09-20', 'ОВД г. Екатеринбурга');


-- Юридические лица

insert into tenant_companies (
    tenant_id, name, director_full_name,
    legal_address, phone,
    bank_name, bank_account, inn
) values
    (3, 'ООО "Альфа-Офис"', 'Сидоров Сергей Сергеевич',
        '630000, г. Новосибирск, ул. Ленина, д. 20', '8-383-300-00-01',
        'ПАО "Банк Развития"', '40702810900000000001', '5400000001'),
    (4, 'ООО "Бета-Логистик"', 'Кузнецова Анна Александровна',
        '620000, г. Екатеринбург, пр. Мира, д. 15', '8-343-400-00-02',
        'АО "Транзит Банк"', '40702810900000000002', '6600000002');


-- Договоры аренды

insert into contracts (
    contract_id, tenant_id, start_date, end_date,
    payment_periodicity, extra_conditions, penalty
) values
    (1, 1, date '2023-01-01', date '2023-12-31',
        'ежемесячно',
        'Оплата до 10-го числа каждого месяца. Индексация на уровень инфляции раз в год.',
        5000.00),
    (2, 3, date '2022-04-01', date '2025-03-31',
        'ежеквартально',
        'Без права субаренды. Обязательное страхование имущества арендатора.',
        20000.00),
    (3, 2, date '2023-06-01', date '2024-05-31',
        'ежемесячно',
        'Киоск на первом этаже, режим работы с 8:00 до 22:00.',
        3000.00),
    (4, 4, date '2021-01-01', date '2023-12-31',
        'ежемесячно',
        'Складские помещения, доступ круглосуточно.',
        15000.00);


-- Помещения в разрезе договоров

insert into contract_premises (
    contract_premises_id, contract_id, premises_id,
    purpose, rent_start_date, rent_end_date, rent_amount
) values
    -- Договор 1: физлицо арендует небольшой офис в здании 1
    (1, 1, 2, 'офис',  date '2023-01-01', date '2023-12-31', 25000.00),

    -- Договор 2: юрлицо арендует несколько офисов в деловом центре
    (2, 2, 16, 'офис',  date '2022-04-01', date '2025-03-31', 120000.00),
    (3, 2, 18, 'офис',  date '2022-04-01', date '2025-03-31', 180000.00),

    -- Договор 3: киоск и небольшой офис в здании 2
    (4, 3, 9,  'киоск', date '2023-06-01', date '2024-05-31', 15000.00),
    (5, 3, 10, 'офис',  date '2023-06-01', date '2024-05-31', 22000.00),

    -- Договор 4: склад и офис компании-арендатора
    (6, 4, 11, 'склад', date '2021-01-01', date '2023-12-31', 30000.00),
    (7, 4, 21, 'офис',  date '2021-01-01', date '2023-12-31', 50000.00);

