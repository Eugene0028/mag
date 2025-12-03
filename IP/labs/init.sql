-- HR schema for PostgreSQL
-- Tables: REGIONS, COUNTRIES, LOCATIONS, DEPARTMENTS,
--         JOBS, EMPLOYEES, JOB_HISTORY

-- Clean up (for repeated runs during development)
DROP TABLE IF EXISTS job_history CASCADE;
DROP TABLE IF EXISTS employees CASCADE;
DROP TABLE IF EXISTS jobs CASCADE;
DROP TABLE IF EXISTS departments CASCADE;
DROP TABLE IF EXISTS locations CASCADE;
DROP TABLE IF EXISTS countries CASCADE;
DROP TABLE IF EXISTS regions CASCADE;

-- ======================
--  REGIONS
-- ======================
CREATE TABLE regions (
    region_id   INTEGER PRIMARY KEY,
    region_name VARCHAR(25) NOT NULL
);

-- ======================
--  COUNTRIES
-- ======================
CREATE TABLE countries (
    country_id   CHAR(2) PRIMARY KEY,
    country_name VARCHAR(40) NOT NULL,
    region_id    INTEGER NOT NULL REFERENCES regions(region_id)
);

-- ======================
--  LOCATIONS
-- ======================
CREATE TABLE locations (
    location_id   INTEGER PRIMARY KEY,
    street_address VARCHAR(40),
    postal_code    VARCHAR(12),
    city           VARCHAR(30) NOT NULL,
    state_province VARCHAR(25),
    country_id     CHAR(2) NOT NULL REFERENCES countries(country_id)
);

-- ======================
--  DEPARTMENTS
-- ======================
CREATE TABLE departments (
    department_id   INTEGER PRIMARY KEY,
    department_name VARCHAR(30) NOT NULL,
    manager_id      INTEGER,
    location_id     INTEGER REFERENCES locations(location_id)
);

-- ======================
--  JOBS
-- ======================
CREATE TABLE jobs (
    job_id     VARCHAR(10) PRIMARY KEY,
    job_title  VARCHAR(35) NOT NULL,
    min_salary NUMERIC(6,0),
    max_salary NUMERIC(6,0)
);

-- ======================
--  EMPLOYEES
-- ======================
CREATE TABLE employees (
    employee_id    INTEGER PRIMARY KEY,
    first_name     VARCHAR(20),
    last_name      VARCHAR(25) NOT NULL,
    email          VARCHAR(25) NOT NULL UNIQUE,
    phone_number   VARCHAR(20),
    hire_date      DATE NOT NULL,
    job_id         VARCHAR(10) NOT NULL REFERENCES jobs(job_id),
    salary         NUMERIC(8,2),
    commission_pct NUMERIC(2,2),
    manager_id     INTEGER,
    department_id  INTEGER
);

-- Add foreign keys that reference employees and departments
ALTER TABLE employees
    ADD CONSTRAINT fk_employees_manager
        FOREIGN KEY (manager_id) REFERENCES employees(employee_id),
    ADD CONSTRAINT fk_employees_department
        FOREIGN KEY (department_id) REFERENCES departments(department_id);

ALTER TABLE departments
    ADD CONSTRAINT fk_departments_manager
        FOREIGN KEY (manager_id) REFERENCES employees(employee_id);

-- ======================
--  JOB_HISTORY
-- ======================
CREATE TABLE job_history (
    employee_id   INTEGER   NOT NULL,
    start_date    DATE      NOT NULL,
    end_date      DATE,
    job_id        VARCHAR(10) NOT NULL,
    department_id INTEGER,
    PRIMARY KEY (employee_id, start_date),
    CONSTRAINT fk_job_history_employee
        FOREIGN KEY (employee_id) REFERENCES employees(employee_id),
    CONSTRAINT fk_job_history_job
        FOREIGN KEY (job_id) REFERENCES jobs(job_id),
    CONSTRAINT fk_job_history_department
        FOREIGN KEY (department_id) REFERENCES departments(department_id)
);

-- ======================
--  SAMPLE DATA
-- ======================

