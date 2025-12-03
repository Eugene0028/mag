-- 2 Сделать выборку столбцов из таблицы HR.EMPLOYEES с применением псевдонимов на русском языке.
select first_name as "Имя", last_name as "Фамилия" from employees;


-- 5 По данным из таблицы HR.EMPLOYEES получить список неповторяющихся значений кода должности (JOB_ID).

select distinct job_id from employees;

-- 6  Из таблицы HR.EMPLOYEES выдать значения JOB_ID так, чтобы они могли повторяться, но только в разных департаментах. Сделать сортировку по столбцу JOB_ID в порядке от Z к A. 

select distinct (department_id), job_id  from employees order by job_id desc;

-- 8 По данным из таблицы HR.EMPLOYEES получить список неповторяющихся значений для столбца SALARY и упорядочить их по убыванию.

select distinct (salary)  from employees order by salary desc;

-- 24 Из таблицы HR.EMPLOYEES выбрать данные по сотрудникам департамента с номером 30, у которых отсутствует комиссионная надбавка и зарплата (SALARY) не превышает 5000.

select * from employees where department_id = 30 and commission_pct is null and salary <= 5000;


-- 30 По таблице HR.EMPLOYEES получить список сотрудников, у которых имя (FIRST_NAME) и фамилия (LAST_NAME) начинаются с одной и той же буквы S.

select * from employees where first_name like 'S%' and last_name like 'S%';


-- 31 На основе таблице HR.EMPLOYEES построить запрос для получения ответа на вопрос, какой станет зарплата у сотрудников в случае её повышения на 20%.

 select salary as "Текущая ЗП", (salary * 0.2 + salary) as "ЗП + 20%" from employees;

-- 32  По данным из таблицы HR.EMPLOYEES получить список неповторяющихся значений для столбца FIRST_NAME

select distinct first_name as "Имя" from employees;

-- 35 По данным из таблицы HR.JOBS вычислить разницу между максимальной и минимальной зарплатой для каждой должности. Сделать сортировку по увеличению разницы

select min_salary, max_salary, (max_salary - min_salary) as "Разница" from jobs order by "Разница" asc;

-- 42 По данным из таблицы HR.EMPLOYEES получить список неповторяющихся значений для номеров департаментов (DEPARTMENT_ID), к которым относятся сотрудники. Список упорядочить по убыванию.

select distinct department_id from employees order by department_id desc;
