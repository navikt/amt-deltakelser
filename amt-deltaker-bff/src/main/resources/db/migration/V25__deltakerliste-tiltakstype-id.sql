alter table deltakerliste drop column tiltaksnavn;

alter table deltakerliste add column tiltakstype_id uuid references tiltakstype;

update deltakerliste
set tiltakstype_id = t.id
from tiltakstype t
where t.type = deltakerliste.tiltakstype;

alter table deltakerliste alter column tiltakstype_id set not null;

alter table deltakerliste drop column tiltakstype;