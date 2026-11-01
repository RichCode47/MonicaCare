-- MonicaCare backend foundation. Apply to a reviewed, linked Supabase project.
-- Existing tables are deliberately not replaced. A name collision must be reviewed.
begin;

create schema monicacare_private;
revoke all on schema monicacare_private from public, anon, authenticated;

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '' check (char_length(display_name) <= 100),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version bigint not null default 1 check (version > 0),
  last_mutation_id uuid,
  deleted_at timestamptz
);
create table public.pregnancy_profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  display_name text not null default '' check (char_length(display_name) <= 100),
  last_menstrual_period date check (last_menstrual_period is null or isfinite(last_menstrual_period)),
  estimated_due_date date not null check (isfinite(estimated_due_date)),
  due_date_source text not null check (due_date_source in ('lmp','clinician')),
  is_clinician_confirmed boolean not null default false,
  pregnancy_status text not null default 'active' check (pregnancy_status in ('active','completed','archived')),
  healthcare_facility text not null default '' check (char_length(healthcare_facility) <= 200),
  emergency_contact_name text not null default '' check (char_length(emergency_contact_name) <= 100),
  emergency_contact_phone text not null default '' check (char_length(emergency_contact_phone) <= 40),
  pregnancy_notes text not null default '' check (char_length(pregnancy_notes) <= 5000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version bigint not null default 1 check (version > 0),
  last_mutation_id uuid,
  deleted_at timestamptz,
  unique (id,user_id),
  check (due_date_source <> 'lmp' or
    (last_menstrual_period is not null and estimated_due_date = last_menstrual_period + 280))
);
create unique index pregnancy_one_active_per_user on public.pregnancy_profiles(user_id)
  where pregnancy_status = 'active' and deleted_at is null;
create index pregnancy_owner_updated on public.pregnancy_profiles(user_id,updated_at,id);

create table public.appointments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  pregnancy_profile_id uuid,
  title text not null check (char_length(trim(title)) between 1 and 200),
  appointment_date date not null check (isfinite(appointment_date)),
  appointment_time time not null,
  time_zone text not null default 'Africa/Lusaka' check (time_zone = 'Africa/Lusaka'),
  facility_name text not null default '' check (char_length(facility_name) <= 200),
  provider_name text not null default '' check (char_length(provider_name) <= 100),
  notes text not null default '' check (char_length(notes) <= 5000),
  status text not null default 'upcoming' check (status in ('upcoming','completed','cancelled')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version bigint not null default 1 check (version > 0),
  last_mutation_id uuid,
  deleted_at timestamptz,
  foreign key (pregnancy_profile_id,user_id) references public.pregnancy_profiles(id,user_id)
);
create index appointments_owner_date on public.appointments(user_id,appointment_date,id);
create index appointments_pregnancy on public.appointments(pregnancy_profile_id,user_id);

