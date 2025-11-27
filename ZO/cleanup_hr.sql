-- Полная очистка схемы HR
-- 1) Удаляет все данные из таблиц каскадным TRUNCATE
-- 2) Удаляет сами таблицы (DROP TABLE ... CASCADE)

-- Сначала каскадно чистим данные
TRUNCATE TABLE
    job_history,
    employees,
    jobs,
    departments,
    locations,
    countries,
    regions
CASCADE;

-- Затем удаляем таблицы целиком
DROP TABLE IF EXISTS
    job_history,
    employees,
    jobs,
    departments,
    locations,
    countries,
    regions
CASCADE;


