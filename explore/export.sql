copy (
select
  row_number() over () as time,
  volt,
  rotate,
  pressure,
  vibration,
  error1,
  error2,
  error3,
  error4,
  service1,
  service2,
  service3,
  service4,
  failure1,
  failure2,
  failure3,
  failure4
from
  machine_log
where
  machineid = '746'
order by datetime asc
) to '/explore/build/machine_746.csv' (format CSV, header true);