create table public.health_journal (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  pregnancy_profile_id uuid,
  entry_date date not null check (isfinite(entry_date)),
  category text not null check (category in ('blood_pressure','weight','symptoms','labs','notes')),
  notes text not null check (char_length(trim(notes)) between 1 and 5000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version bigint not null default 1 check (version > 0),
  last_mutation_id uuid,
  deleted_at timestamptz,
  foreign key (pregnancy_profile_id,user_id) references public.pregnancy_profiles(id,user_id)
);
create index journal_owner_date on public.health_journal(user_id,entry_date,id);
create index journal_pregnancy on public.health_journal(pregnancy_profile_id,user_id);

create table public.user_settings (
  user_id uuid primary key references auth.users(id) on delete cascade,
  preferred_language text not null default 'en' check (preferred_language in ('en','bem','ny')),
  notification_preferences jsonb not null default '{"appointments":false,"journal":false}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version bigint not null default 1 check (version > 0),
  last_mutation_id uuid,
  deleted_at timestamptz,
  check (jsonb_typeof(notification_preferences) = 'object' and
    octet_length(notification_preferences::text) <= 1000)
);

create table public.pregnancy_weeks (
  id uuid primary key default gen_random_uuid(),
  week_number integer not null unique check (week_number between 0 and 42),
  title text not null check (char_length(title) between 1 and 200),
  baby_development text not null,
  maternal_changes text not null default '',
  educational_guidance text not null default '',
  fruit_comparison text,
  length_cm numeric check (length_cm > 0 and length_cm < 100),
  weight_grams numeric check (weight_grams > 0 and weight_grams < 10000),
  milestones jsonb not null default '[]'::jsonb check (jsonb_typeof(milestones) = 'array'),
  published boolean not null default false,
  reviewed_at timestamptz,
  updated_at timestamptz not null default now(),
  check (not published or reviewed_at is not null)
);
create table public.educational_articles (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 1 and 200),
  category text not null check (char_length(category) between 1 and 100),
  content text not null check (char_length(content) between 1 and 50000),
  published boolean not null default false,
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (not published or reviewed_at is not null)
);
create index articles_published_category on public.educational_articles(category,id) where published;
create table public.emergency_contacts (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 1 and 200),
  phone_number text not null check (char_length(phone_number) between 3 and 40),
  category text not null check (char_length(category) between 1 and 100),
  location text not null default 'Zambia',
  is_active boolean not null default false,
  verified_at timestamptz,
  updated_at timestamptz not null default now(),
  check (not is_active or verified_at is not null)
);
create index contacts_active_location on public.emergency_contacts(location,id) where is_active;

-- Server-managed version/time fields. Direct REST updates also advance the version.
create function monicacare_private.record_version() returns trigger
language plpgsql set search_path = '' as $$
declare previous jsonb; incoming jsonb; owner_column text;
begin
  incoming := to_jsonb(new);
  owner_column := case when TG_TABLE_NAME = 'profiles' then 'id' else 'user_id' end;
  if TG_OP = 'UPDATE' then
    previous := to_jsonb(old);
    if incoming->>owner_column is distinct from previous->>owner_column
       or incoming->>'id' is distinct from previous->>'id' then
      raise exception using errcode='23514',message='Record identity is immutable';
    end if;
    incoming := incoming || jsonb_build_object('version',(previous->>'version')::bigint+1,
      'created_at',previous->'created_at','updated_at',clock_timestamp());
  else
    incoming := incoming || jsonb_build_object('version',1,'created_at',clock_timestamp(),
      'updated_at',clock_timestamp());
  end if;
  new := jsonb_populate_record(new,incoming);
  return new;
end;
$$;
revoke all on function monicacare_private.record_version() from public,anon,authenticated;

do $$
declare t text; owner_column text;
begin
  foreach t in array array['profiles','pregnancy_profiles','appointments','health_journal','user_settings'] loop
    owner_column := case when t='profiles' then 'id' else 'user_id' end;
    execute format('alter table public.%I enable row level security',t);
    execute format('alter table public.%I force row level security',t);
    execute format('revoke all on public.%I from public,anon,authenticated',t);
    execute format('grant select,insert,update on public.%I to authenticated',t);
    execute format('create policy own_read on public.%I for select to authenticated using ((select auth.uid())=%I)',t,owner_column);
    execute format('create policy own_create on public.%I for insert to authenticated with check ((select auth.uid())=%I)',t,owner_column);
    execute format('create policy own_update on public.%I for update to authenticated using ((select auth.uid())=%I) with check ((select auth.uid())=%I)',t,owner_column,owner_column);
    execute format('create trigger record_version before insert or update on public.%I for each row execute function monicacare_private.record_version()',t);
  end loop;
end;
$$;
do $$
declare t text; visibility text;
begin
  foreach t in array array['pregnancy_weeks','educational_articles','emergency_contacts'] loop
    visibility := case when t='emergency_contacts' then 'is_active' else 'published' end;
    execute format('alter table public.%I enable row level security',t);
    execute format('alter table public.%I force row level security',t);
    execute format('revoke all on public.%I from public,anon,authenticated',t);
    execute format('grant select on public.%I to anon,authenticated',t);
    execute format('create policy published_read on public.%I for select to anon,authenticated using (%I)',t,visibility);
  end loop;
end;
$$;

