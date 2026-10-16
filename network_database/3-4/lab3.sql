-- Вывести на экран информацию об организации, для
-- которой сумма ее рыночной стоимости и суммарной
-- рыночной стоимости ее дочерних организаций является
-- максимальной.


with recursive corp_tree as (
    select c.*, c.crp_id as start_id
    from corp c

                union all

    select c.*, ct.start_id
    from corp c
    join corp_tree ct on c.crp_pid = ct.crp_id
)

select start_id, ct.crp_name, sum(crp_worth) as total_worth from corp_tree ct group by start_id order by total_worth desc limit 1;