-- 1) REGIONS
INSERT INTO regions (region_id, region_name) VALUES
    (1, 'Europe'),
    (2, 'Americas'),
    (3, 'Asia'),
    (4, 'Middle East & Africa');

-- 2) COUNTRIES
INSERT INTO countries (country_id, country_name, region_id) VALUES
    ('US', 'United States of America', 2),
    ('UK', 'United Kingdom', 1),
    ('DE', 'Germany', 1),
    ('JP', 'Japan', 3),
    ('CA', 'Canada', 2);

-- 3) LOCATIONS (part 1)
INSERT INTO locations (location_id, street_address, postal_code, city, state_province, country_id) VALUES
    (1000, '200 Main Street', '10001', 'New York', 'NY', 'US'),
    (1100, '10 Downing Street', 'SW1A 2AA', 'London', NULL, 'UK');

-- 4) LOCATIONS (part 2)
INSERT INTO locations (location_id, street_address, postal_code, city, state_province, country_id) VALUES
    (1200, 'Unter den Linden 1', '10117', 'Berlin', NULL, 'DE'),
    (1300, '1-1 Chiyoda', '100-8111', 'Tokyo', 'Tokyo', 'JP'),
    (1400, '100 King Street', 'M5H 1J9', 'Toronto', 'ON', 'CA'),
    (1500, '1 Market Street', '94105', 'San Francisco', 'CA', 'US');

-- 5) DEPARTMENTS
INSERT INTO departments (department_id, department_name, manager_id, location_id) VALUES
    (10, 'Administration', NULL, 1000),
    (20, 'IT',           NULL, 1100),
    (30, 'Sales',        NULL, 1200),
    (40, 'Finance',      NULL, 1300),
    (50, 'Marketing',    NULL, 1000),
    (60, 'Operations',   NULL, 1200),
    (70, 'Support',      NULL, 1300),
    (80, 'Logistics',    NULL, 1100),
    (90, 'Research',     NULL, 1400),
    (100,'Consulting',   NULL, 1100),
    (110,'Human Resources', NULL, 1200),
    (120,'Training',     NULL, 1000),
    (130,'Sales Canada', NULL, 1400);

-- 6) JOBS
INSERT INTO jobs (job_id, job_title, min_salary, max_salary) VALUES
    ('AD_PRES', 'President',          20000, 40000),
    ('AD_VP',   'Vice President',     15000, 30000),
    ('IT_PROG', 'Programmer',          4000, 12000),
    ('SA_REP',  'Sales Representative',3000, 10000),
    ('SA_MAN',  'Sales Manager',       10000, 18000),
    ('IT_MGR',  'IT Manager',          10000, 16000),
    ('MK_MAN',  'Marketing Manager',    9000, 15000),
    ('MK_REP',  'Marketing Representative', 4000, 9000),
    ('HR_REP',  'HR Representative',    4000, 9000),
    ('AC_ACCOUNT','Accountant',         5000, 11000),
    ('FI_MGR',  'Finance Manager',     12000, 20000);

-- 7) EMPLOYEES (part 1)
INSERT INTO employees (
    employee_id, first_name, last_name, email, phone_number,
    hire_date, job_id, salary, commission_pct, manager_id, department_id
) VALUES
    (100, 'Steven',  'King',   'steven.king@example.com',   '515.123.4567', DATE '2010-06-17', 'AD_PRES', 24000, NULL,   NULL, 10),
    (101, 'Neena',   'Kochhar','neena.kochhar@example.com', '515.123.4568',DATE '2011-09-21', 'AD_VP',   17000, NULL,   100,  10),
    (102, 'Lex',     'De Haan','lex.dehaan@example.com',    '515.123.4569', DATE '2012-01-13', 'AD_VP',   17000, NULL,   100,  40);