-- Optimistic, idempotent writes. SECURITY INVOKER keeps every write under RLS.
-- Ownership is assigned from the JWT, never from JSON supplied by a client.
create function public.sync_private_record(
  p_table text,p_id uuid,p_expected_version bigint,p_mutation_id uuid,p_payload jsonb
) returns jsonb language plpgsql security invoker set search_path='' as $$
declare uid uuid := auth.uid(); key_column text; owner_column text;
  existing jsonb; next_row jsonb; result jsonb; assignments text;
begin
  if uid is null then raise exception using errcode='42501',message='Authentication required'; end if;
  if p_table is null or p_table not in ('profiles','pregnancy_profiles','appointments','health_journal','user_settings')
     or p_id is null or p_mutation_id is null or p_expected_version < 0
     or p_expected_version is null or jsonb_typeof(p_payload) <> 'object' or p_payload is null then
    raise exception using errcode='22023',message='Invalid sync request';
  end if;
  if octet_length(p_payload::text)>20000 then
    raise exception using errcode='22023',message='Record exceeds sync limit';
  end if;
  key_column := case when p_table='user_settings' then 'user_id' else 'id' end;
  owner_column := case when p_table='profiles' then 'id' else 'user_id' end;
  if p_table in ('profiles','user_settings') and p_id <> uid then
    raise exception using errcode='42501',message='Invalid record owner';
  end if;
  -- Serializes simultaneous creation as well as updates to this identity.
  perform pg_advisory_xact_lock(hashtextextended(p_table||':'||p_id::text,0));
  execute format('select to_jsonb(r) from public.%I r where %I=$1 and %I=$2 for update',p_table,key_column,owner_column)
    into existing using p_id,uid;
  if existing->>'last_mutation_id'=p_mutation_id::text then return existing; end if;
  if coalesce((existing->>'version')::bigint,0) <> p_expected_version then
    raise exception using errcode='P0001',message='SYNC_CONFLICT';
  end if;
  next_row := coalesce(existing,'{}'::jsonb) ||
    (p_payload - array['id','user_id','version','last_mutation_id','created_at','updated_at']) ||
    jsonb_build_object(key_column,p_id,owner_column,uid,'last_mutation_id',p_mutation_id,
      'version',coalesce((existing->>'version')::bigint,0)+1,
      'created_at',coalesce(existing->'created_at',to_jsonb(clock_timestamp())),
      'updated_at',clock_timestamp());
  if existing is null then
    execute format('insert into public.%I select (jsonb_populate_record(null::public.%I,$1)).* returning to_jsonb(%I.*)',
      p_table,p_table,p_table) into result using next_row;
  else
    select string_agg(format('%1$I=x.%1$I',a.attname),',') into assignments
      from pg_attribute a where a.attrelid=format('public.%I',p_table)::regclass
        and a.attnum>0 and not a.attisdropped and a.attname not in ('id','user_id');
    execute format('update public.%I r set %s from jsonb_populate_record(null::public.%I,$1) x where r.%I=$2 and r.%I=$3 returning to_jsonb(r.*)',
      p_table,assignments,p_table,key_column,owner_column) into result using next_row,p_id,uid;
  end if;
  return result;
end;
$$;
revoke all on function public.sync_private_record(text,uuid,bigint,uuid,jsonb) from public,anon;
grant execute on function public.sync_private_record(text,uuid,bigint,uuid,jsonb) to authenticated;

-- Private uploads only. No public URL access, no overwrite, no client bucket management.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('monicacare-private','monicacare-private',false,5242880,
  array['application/pdf','image/jpeg','image/png']);
create policy monicacare_storage_read on storage.objects for select to authenticated
using (bucket_id='monicacare-private' and (storage.foldername(name))[1]=(select auth.uid())::text);
create policy monicacare_storage_create on storage.objects for insert to authenticated
with check (bucket_id='monicacare-private' and (storage.foldername(name))[1]=(select auth.uid())::text);
create policy monicacare_storage_delete on storage.objects for delete to authenticated
using (bucket_id='monicacare-private' and (storage.foldername(name))[1]=(select auth.uid())::text);

commit;
