-- PostgreSQL version of corp.sql. Encoding: UTF-8.

BEGIN;

CREATE TABLE corp 
   (	crp_id NUMERIC not null, 
	crp_pid NUMERIC, 
	crp_name VARCHAR(20), 
	crp_worth NUMERIC
   )  ;


Insert into CORP (CRP_ID,CRP_PID,CRP_NAME,CRP_WORTH) values (1,null,'MainFactory',900);
Insert into CORP (CRP_ID,CRP_PID,CRP_NAME,CRP_WORTH) values (2,null,'ImportantBank',900);
Insert into CORP (CRP_ID,CRP_PID,CRP_NAME,CRP_WORTH) values (3,1,'AutoFactory',700);
Insert into CORP (CRP_ID,CRP_PID,CRP_NAME,CRP_WORTH) values (4,1,'DevFactory',500);
Insert into CORP (CRP_ID,CRP_PID,CRP_NAME,CRP_WORTH) values (5,2,'SubBank',300);
Insert into CORP (CRP_ID,CRP_PID,CRP_NAME,CRP_WORTH) values (6,2,'UnderBank',500);
Insert into CORP (CRP_ID,CRP_PID,CRP_NAME,CRP_WORTH) values (7,4,'SomeCarFactory',400);
Insert into CORP (CRP_ID,CRP_PID,CRP_NAME,CRP_WORTH) values (8,4,'AnotherCarFactory',400);
Insert into CORP (CRP_ID,CRP_PID,CRP_NAME,CRP_WORTH) values (9,5,'MicroBank',100);
--------------------------------------------------------
--  DDL for Index CORP_PK
--------------------------------------------------------

  CREATE UNIQUE INDEX corp_pk ON corp (crp_id);
--------------------------------------------------------
--  Constraints for Table CORP
--------------------------------------------------------

  ALTER TABLE corp ADD CONSTRAINT corp_pk PRIMARY KEY USING INDEX corp_pk;

COMMIT;
