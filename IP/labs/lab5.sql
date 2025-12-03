-- 8     В таблицу JOBS добавить новую запись, указав при этом код должности (JOB_ID), название должности (JOB_TITLE) и минимальную зарплату (MIN_SALARY), 
-- которая в 2 раза ниже максимального значения в столбце MAX_SALARY.


insert into jobs (job_id, job_title, min_salary, max_salary)
select 'IT_SEC', 'Security Engineer', max(max_salary) / 2, max(max_salary)
from jobs;


-- 10. В таблицу JOB_HISTORY добавить новую запись, указав при этом EMPLOYEE_ID=111, START_DATE=TO_DATE ('28-SEP-97'), END_DATE=TO_DATE('31-DEC-09'), JOB_ID='MK_REP'.

insert into job_history (employee_id, start_date, end_date, job_id, department_id)
values (111, to_date('28-SEP-97', 'DD-MON-RR'), to_date('31-DEC-09', 'DD-MON-RR'), 'MK_REP', 50);


-- 13. В таблицу COUNTRIES добавить новую страну из 2-го региона (Americas), без указания списка столбцов.

insert into countries
values ('BR', 'Brazil', 2);


-- 20. Из таблицы LOCATIONS удалить записи, относящиеся к Японии (COUNTRY_NAME='Japan').

delete from locations
where country_id in (
    select country_id
    from countries
    where country_name = 'Japan'
);


-- 26. В таблицу EMPLOYEES добавить данные по новому сотруднику Tom Gus, затем записать в SALARY минимальную зарплату по его должности.

insert into employees (employee_id, first_name, last_name, email, hire_date, job_id)
values (301, 'Tom', 'Gus', 'TGUS', to_date('10-JAN-2013', 'DD-MON-YYYY'), 'MK_MAN');

update employees
set salary = (
    select min_salary
    from jobs
    where job_id = 'MK_MAN'
)
where employee_id = 301;


-- 37. Сотрудникам, которые работают в городе Toronto, назначить COMMISSION_PCT = 0.3.

update employees
set commission_pct = 0.3
where department_id in (
    select department_id
    from departments d
    join locations l on d.location_id = l.location_id
    where l.city = 'Toronto'
);


-- 38. Руководителям департаментов в стране 'United Kingdom' увеличить зарплату на 600.

update employees
set salary = salary + 600
where employee_id in (
    select manager_id
    from departments d
    join locations l on d.location_id = l.location_id
    join countries c on l.country_id = c.country_id
    where c.country_name = 'United Kingdom'
);


-- 41. Сотрудников департамента 60 перевести в департамент 80.

update employees
set department_id = 80
where department_id = 60;
