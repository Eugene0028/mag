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
    ('JP', 'Japan', 3);

-- 3) LOCATIONS (part 1)
INSERT INTO locations (location_id, street_address, postal_code, city, state_province, country_id) VALUES
    (1000, '200 Main Street', '10001', 'New York', 'NY', 'US'),
    (1100, '10 Downing Street', 'SW1A 2AA', 'London', NULL, 'UK');

-- 4) LOCATIONS (part 2)
INSERT INTO locations (location_id, street_address, postal_code, city, state_province, country_id) VALUES
    (1200, 'Unter den Linden 1', '10117', 'Berlin', NULL, 'DE'),
    (1300, '1-1 Chiyoda', '100-8111', 'Tokyo', 'Tokyo', 'JP');

-- 5) DEPARTMENTS
INSERT INTO departments (department_id, department_name, manager_id, location_id) VALUES
    (10, 'Administration', NULL, 1000),
    (20, 'IT',           NULL, 1100),
    (30, 'Sales',        NULL, 1200),
    (40, 'Finance',      NULL, 1300);

-- 6) JOBS
INSERT INTO jobs (job_id, job_title, min_salary, max_salary) VALUES
    ('AD_PRES', 'President',          20000, 40000),
    ('AD_VP',   'Vice President',     15000, 30000),
    ('IT_PROG', 'Programmer',          4000, 12000),
    ('SA_REP',  'Sales Representative',3000, 10000);

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

-- 9) JOB_HISTORY (part 1)
INSERT INTO job_history (employee_id, start_date, end_date, job_id, department_id) VALUES
    (103, DATE '2013-03-03', DATE '2014-03-03', 'IT_PROG', 20),
    (104, DATE '2014-05-21', DATE '2015-05-21', 'IT_PROG', 20);

-- 10) JOB_HISTORY (part 2)
INSERT INTO job_history (employee_id, start_date, end_date, job_id, department_id) VALUES
    (105, DATE '2015-07-11', DATE '2016-07-11', 'SA_REP', 30),
    (102, DATE '2012-01-13', DATE '2013-01-13', 'AD_VP',  40);

