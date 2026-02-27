alter table nav_bruker rename column personid to person_id;

alter table deltaker add column person_id uuid references nav_bruker;

update deltaker
set person_id = nb.person_id
from nav_bruker nb
where deltaker.personident = nb.personident;

alter table deltaker alter column person_id set not null;

alter table deltaker drop column personident;