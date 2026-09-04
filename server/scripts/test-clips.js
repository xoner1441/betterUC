"use strict";
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const {fixture,mp4,ACCOUNT,OTHER}=require('./clip-test-fixtures');
const {inspectMp4}=require('../clipMp4');
const {createClipStorage}=require('../clipStorage');
const {key}=require('../clipRoutes');

test('MP4: bounded H.264 structure, dimensions and duration',async()=>{
  for(const settings of [{},{duration:300,width:1920,height:1080}]){const bytes=mp4(settings);const meta=await inspectMp4(async(s,e)=>bytes.subarray(s,e+1),bytes.length);assert.equal(meta.durationSeconds,settings.duration||30);}
  for(const bytes of [mp4({duration:303}),mp4({width:2000}),mp4({codec:'hvc1'}),mp4().subarray(0,80),Buffer.from('<html>not a video</html>')]){
    await assert.rejects(()=>inspectMp4(async(s,e)=>bytes.subarray(s,e+1),bytes.length),{statusCode:415});
  }
});
test('real synthetic exporter MP4 (when local hardware fixture exists)',async t=>{
  let bytes;try{bytes=await fs.readFile(path.join(__dirname,'../../build/clip-test/synthetic-av-sync-60fps-3s-mixed.mp4'));}catch{t.skip('Run Gradle hardware tests to generate synthetic fixture');return;}
  const result=await inspectMp4(async(s,e)=>bytes.subarray(s,e+1),bytes.length);assert.equal(result.durationSeconds,2);assert.equal(result.width,320);assert.equal(result.height,180);
});
test('R2: fail closed and sign content length/type/MD5/create-only; no secret in URL',async()=>{
  assert.equal(createClipStorage({}),null);
  const storage=createClipStorage({CLIP_R2_ACCOUNT_ID:'a'.repeat(32),CLIP_R2_BUCKET:'betteruc-test',CLIP_R2_ACCESS_KEY_ID:'test-key',CLIP_R2_SECRET_ACCESS_KEY:'test-secret'});
  const signed=await storage.uploadUrl('clips/staging/test.mp4',{byteSize:128,md5:'AAAAAAAAAAAAAAAAAAAAAA=='},3600),url=new URL(signed.url);
  const headers=url.searchParams.get('X-Amz-SignedHeaders').split(';');
  for(const h of ['content-length','content-type','content-md5','if-none-match'])assert.ok(headers.includes(h),h);
  assert.equal(signed.headers['if-none-match'],'*');assert.ok(!signed.url.includes('test-secret'));
});
test('owner upload -> validation -> combined private gallery -> unlisted play/download -> deletion',async t=>{
  const f=await fixture();t.after(()=>f.close());
  assert.equal((await f.request('/api/user/media','GET',undefined,'bad',true)).status,401);
  const {response,result}=await f.reserve();assert.equal(response.status,201);const id=result.id;
  assert.equal((await f.request('/api/clips/'+id)).status,404,'pending not visible');
  assert.equal((await f.request('/api/clips/'+id+'/complete','POST',{},'test-other')).status,404);
  const finished=await f.request('/api/clips/'+id+'/complete','POST',{});assert.equal(finished.status,200);assert.equal((await finished.json()).url,'https://betteruc.test/c/'+id);
  assert.equal((await f.request('/api/clips/'+id+'/complete','POST',{})).status,200,'completion idempotent');
  const own=await(await f.request('/api/user/media','GET',undefined,'test-owner',true)).json();assert.deepEqual(new Set(own.media.map(x=>x.kind)),new Set(['clip','screenshot']));
  const other=await(await f.request('/api/user/media','GET',undefined,'test-other',true)).json();assert.deepEqual(other.media,[]);
  assert.equal((await f.request('/api/clips/'+id,'GET',undefined,'no-auth')).status,200,'share link intentionally unlisted');
  assert.equal((await f.request('/api/clips/'+id+'/play')).status,302);assert.equal((await f.request('/api/clips/'+id+'/download')).status,302);
  assert.equal(f.calls.filter(c=>c[0]==='read').at(-1)[3],true);
  assert.equal((await f.request('/api/user/clips/'+id,'DELETE',undefined,'test-other',true)).status,404);
  assert.equal((await f.request('/api/user/clips/'+id,'DELETE',undefined,'test-owner',true)).status,200);
  assert.equal((await f.request('/api/clips/'+id)).status,404);assert.ok(!f.objects.has(key(id,'ready')));assert.ok(f.objects.has(key(id,'stage')),'immutable grant key retained until expiry');
});
test('simultaneous reservations honor daily count; aborted attempts still count',async t=>{
  const f=await fixture({CLIP_DAILY_COUNT:1});t.after(()=>f.close());
  const results=await Promise.all([f.reserve(),f.reserve()]);assert.deepEqual(results.map(r=>r.response.status).sort(),[201,429]);
  const id=results.find(r=>r.response.status===201).result.id;await f.request('/api/clips/'+id,'DELETE');assert.equal((await f.reserve()).response.status,429);
});
test('server enforces bytes, validates poster, authentication and unavailable R2 without breaking screenshots',async t=>{
  const f=await fixture({CLIP_MAX_BYTES:1024,CLIP_DAILY_BYTES:1024});t.after(()=>f.close());
  assert.equal((await f.request('/api/clips','POST',{},'bad')).status,401);
  assert.equal((await f.reserve(Buffer.alloc(1025))).response.status,413);
  assert.equal((await f.request('/api/clips','POST',{byteSize:500,md5:'AAAAAAAAAAAAAAAAAAAAAA==',poster:'aaaa'})).status,400);
  f.deps.storage=null;f.resetRoutes();assert.equal((await f.reserve()).response.status,503);
  const gallery=await(await f.request('/api/user/media','GET',undefined,'test-owner',true)).json();assert.equal(gallery.media.length,1);assert.equal(gallery.clipUploadsEnabled,false);
});
test('invalid MP4 never becomes public, cleanup retries and expires staging',async t=>{
  const f=await fixture();t.after(()=>f.close());const {result}=await f.reserve(Buffer.alloc(100));const id=result.id;
  assert.equal((await f.request('/api/clips/'+id+'/complete','POST',{})).status,415);assert.equal((await f.repo.get(id)).state,'deleted');
  await f.db.query("update clip_uploads set upload_expires_at=now()-interval '10 minutes' where id=$1",[id]);
  const original=f.storage.delete;f.storage.delete=async()=>{throw new Error('offline');};await f.routes.cleanup();assert.ok(f.objects.has(key(id,'stage')));
  f.storage.delete=original;await f.routes.cleanup();assert.equal(f.objects.size,0);assert.equal((await f.repo.get(id)).state,'deleted');
});
test('delete racing finalization cannot republish; expired grants and share links denied',async t=>{
  const f=await fixture();t.after(()=>f.close());const {result}=await f.reserve();const id=result.id;
  const original=f.storage.copy;f.storage.copy=async(...args)=>{await f.repo.remove(id,ACCOUNT);await original(...args);};
  assert.equal((await f.request('/api/clips/'+id+'/complete','POST',{})).status,409);assert.equal((await f.repo.get(id)).state,'deleted');assert.ok(!f.objects.has(key(id,'ready')));
  const second=(await f.reserve()).result.id;
  await f.db.query("update clip_uploads set upload_expires_at=now()-interval '1 minute' where id=$1",[second]);
  assert.equal((await f.request('/api/clips/'+second+'/complete','POST',{})).status,409);
  await f.db.query("update clip_uploads set state='ready',expires_at=now()-interval '1 minute' where id=$1",[second]);
  assert.equal((await f.request('/api/clips/'+second+'/play')).status,404);
});
test('daily bytes and global reserved bytes are enforced in PostgreSQL',async t=>{
  const f=await fixture();t.after(()=>f.close());
  const entry={id:'q'.repeat(32),accountId:ACCOUNT,originalName:'Quota.mp4',byteSize:600,md5:'AAAAAAAAAAAAAAAAAAAAAA==',
    uploadExpiresAt:new Date(Date.now()+3600000).toISOString(),expiresAt:new Date(Date.now()+604800000).toISOString()};
  const limits={dailyCount:5,dailyBytes:1024,totalBytes:1024};
  await f.repo.reserve(entry,limits);
  await assert.rejects(()=>f.repo.reserve({...entry,id:'r'.repeat(32)},limits),{statusCode:429});
  await assert.rejects(()=>f.repo.reserve({...entry,id:'r'.repeat(32),accountId:OTHER},limits),{statusCode:429});
  await f.db.query("update clip_uploads set created_at=now()-interval '25 hours',purged_at=now(),state='deleted'");
  assert.equal((await f.repo.reserve({...entry,id:'r'.repeat(32)},limits)).byteSize,600);
});
test('size mismatch fails; completed video staging expires without deleting live share',async t=>{
  const f=await fixture();t.after(()=>f.close());const first=(await f.reserve()).result.id;
  f.objects.get(key(first,'stage')).body=Buffer.alloc(33);
  assert.equal((await f.request('/api/clips/'+first+'/complete','POST',{})).status,415);
  const second=(await f.reserve()).result.id;assert.equal((await f.request('/api/clips/'+second+'/complete','POST',{})).status,200);
  await f.db.query("update clip_uploads set upload_expires_at=now()-interval '10 minutes'");await f.routes.cleanup();
  assert.ok(!f.objects.has(key(second,'stage')));assert.ok(f.objects.has(key(second,'ready')));assert.ok(f.objects.has(key(second,'poster')));
  assert.equal((await f.request('/api/clips/'+second)).status,200);
  await f.db.query("update clip_uploads set expires_at=now()-interval '1 minute'");await f.routes.cleanup();
  assert.ok(!f.objects.has(key(second,'ready')));assert.equal((await f.request('/api/clips/'+second)).status,404);
});
