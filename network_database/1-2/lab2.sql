-- Напишите команду SELECT, использующую соотнесенный подзапрос, которая
-- выберет имена и номера всех заказчиков с максимальными для их городов
-- оценками
select c.cnum, c.cname from cust c where c.rating = (select max(rating) from cust c2 where c.city = c2.city);

-- Напишите два запроса, отображающие продавцов, которые в своих городах
-- имеют заказчиков, которых они не обслуживают. Один запрос — с
-- использованием объединения и один — с соотнесенным подзапросом.

select distinct (s.*) from sal s join cust c on c.snum != s.snum and s.city = c.city;

select * from sal s where s.city in (select c.city from cust c where c.city = s.city and c.snum != s.snum);


-- len("Флат") % 2 == 0, 1 вариант

--Напишите запрос, который определяет и выводит на экран последнее
-- неиспользованное значение кода (CNUM) большее 2003 в таблице
-- Покупателей двумя способами (через соотнесенные подзапросы и outer join).
select max(c.cnum - 1) from cust c where not exists(select 1 from cust c2 where c.cnum - 1 = c2.cnum) and c.cnum - 1 > 2003;

select max(c.cnum - 1) from cust c left join cust c2 on c.cnum - 1 = c2.cnum where c2.cnum is null and c.cnum - 1 > 2003;


-- Вывести на экран всех покупателей, в городе которых живут другие покупатели

select distinct c.* from cust c join cust c2 on c.cnum != c2.cnum where c2.city = c.city;

-- Вывести на экран покупателей имеющих рейтинг больше, чем рейтинг любого
-- покупателя не из его города.

select * from cust c where c.rating > all(select c2.rating from cust c2 where c.cnum != c2.cnum and c.city != c2.city);

