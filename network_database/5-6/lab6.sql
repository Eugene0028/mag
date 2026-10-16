-- Практическая работа №6. SQL-инъекции.
-- Объект анализа: show_sal_filtered из lab5.sql.
-- Перед запуском выполнить lab5.sql в той же базе и схеме.
-- Запускать этот файл целиком. Вывод в pgAdmin: вкладка Messages.
-- Ожидаемые результаты приведены для исходного учебного дампа.

-- 1. Анализ исходной процедуры.
-- p_sname и p_city передаются через EXECUTE ... USING как значения.
-- p_operator включается в текст SQL после проверки на AND или OR.
-- В этих трёх параметрах исходной процедуры уязвимость не обнаружена.
-- Для практического примера создаётся отдельная учебная версия без
-- проверки оператора. Она читает временную копию SAL.

BEGIN;

SET LOCAL client_min_messages = 'notice';

DO $prerequisite$
BEGIN
    IF to_regprocedure('show_sal_filtered(text,text,text)') IS NULL THEN
        RAISE EXCEPTION 'Сначала выполните lab5.sql в этой базе и схеме';
    END IF;
END;
$prerequisite$;

CREATE TEMP TABLE lab6_sal ON COMMIT DROP AS
SELECT snum, sname, city, comm FROM sal;

-- 2. Учебная уязвимая версия.
-- Отличие в защите: отсутствует проверка p_operator на AND или OR.
-- Таблица и процедура созданы внутри транзакции, откатываемой в конце.

CREATE OR REPLACE PROCEDURE pg_temp.lab6_show_sal_unsafe(
    IN p_sname TEXT DEFAULT NULL,
    IN p_city TEXT DEFAULT NULL,
    IN p_operator TEXT DEFAULT 'AND'
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_sql TEXT := 'SELECT s.snum, s.sname, s.city, s.comm FROM pg_temp.lab6_sal s';
    v_operator TEXT := UPPER(BTRIM(p_operator));
    v_seller RECORD;
    v_count INTEGER := 0;
BEGIN
    -- Уязвимость: произвольный текст v_operator становится частью SQL.
    IF p_sname IS NOT NULL AND p_city IS NOT NULL THEN
        v_sql := v_sql || ' WHERE s.sname = $1 ' || v_operator || ' s.city = $2';
    ELSIF p_sname IS NOT NULL THEN
        v_sql := v_sql || ' WHERE s.sname = $1';
    ELSIF p_city IS NOT NULL THEN
        v_sql := v_sql || ' WHERE s.city = $2';
    END IF;

    v_sql := v_sql || ' ORDER BY s.snum';
    RAISE NOTICE 'SQL: %', v_sql;
    RAISE NOTICE 'SNUM | SNAME | CITY | COMM';

    FOR v_seller IN EXECUTE v_sql USING p_sname, p_city
    LOOP
        RAISE NOTICE '% | % | % | %',
            v_seller.snum, v_seller.sname, v_seller.city, v_seller.comm;
        v_count := v_count + 1;
    END LOOP;

    RAISE NOTICE 'Найдено записей: %', v_count;
END;
$$;

-- Обычный вызов: продавца Peel из San Jose нет, найдено 0 записей.
CALL pg_temp.lab6_show_sal_unsafe('Peel', 'San Jose', 'AND');

-- Атака через параметр оператора: найдено 5 записей вместо 0.
-- Получится одна строка SQL:
-- SELECT ... WHERE s.sname = $1 OR TRUE -- s.city = $2 ORDER BY s.snum
-- OR TRUE делает условие истинным для всех строк.
-- Два дефиса комментируют оставшуюся часть запроса, включая город.
CALL pg_temp.lab6_show_sal_unsafe('Peel', 'San Jose', 'OR TRUE --');


-- 3. Защита в show_sal_filtered из пятой лабораторной.
-- Там перед сборкой SQL уже есть:
-- IF v_operator IS NULL OR v_operator NOT IN ('AND', 'OR') THEN
--     RAISE EXCEPTION ... USING ERRCODE = '22023';
-- END IF;
-- Имена и города остаются значениями $1/$2 благодаря USING.

-- Нормальные условия продолжают работать: 0 записей для AND, 2 для OR.
CALL show_sal_filtered('Peel', 'San Jose', 'AND');
CALL show_sal_filtered('Peel', 'San Jose', 'OR');

-- Та же атака блокируется до выполнения динамического SELECT.
-- Обрабатываем ожидаемое исключение, чтобы выполнить оставшиеся примеры.
DO $blocked_attack$
BEGIN
    CALL show_sal_filtered('Peel', 'San Jose', 'OR TRUE --');
EXCEPTION
    WHEN SQLSTATE '22023' THEN
        RAISE NOTICE 'Атака отклонена, SQLSTATE = %: %', SQLSTATE, SQLERRM;
END;
$blocked_attack$;

-- Строка инъекции в имени воспринимается как буквальное имя: 0 записей.
CALL show_sal_filtered(p_sname => $payload$' OR 1=1 --$payload$);

-- Строка инъекции в городе воспринимается как название города: 0 записей.
CALL show_sal_filtered(p_city => $payload$' OR 1=1 --$payload$);

-- Основные меры защиты:
-- 1) Передавать значения параметрами: EXECUTE ... USING / prepared statements.
-- 2) Выбирать операторы и другие части синтаксиса из разрешённого списка.
-- 3) Использовать статический SQL, когда динамическая сборка не требуется.
-- 4) Выдавать роли приложения только необходимые права.
-- Реализация show_sal_filtered уже содержит первые две меры.

ROLLBACK;
