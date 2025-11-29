

-- 6. По данным из таблицы HR.EMPLOYEES сформировать перечень менеджеров с указанием идентификатора менеджера (manager_id) и количества сотрудников у него в подчинении.
select manager_id, count(employee_id) 
from employees 
where manager_id is not null 
group by manager_id;



-- 9. По данным из таблицы HR.EMPLOYEES сформировать список тех менеджеров, 
-- у которых в подчинении находится более 6 сотрудников, получающих месячную зарплату (SALARY) в интервале от 2000 до 12000.


select e1.manager_id 
from employees e1 
where e1.manager_id is not null and e1.salary between 2000 and 12000 
group by e1.manager_id
having count(e1.employee_id) > 6;


-- 14. По данным из таблицы HR.EMPLOYEES сформировать список должностей, для которых средняя зарплата превышает 10000.

select job_id
from employees
group by job_id
having avg(salary) > 10000;

-- 17. По данным из таблицы HR.EMPLOYEES найти минимальную и максимальную зарплату для каждого департамента.


select department_id, min(salary), max(salary)
from employees
group by department_id;

-- 18. По данным из таблицы HR.EMPLOYEES найти количество сотрудников и фонд заработной платы для каждого департамента.

select department_id, count(employee_id), sum(salary)
from employees
group by department_id;


-- 27. По данным из таблицы HR.EMPLOYEES сформировать список должностей (JOB_ID) с указанием по каждой из них количества сотрудников, получающих комиссионную надбавку.

select job_id, count(employee_id)
from employees
where commission_pct is not null and commission_pct > 0
group by job_id;


-- 31.Используя данные из таблицы HR.EMPLOYEES, по каждой должности определить годы, для которых число сотрудников, принятых на работу, превышало 5 (см. подсказку).


select job_id,
       TO_CHAR(hire_date, 'YYYY') as hire_year,
       count(employee_id)
from employees
group by job_id, TO_CHAR(hire_date, 'YYYY')
having count(employee_id) > 5;