-- 8) EMPLOYEES (part 2)
INSERT INTO employees (
    employee_id, first_name, last_name, email, phone_number,
    hire_date, job_id, salary, commission_pct, manager_id, department_id
) VALUES
    (103, 'Alexander','Hunold','alexander.hunold@example.com','590.423.4567', DATE '2013-03-03', 'IT_PROG',  9000, 0.10, 101, 20),
    (104, 'Bruce',    'Ernst', 'bruce.ernst@example.com',     '590.423.4568', DATE '2014-05-21', 'IT_PROG',  6000, 0.05, 103, 20),
    (105, 'David',    'Austin','david.austin@example.com',    '590.423.4569', DATE '2015-07-11', 'SA_REP',   8000, 0.15, 101, 30);

-- 8.1) EMPLOYEES (part 3 - more management and staff)
INSERT INTO employees (
    employee_id, first_name, last_name, email, phone_number,
    hire_date, job_id, salary, commission_pct, manager_id, department_id
) VALUES
    (106, 'Sarah',  'Summers','sarah.summers106@example.com',  '515.123.4600', DATE '2009-02-01', 'SA_MAN',   15000, 0.20, 100, 30),
    (107, 'Robert', 'King',   'robert.king107@example.com',    '515.123.4601', DATE '2008-01-10', 'IT_MGR',   14000, NULL, 100, 20),
    (108, 'Michael','Scott',  'michael.scott108@example.com',  '515.123.4602', DATE '2007-04-01', 'MK_MAN',   12000, NULL, 100, 50),
    (109, 'Helga',  'Schmidt','helga.schmidt109@example.com',  '515.123.4603', DATE '2005-11-20', 'FI_MGR',   16000, NULL, 100, 40),
    (110, 'Susan',  'Smith',  'susan.smith110@example.com',    '515.123.4604', DATE '2010-09-01', 'SA_MAN',   13500, 0.10, 101, 30),
    (111, 'Tim',    'Gordon', 'tim.gordon111@example.com',     '515.123.4605', DATE '2012-12-15', 'MK_REP',    5000, 0.05, 108, 50);

-- 8.2) EMPLOYEES (part 4 - sales staff and low-salary employees)
INSERT INTO employees (
    employee_id, first_name, last_name, email, phone_number,
    hire_date, job_id, salary, commission_pct, manager_id, department_id
) VALUES
    (120, 'Sam',    'Stone',   'sam.stone120@example.com',      '515.555.1200', DATE '2015-04-10', 'SA_REP',  4500,  NULL, 110, 30),
    (121, 'Sophie', 'Swift',   'sophie.swift121@example.com',   '515.555.1201', DATE '2016-05-15', 'SA_REP',  4800,  NULL, 110, 30),
    (122, 'Mark',   'Brown',   'mark.brown122@example.com',     '515.555.1202', DATE '2017-06-20', 'SA_REP',  3000,  NULL, 110, 30),
    (123, 'Ivan',   'Petrov',  'ivan.petrov123@example.com',    '515.555.1203', DATE '2018-01-05', 'SA_REP',  3500,  NULL, 110, 30),
    (124, 'Anna',   'Ivanova', 'anna.ivanova124@example.com',   '515.555.1204', DATE '2019-03-12', 'SA_REP',  7000,  0.10, 110, 30),
    (125, 'Sergey', 'Sergeev', 'sergey.sergeev125@example.com', '515.555.1205', DATE '2020-07-22', 'SA_REP',  9500,  0.20, 110, 60),
    (126, 'Olga',   'Orlova',  'olga.orlova126@example.com',    '515.555.1206', DATE '2021-09-30', 'SA_REP', 10500,  0.15, 110, 60);

