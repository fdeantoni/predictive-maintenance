create table telemetry (datetime timestamp, machineID float, volt float, rotate float, pressure float, vibration float);
copy telemetry(datetime, machineid, volt, rotate, pressure, vibration) from '/explore/build/telemetry.csv' delimiter ',' csv header;
create index telemetry_machineid_datetime_idx on telemetry (machineid, datetime asc);

create table errors(datetime timestamp, machineid float, errorid varchar(50));
copy errors(datetime, machineid, errorid) from '/explore/build/errors.csv' delimiter ',' csv header;
create index errors_machineid_datetime_idx on errors (machineid, datetime asc);

create table maint(datetime timestamp, machineid float, comp varchar(50));
copy maint(datetime, machineid, comp) from '/explore/build/maint.csv' delimiter ',' csv header;
create index maint_machineid_datetime_idx on maint (machineid, datetime asc);

create table machines(machineid float, model varchar(50), age float);
copy machines(machineid, model, age) from '/explore/build/machines.csv' delimiter ',' csv header;
create index machines_machineid_idx on machines (machineid);

create table failures(datetime timestamp, machineid float, failure varchar(50));
copy failures(datetime, machineid, failure) from '/explore/build/failures.csv' delimiter ',' csv header;
create index failures_machineid_datetime_idx on failures (machineid, datetime asc);

create table if not exists machine_log as
select telemetry.datetime,
       telemetry.machineid,
       telemetry.volt,
       telemetry.rotate,
       telemetry.pressure,
       telemetry.vibration,
       coalesce(f_comp1.failure, 0) as failure1,
       coalesce(s_comp1.service, 0) as service1,
       coalesce(f_comp2.failure, 0) as failure2,
       coalesce(s_comp2.service, 0) as service2,
       coalesce(f_comp3.failure, 0) as failure3,
       coalesce(s_comp3.service, 0) as service3,
       coalesce(f_comp4.failure, 0) as failure4,
       coalesce(s_comp4.service, 0) as service4,
       coalesce(error1.error, 0) as error1,
       coalesce(error2.error, 0) as error2,
       coalesce(error3.error, 0) as error3,
       coalesce(error4.error, 0) as error4
from
    telemetry inner join machines on telemetry.machineid = machines.machineid
              left join (select machineid, datetime, 1 as failure from failures where failure = 'comp1') f_comp1 on (telemetry.machineid = f_comp1.machineid and telemetry.datetime = f_comp1.datetime)
              left join (select machineid, datetime, 1 as service from maint where comp = 'comp1') s_comp1 on (telemetry.machineid = s_comp1.machineid and telemetry.datetime = s_comp1.datetime)
              left join (select machineid, datetime, 1 as failure from failures where failure = 'comp2') f_comp2 on (telemetry.machineid = f_comp2.machineid and telemetry.datetime = f_comp2.datetime)
              left join (select machineid, datetime, 1 as service from maint where comp = 'comp2') s_comp2 on (telemetry.machineid = s_comp2.machineid and telemetry.datetime = s_comp2.datetime)
              left join (select machineid, datetime, 1 as failure from failures where failure = 'comp3') f_comp3 on (telemetry.machineid = f_comp3.machineid and telemetry.datetime = f_comp3.datetime)
              left join (select machineid, datetime, 1 as service from maint where comp = 'comp3') s_comp3 on (telemetry.machineid = s_comp3.machineid and telemetry.datetime = s_comp3.datetime)
              left join (select machineid, datetime, 1 as failure from failures where failure = 'comp4') f_comp4 on (telemetry.machineid = f_comp4.machineid and telemetry.datetime = f_comp4.datetime)
              left join (select machineid, datetime, 1 as service from maint where comp = 'comp4') s_comp4 on (telemetry.machineid = s_comp4.machineid and telemetry.datetime = s_comp4.datetime)
              left join (select machineid, datetime, 1 as error from errors where errorid = 'error1') error1 on (telemetry.machineid = error1.machineid and telemetry.datetime = error1.datetime)
              left join (select machineid, datetime, 1 as error from errors where errorid = 'error2') error2 on (telemetry.machineid = error2.machineid and telemetry.datetime = error2.datetime)
              left join (select machineid, datetime, 1 as error from errors where errorid = 'error3') error3 on (telemetry.machineid = error3.machineid and telemetry.datetime = error3.datetime)
              left join (select machineid, datetime, 1 as error from errors where errorid = 'error4') error4 on (telemetry.machineid = error4.machineid and telemetry.datetime = error4.datetime);

create index on machine_log (machineid, datetime asc);