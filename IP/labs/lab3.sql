-- 5. По таблице HR.EMPLOYEES сформировать список сотрудников, которые работают в той же должности,
-- что и сотрудник с идентификатором 169. Список должен содержать имя и фамилию, оклад, идентификаторы отдела и должности.


select * from employees where job_id = (select job_id from employees where employee_id = 169);




-- 16. С помощью таблицы HR.EMPLOYEES построить список сотрудников, у которых стаж работы в компании выше среднего стажа. 
-- В этом списке для каждого сотрудника также указать, сколько ему не хватает до максимального стажа.

SELECT
    employee_id,
    first_name,
    last_name,
    age(current_date, hire_date) AS work_experience,
    (
        SELECT max(age(current_date, hire_date))
        FROM employees
    ) - age(current_date, hire_date) AS diff_to_max_experience
FROM employees
WHERE age(current_date, hire_date) > (
    SELECT avg(age(current_date, hire_date))
    FROM employees
);



-- 18. С помощью таблицы HR.EMPLOYEES получить список, в котором из каждого департамента должны быть только сотрудники, имеющие минимальный стаж работы.

select * from employees e1 where hire_date in (select max(hire_date) from employees e2 where e1.department_id = e2.department_id);



-- 23. Используя таблицу HR.DEPARTMENTS, отобразить полные данные о департаментах, которые размещаются там же, 
-- где департамент с номером 90. Исключить из рассмотрения департаменты, для которых не указан код руководителя.


select * from departments where manager_id is not null and  location_id = (select location_id from departments where department_id = 90);



-- 30. Используя таблицы HR.DEPARTMENTS и HR.EMPLOYEES, отобразить полные данные о департаментах, 
-- в которые за период 2008-2009 гг. были приняты на работу новые сотрудники.


select * from departments where department_id in (select department_id from employees where hire_date BETWEEN '2008-01-01' AND '2009-12-31');




-- 35. Используя таблицы HR.JOBS и HR.EMPLOYEES, отобразить полные данные о должностях, на которые за период 2006-2007 гг. не принимали на работу новых сотрудников.

select * from jobs where job_id not in (select job_id from employees where hire_date BETWEEN '2006-01-01' AND '2007-12-31');




-- 40. Используя таблицы HR.EMPLOYEES и HR.DEPARTMENTS, сформировать список сотрудников, 
-- которые являются руководителями департаментов. Список должен содержать имя и фамилию, оклад, идентификатор департамента, номер телефона и e-mail.



select first_name, last_name, salary, department_id, phone_number, email, manager_id from employees where employee_id in (select manager_id from departments);


