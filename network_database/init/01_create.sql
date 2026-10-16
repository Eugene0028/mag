-- PostgreSQL version of CREATE.SQL. Encoding: UTF-8.

BEGIN;

--	Построение таблиц для учебной базы данных
--
--
--
--	создание таблицы продавцов - SAL
--
create table SAL
  (SNUM NUMERIC(4),
   SNAME VARCHAR(10) NOT NULL,
   CITY  VARCHAR(10) NOT NULL,
   COMM  NUMERIC(7,2) NOT NULL);
--
--	создание таблицы заказчиков - CUST
--
create table CUST
  (CNUM NUMERIC(4),
   CNAME VARCHAR(10) NOT NULL,
   CITY  VARCHAR(10) NOT NULL,
   RATING NUMERIC(3) NOT NULL,
   SNUM NUMERIC(4));
--
--	создание таблицы заказов - ORD
--
create table ORD
  (ONUM NUMERIC(4),
   AMT  NUMERIC(7,2) NOT NULL,
   ODATE date NOT NULL,
   CNUM NUMERIC(4),
   SNUM NUMERIC(4));
--
--	определение первичного ключа таблицы sal
--
ALTER TABLE sal
   ADD CONSTRAINT sal_pk_snum PRIMARY KEY (snum);
--
--	определение первичного и внешнего ключей таблицы cust
--
ALTER TABLE cust
   ADD CONSTRAINT cust_pk_cnum PRIMARY KEY (cnum),
        ADD CONSTRAINT cust_fk_snum FOREIGN KEY (snum)
        REFERENCES sal(snum);
--
--	определение первичного и внешних ключей таблицы ord
--
ALTER TABLE ord
   ADD CONSTRAINT ord_pk_onum PRIMARY KEY (onum),
        ADD CONSTRAINT ord_fk_cnum FOREIGN KEY (cnum)
        REFERENCES cust(cnum),
        ADD CONSTRAINT ord_fk_snum FOREIGN KEY (snum)
        REFERENCES sal(snum);

COMMIT;