-- 8.3) EMPLOYEES (part 5 - additional programmers)
INSERT INTO employees (
    employee_id, first_name, last_name, email, phone_number,
    hire_date, job_id, salary, commission_pct, manager_id, department_id
) VALUES
    (130, 'Julia',  'Niles',   'julia.niles130@example.com',    '590.423.5001', DATE '2013-04-01', 'IT_PROG', 8000, 0.10, 107, 20),
    (131, 'James',  'Smith',   'james.smith131@example.com',    '590.423.5002', DATE '2013-06-01', 'IT_PROG', 9000, 0.10, 107, 20),
    (132, 'Linda',  'Black',   'linda.black132@example.com',    '590.423.5003', DATE '2013-07-01', 'IT_PROG', 7500, 0.05, 107, 20),
    (133, 'Victor', 'Hugo',    'victor.hugo133@example.com',    '590.423.5004', DATE '2013-08-01', 'IT_PROG', 7000, NULL, 107, 20),
    (134, 'Nina',   'Gold',    'nina.gold134@example.com',      '590.423.5005', DATE '2013-09-01', 'IT_PROG', 8500, NULL, 107, 20),
    (135, 'Pavel',  'Sidorov', 'pavel.sidorov135@example.com',  '590.423.5006', DATE '2014-02-10', 'IT_PROG', 8200, 0.05, 107, 20);

-- 8.4) EMPLOYEES (part 6 - long-tenure employees across departments)
INSERT INTO employees (
    employee_id, first_name, last_name, email, phone_number,
    hire_date, job_id, salary, commission_pct, manager_id, department_id
) VALUES
    (140, 'Tony',   'Canada',  'tony.canada140@example.com',    '416.555.1400', DATE '2004-01-15', 'SA_MAN',    14500, 0.12, 101, 90),
    (141, 'Carol',  'Toronto', 'carol.toronto141@example.com',  '416.555.1401', DATE '2010-05-20', 'SA_REP',     9000, 0.10, 140, 90),
    (142, 'David',  'Miller',  'david.miller142@example.com',   '416.555.1402', DATE '2011-11-11', 'MK_REP',     6500, 0.08, 140, 130),
    (143, 'Emily',  'Clark',   'emily.clark143@example.com',    '212.555.1430', DATE '2002-03-01', 'AC_ACCOUNT', 6000, NULL, 109, 40),
    (144, 'George', 'Baker',   'george.baker144@example.com',   '212.555.1440', DATE '2000-06-30', 'AC_ACCOUNT', 7500, NULL, 109, 40),
    (145, 'Henry',  'Ford',    'henry.ford145@example.com',     '49.555.1450',  DATE '2003-09-09', 'HR_REP',     5500, NULL, 109, 110),
    (146, 'Ingrid', 'Klein',   'ingrid.klein146@example.com',   '49.555.1460',  DATE '2006-03-01', 'HR_REP',     6200, NULL, 145, 110);

-- 8.5) EMPLOYEES (part 7 - hires in 2006-2007 and various countries)
INSERT INTO employees (
    employee_id, first_name, last_name, email, phone_number,
    hire_date, job_id, salary, commission_pct, manager_id, department_id
) VALUES
    (147, 'John',   'Taylor',  'john.taylor147@example.com',    '44.555.1470',  DATE '2006-02-10', 'SA_REP',   9000, 0.12, 106, 30),
    (148, 'Karen',  'Jones',   'karen.jones148@example.com',    '44.555.1480',  DATE '2007-07-18', 'IT_PROG',  9500, 0.05, 107, 20),
    (149, 'Lars',   'Meyer',   'lars.meyer149@example.com',     '49.555.1490',  DATE '2007-10-05', 'SA_REP',   8000, 0.10, 110, 60),
    (150, 'Martin', 'Green',   'martin.green150@example.com',   '44.555.1500',  DATE '2012-08-01', 'SA_MAN',  13000, 0.10, 101, 80),
    (151, 'Olivia', 'White',   'olivia.white151@example.com',   '44.555.1510',  DATE '2013-01-20', 'MK_MAN',  11000, NULL, 101, 100),
    (152, 'Paul',   'Young',   'paul.young152@example.com',     '81.555.1520',  DATE '2014-04-14', 'SA_REP',   7000, 0.05, 150, 70),
    (153, 'Qi',     'Chen',    'qi.chen153@example.com',        '81.555.1530',  DATE '2015-09-09', 'IT_PROG',  7800, 0.05, 107, 70);

