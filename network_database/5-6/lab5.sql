-- Разработать процедуру, реализующую вывод
-- содержимого таблицы SAL на основании
-- ограничений, задаваемых на поля: SNAME, CITY.
--  Предусмотреть возможность задания ограничений
-- только на одно из полей.
--  Реализовать возможность использования при
-- формировании ограничений на строки таблицы SAL
-- операторов AND и OR.
--  Необходимая для формирования ограничений
-- информация передается в качестве параметров
-- процедуры.

CREATE OR REPLACE PROCEDURE show_sal_filtered(
    IN p_sname TEXT DEFAULT NULL,
    IN p_city TEXT DEFAULT NULL,
    IN p_operator TEXT DEFAULT 'AND'
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_sql TEXT := 'SELECT s.snum, s.sname, s.city, s.comm FROM sal s';
    v_operator TEXT := UPPER(BTRIM(p_operator));
    v_seller RECORD;
    v_count INTEGER := 0;
BEGIN
    IF v_operator IS NULL OR v_operator NOT IN ('AND', 'OR') THEN
        RAISE EXCEPTION 'Допустимые операторы: AND и OR. Получено: %', p_operator
            USING ERRCODE = '22023';
    END IF;

    IF p_sname IS NOT NULL AND p_city IS NOT NULL THEN
        v_sql := v_sql || ' WHERE s.sname = $1 ' || v_operator || ' s.city = $2';
    ELSIF p_sname IS NOT NULL THEN
        v_sql := v_sql || ' WHERE s.sname = $1';
    ELSIF p_city IS NOT NULL THEN
        v_sql := v_sql || ' WHERE s.city = $2';
    END IF;

    v_sql := v_sql || ' ORDER BY s.snum';

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



-- Оба условия через AND: Peel из London, SNUM = 1001.
CALL show_sal_filtered('Peel', 'London', 'AND');

-- Ограничение только по имени: Peel, SNUM = 1001.
CALL show_sal_filtered(p_sname => 'Peel');

-- Ограничение только по городу: London, SNUM = 1001 и 1004.
CALL show_sal_filtered(p_city => 'London');

-- AND: продавца Peel из San Jose нет, поэтому найдено 0 записей.
CALL show_sal_filtered('Peel', 'San Jose', 'AND');

-- OR: Peel из любого города или любой продавец из San Jose.
-- SNUM = 1001 и 1002.
CALL show_sal_filtered('Peel', 'San Jose', 'OR');

-- Оба фильтра не заданы: все 5 продавцов.
CALL show_sal_filtered();
