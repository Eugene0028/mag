-- 2. Путем самосоединения таблицы EMPLOYEES получить список сотрудников с указанием для каждого из них фамилии, 
-- имени и телефонного номера его непосредственного начальника. Включить в список даже тех сотрудников, у которых нет начальников в штате компании.

select e1.last_name, e1.first_name, e2.phone_number as boss_number  
from employees e1 
left join employees e2 on e1.manager_id = e2.employee_id;



-- 6. Путем соединения таблиц HR.DEPARTMENTS и HR.EMPLOYEES отобразить полные данные о департаментах, в которых максимальная зарплата выше 10000.
select distinct d.*
from departments d
join employees e on d.department_id = e.department_id
where e.salary > 10000;

-- 13. Путем соединения таблиц HR.EMPLOYEES и HR.DEPARTMENTS сформировать список, в котором для каждого сотрудника отобразить 
-- имя, фамилию, номер телефона, номер отдела и полное название отдела. Список ограничить сотрудниками, у которых стаж работы выше 16 лет.

select e.first_name, e.last_name, e.phone_number, e.department_id, d.department_name 
from employees e 
join departments d on e.department_id = d.department_id and extract(year from age(current_date, hire_date)) > 16;


-- 15. Путем соединения таблиц HR.EMPLOYEES и HR.DEPARTMENTS сформировать список сотрудников, которые являются руководителями департаментов. 
-- Список должен содержать имя и фамилию, оклад, идентификатор департамента, номер телефона и e-mail.

select e.first_name, e.last_name, e.salary, e.department_id, e.phone_number, e.email 
from employees e 
join departments d on e.employee_id = d.manager_id;


-- 19. Путем соединения таблиц HR.EMPLOYEES, HR.DEPARTMENTS и HR.LOCATIONS получить список, в котором для каждого сотрудника отобразить 
-- имя и фамилию, полное название департамента, город и код страны. Список ограничить кодами страны CA и DE.

select e.first_name, e.last_name, d.department_name, l.city, l.country_id 
from employees e 
join departments d on e.department_id = d.department_id 
join locations l on d.location_id = l.location_id
where l.country_id in ('CA', 'DE');

-- 20. Путем соединения таблиц HR.EMPLOYEES, HR.DEPARTMENTS и HR.LOCATIONS посчитать, сколько сотрудников работает в разных странах.
select l.country_id, count(*)
from employees e 
join departments d on e.department_id = d.department_id
join locations l on d.location_id = l.location_id
group by l.country_id;

-- 21. Путем соединения таблиц HR.EMPLOYEES, HR.DEPARTMENTS и HR.LOCATIONS посчитать средний стаж сотрудников, которые работают в разных странах.


select l.country_id, avg(age(current_date, e.hire_date)) 
from employees e 
join departments d on e.department_id = d.department_id 
join locations l on d.location_id = l.location_id 
group by country_id;