-- 8.6) EMPLOYEES (part 8 - additional miscellaneous employees)
INSERT INTO employees (
    employee_id, first_name, last_name, email, phone_number,
    hire_date, job_id, salary, commission_pct, manager_id, department_id
) VALUES
    (160, 'Old',    'Timer',    'old.timer160@example.com',      '212.555.1600', DATE '2006-03-01', 'SA_REP',     9500, 0.10, 110, 30),
    (161, 'Ancient','Worker',   'ancient.worker161@example.com', '212.555.1610', DATE '2007-05-15', 'IT_PROG',    9800, 0.05, 107, 20),
    (162, 'Long',   'Service',  'long.service162@example.com',   '212.555.1620', DATE '2000-01-15', 'AC_ACCOUNT', 8000, NULL, 109, 40),
    (163, 'Maria',  'Lopez',    'maria.lopez163@example.com',    '1.555.1630',   DATE '2018-10-10', 'HR_REP',     5200, NULL, 145, 110),
    (164, 'Nikolai','Sokolov',  'nikolai.sokolov164@example.com','7.555.1640',  DATE '2019-12-01', 'IT_PROG',    7600, 0.05, 107, 60),
    (165, 'Oksana', 'Smirnova', 'oksana.smirnova165@example.com','7.555.1650',  DATE '2020-11-11', 'SA_REP',     8200, 0.12, 110, 80),
    (166, 'Peter',  'Sokolov',  'peter.sokolov166@example.com',  '7.555.1660',  DATE '2021-06-06', 'MK_REP',     6200, 0.08, 151, 100),
    (167, 'Rita',   'Kuznetsova','rita.kuznetsova167@example.com','7.555.1670', DATE '2022-02-02', 'MK_REP',     6400, 0.10, 151, 100),
    (168, 'Svetlana','Sidorova','svetlana.sidorova168@example.com','7.555.1680',DATE '2019-09-09', 'HR_REP',     5400, NULL, 145, 110),
    (169, 'Igor',   'Programmer','igor.programmer169@example.com','7.555.1690', DATE '2016-03-03', 'IT_PROG',    8800, 0.07, 107, 60),
    (170, 'Thomas', 'Hardy',    'thomas.hardy170@example.com',   '44.555.1700', DATE '2017-07-07', 'SA_REP',     7200, 0.05, 150, 80),
    (171, 'Ulyana', 'Smirnova', 'ulyana.smirnova171@example.com','7.555.1710',  DATE '2018-08-08', 'IT_PROG',    8100, 0.05, 107, 90);

-- 8.7) Set department managers after employees have been created
UPDATE departments SET manager_id = 100 WHERE department_id = 10;
UPDATE departments SET manager_id = 107 WHERE department_id = 20;
UPDATE departments SET manager_id = 110 WHERE department_id = 30;
UPDATE departments SET manager_id = 109 WHERE department_id = 40;
UPDATE departments SET manager_id = 108 WHERE department_id = 50;
UPDATE departments SET manager_id = 110 WHERE department_id = 60;
UPDATE departments SET manager_id = 150 WHERE department_id = 70;
UPDATE departments SET manager_id = 150 WHERE department_id = 80;
UPDATE departments SET manager_id = 140 WHERE department_id = 90;
UPDATE departments SET manager_id = 151 WHERE department_id = 100;
UPDATE departments SET manager_id = 145 WHERE department_id = 110;
UPDATE departments SET manager_id = 101 WHERE department_id = 120;
UPDATE departments SET manager_id = 140 WHERE department_id = 130;

-- 9) JOB_HISTORY (part 1)
INSERT INTO job_history (employee_id, start_date, end_date, job_id, department_id) VALUES
    (103, DATE '2013-03-03', DATE '2014-03-03', 'IT_PROG', 20),
    (104, DATE '2014-05-21', DATE '2015-05-21', 'IT_PROG', 20);

-- 10) JOB_HISTORY (part 2)
INSERT INTO job_history (employee_id, start_date, end_date, job_id, department_id) VALUES
    (105, DATE '2015-07-11', DATE '2016-07-11', 'SA_REP', 30),
    (102, DATE '2012-01-13', DATE '2013-01-13', 'AD_VP',  40);

