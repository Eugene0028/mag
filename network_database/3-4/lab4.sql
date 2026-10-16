-- Для каждого игрока вывести количество мячей с
-- пенальти, забитых игроками его амплуа

select sl.sh_id, sl.sh_name, sl.sh_pos, sum(sl.sh_pen) over (partition by sl.sh_pos) from shooter_list sl;


-- Для каждой команды вывести по три лучших
-- бомбардира без учета мячей забитых с пенальти.

select * from (select sl.sh_team, sl.sh_name, sl.sh_goals - sl.sh_pen as goals_without_penalties,
                      row_number() over (
                          partition by sl.sh_team
                          order by sl.sh_goals - sl.sh_pen desc, sl.sh_id) as place
               from shooter_list sl) r where r.place <= 3 order by sh_team, place;


-- Для каждого игрока вывести количество игроков
-- его команды, забивших хотя бы на один мяч
-- больше него.

select sl.sh_id, sl.sh_team, sl.sh_name, sl.sh_goals, rank() over (partition by sl.sh_team order by sl.sh_goals desc) - 1 as r from shooter_list sl;


-- Для каждого результативного футболиста вывести
-- на экран количество минут, проведенных им на
-- поле, и среднее количество минут, проведенных на
-- поле игроками его команды, забивавшими голы.


select sl.sh_id, sl.sh_team, sl.sh_name, sh_min, avg(sl.sh_min) over (partition by sl.sh_team) from shooter_list sl where sl.sh_goals >= 1;

-- Для игроков команды вывести среднее количество
-- минут, проведенных на поле их лучшими
-- бомбардирами.

select sl.sh_id,
       sl.sh_team,
       sl.sh_name, avg(sl.sh_min) filter ( where team_rank = 1 ) over (partition by sl.sh_team)

from (select *, rank() over (partition by sh_team order by sh_goals desc) as team_rank from shooter_list) sl;