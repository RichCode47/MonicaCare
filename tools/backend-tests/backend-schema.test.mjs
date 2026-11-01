import { test, before, after, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';

let db;
const A = '11111111-1111-4111-8111-111111111111';
const B = '22222222-2222-4222-8222-222222222222';
const P = '33333333-3333-4333-8333-333333333333';
const Q = '44444444-4444-4444-8444-444444444444';
const M = '55555555-5555-4555-8555-555555555555';
const N = '66666666-6666-4666-8666-666666666666';

before(async () => {
  db = new PGlite();
  await db.exec(`
    create role anon;
    create role authenticated;
    create schema auth;
    create schema storage;
    create schema private;
    grant usage on schema private to authenticated;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as
      $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    grant usage on schema auth,storage to anon,authenticated;
    grant execute on function auth.uid() to anon,authenticated;
    create table storage.buckets(id text primary key,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]);
    create table storage.objects(id uuid primary key default gen_random_uuid(),bucket_id text references storage.buckets(id),name text);
    alter table storage.objects enable row level security;
    grant select,insert,delete on storage.objects to authenticated;
    create function storage.foldername(text) returns text[] language sql immutable as
      $$ select (string_to_array($1,'/'))[1:cardinality(string_to_array($1,'/'))-1] $$;
    insert into auth.users values ('${A}'),('${B}');
  `);
  const migration = await readFile(new URL('../../supabase/migrations/202611010001_monicacare_foundation.sql', import.meta.url), 'utf8');
  await db.exec(migration);
});
after(async () => { await db?.close(); });
beforeEach(async () => { await db.exec('reset role; begin'); });
afterEach(async () => { await db.exec('rollback; reset role'); });

async function as(role, user = '') {
  await db.exec('reset role');
  await db.query("select set_config('request.jwt.claim.sub',$1,false)", [user]);
  await db.exec('set role ' + role);
}
const pregnancy = () => ({
  display_name: 'Isolated test', last_menstrual_period: '2026-06-01',
  estimated_due_date: '2027-03-08', due_date_source: 'lmp',
  is_clinician_confirmed: false, pregnancy_status: 'active',
  healthcare_facility: '', emergency_contact_name: '', emergency_contact_phone: '',
  pregnancy_notes: '', deleted_at: null
});
async function put(table, id, version, payload, mutation = M) {
  const r = await db.query('select public.sync_private_record($1,$2::uuid,$3::bigint,$4::uuid,$5::jsonb) as record',
    [table,id,version,mutation,JSON.stringify(payload)]);
  return r.rows[0].record;
}
async function rejects(block, code) {
  await db.exec('savepoint rejected_operation');
  let error;
  try { await block(); } catch (e) { error = e; }
  await db.exec('rollback to savepoint rejected_operation');
  assert.ok(error, 'Expected operation to be denied');
  if (code) assert.equal(error.code, code, error.message);
}

test('all eight public application tables enforce RLS', async () => {
  const result = await db.query("select relname,relrowsecurity,relforcerowsecurity from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind='r'");
  assert.equal(result.rows.length, 8);
  assert.ok(result.rows.every(x => x.relrowsecurity && x.relforcerowsecurity));
});
test('unrelated existing private schema permissions are preserved', async () => {
  assert.equal((await db.query("select has_schema_privilege('authenticated','private','usage') as allowed")).rows[0].allowed,true);
});
test('owner can create/read/update; another user cannot see or change the row', async () => {
  await as('authenticated', A);
  const created = await put('pregnancy_profiles', P, 0, pregnancy());
  assert.equal(created.user_id, A);
  assert.equal(created.version, 1);
  const edited = await put('pregnancy_profiles', P, 1, {...pregnancy(), pregnancy_notes:'Updated'}, N);
  assert.equal(edited.version, 2);
  await as('authenticated', B);
  assert.equal((await db.query('select * from public.pregnancy_profiles')).rows.length, 0);
  assert.equal((await db.query('update public.pregnancy_profiles set pregnancy_notes=$1 where id=$2 returning id',['Forbidden',P])).rows.length, 0);
});
test('forged JSON ownership cannot create records under another account', async () => {
  await as('authenticated', A);
  const created = await put('pregnancy_profiles', P, 0, {...pregnancy(), user_id:B, version:999});
  assert.equal(created.user_id, A);
  assert.equal(created.version, 1);
  await rejects(() => db.query('insert into public.profiles(id,display_name) values ($1,$2)',[B,'Forbidden']), '42501');
});
test('anonymous access to private records and sync RPC is denied', async () => {
  await as('anon');
  await rejects(() => db.query('select * from public.health_journal'), '42501');
  await rejects(() => put('pregnancy_profiles',P,0,pregnancy()), '42501');
});
test('cross-owner pregnancy foreign keys are rejected for appointments and journal entries', async () => {
  await as('authenticated', B);
  await put('pregnancy_profiles', P, 0, pregnancy());
  await as('authenticated', A);
  const appointment = {pregnancy_profile_id:P,title:'Test visit',appointment_date:'2026-12-01',
    appointment_time:'10:30',time_zone:'Africa/Lusaka',facility_name:'',provider_name:'',notes:'',status:'upcoming',deleted_at:null};
  await rejects(() => put('appointments',Q,0,appointment), '23503');
  await rejects(() => put('health_journal',Q,0,{pregnancy_profile_id:P,entry_date:'2026-11-01',category:'notes',notes:'Test',deleted_at:null}), '23503');
});
test('idempotent retries succeed and stale versions conflict without losing server data', async () => {
  await as('authenticated', A);
  const first = await put('pregnancy_profiles',P,0,pregnancy());
  const retry = await put('pregnancy_profiles',P,0,pregnancy());
  assert.equal(retry.version, first.version);
  await put('pregnancy_profiles',P,1,{...pregnancy(),pregnancy_notes:'Server changes'},N);
  await rejects(() => put('pregnancy_profiles',P,1,{...pregnancy(),pregnancy_notes:'Stale'},'77777777-7777-4777-8777-777777777777'), 'P0001');
  assert.equal((await db.query('select pregnancy_notes from public.pregnancy_profiles')).rows[0].pregnancy_notes,'Server changes');
});
test('public catalog exposes only reviewed/published content and forbids client writes', async () => {
  await db.exec(`insert into public.educational_articles(title,category,content,published,reviewed_at) values
    ('Draft','Test','Draft content',false,null),('Reviewed','Test','Reviewed content',true,now())`);
  await as('anon');
  assert.equal((await db.query('select title from public.educational_articles')).rows[0].title,'Reviewed');
  await rejects(() => db.query("insert into public.educational_articles(title,category,content) values ('X','X','X')"),'42501');
});
test('private storage permits only the authenticated user folder', async () => {
  assert.equal((await db.query("select public from storage.buckets where id='monicacare-private'")).rows[0].public,false);
  await as('authenticated', A);
  await db.query("insert into storage.objects(bucket_id,name) values ('monicacare-private',$1)",[A+'/test.pdf']);
  await rejects(() => db.query("insert into storage.objects(bucket_id,name) values ('monicacare-private',$1)",[B+'/forbidden.pdf']), '42501');
  await as('authenticated', B);
  assert.equal((await db.query('select * from storage.objects')).rows.length,0);
});
test('only one active pregnancy per owner and valid dating is enforced', async () => {
  await as('authenticated', A);
  await put('pregnancy_profiles',P,0,pregnancy());
  await rejects(() => put('pregnancy_profiles',Q,0,pregnancy()), '23505');
  await rejects(() => put('pregnancy_profiles',P,1,{...pregnancy(),estimated_due_date:'2027-03-09'},N), '23514');
});
test('RPC rejects unknown tables and immutable ownership changes', async () => {
  await as('authenticated', A);
  await rejects(() => put('auth.users',P,0,{}), '22023');
  await put('pregnancy_profiles',P,0,pregnancy());
  await rejects(() => db.query('update public.pregnancy_profiles set user_id=$1 where id=$2',[B,P]), '23514');
});
test('infinite dates and null table requests fail closed', async () => {
  await as('authenticated', A);
  await rejects(() => put('pregnancy_profiles',P,0,{...pregnancy(),last_menstrual_period:'infinity',estimated_due_date:'infinity'}), '23514');
  await rejects(() => put(null,P,0,pregnancy()), '22023');
});
test('profile/settings identities must be the caller, and deletion uses a versioned marker', async () => {
  await as('authenticated', A);
  await rejects(() => put('profiles',B,0,{display_name:'Forbidden',deleted_at:null}), '42501');
  const profile = await put('profiles',A,0,{display_name:'Test',deleted_at:null});
  assert.equal(profile.id,A);
  const settings = await put('user_settings',A,0,{preferred_language:'en',notification_preferences:{},deleted_at:null});
  assert.equal(settings.user_id,A);
  await put('pregnancy_profiles',P,0,pregnancy());
  const removed = await put('pregnancy_profiles',P,1,{...pregnancy(),deleted_at:'2026-11-01T10:00:00Z'},N);
  assert.ok(removed.deleted_at);
  await rejects(() => db.query('delete from public.pregnancy_profiles where id=$1',[P]), '42501');
});
