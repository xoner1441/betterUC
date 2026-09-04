"use strict";
// Synthetic test data only. Never connects to production PostgreSQL or Cloudflare.
const {PGlite}=require('@electric-sql/pglite');
const fs=require('node:fs/promises');
const path=require('node:path');
const crypto=require('node:crypto');
const sharp=require('sharp');
const {createClipRepository}=require('../clipRepository');
const {createClipRoutes,key}=require('../clipRoutes');
const ACCOUNT='00000000-0000-4000-8000-000000000001',OTHER='00000000-0000-4000-8000-000000000002';
function box(type,...parts) { const body=Buffer.concat(parts),head=Buffer.alloc(8); head.writeUInt32BE(body.length+8);head.write(type,4);return Buffer.concat([head,body]); }
function mp4({duration=30,width=1280,height=720,codec='avc1'}={}) {
  const mvhd=Buffer.alloc(100);mvhd.writeUInt32BE(1000,12);mvhd.writeUInt32BE(duration*1000,16);
  const tkhd=Buffer.alloc(84);tkhd.writeUInt32BE(width*65536,76);tkhd.writeUInt32BE(height*65536,80);
  const hdlr=Buffer.alloc(24);hdlr.write('vide',8);const stsd=Buffer.alloc(8);stsd.writeUInt32BE(1,4);
  return Buffer.concat([box('ftyp',Buffer.from('isom0000isomavc1')),box('mdat',Buffer.alloc(32)),box('moov',box('mvhd',mvhd),
    box('trak',box('tkhd',tkhd),box('mdia',box('hdlr',hdlr),box('minf',box('stbl',box('stsd',stsd,box(codec,Buffer.alloc(78))))))))]);
}
async function fixture(env={}) {
  const db=new PGlite();
  await db.exec(`create table accounts(id uuid primary key); insert into accounts values('${ACCOUNT}'),('${OTHER}');
    create table screenshot_uploads(id text primary key, account_id uuid, created_at timestamptz, deleted_at timestamptz);`);
  await db.exec(await fs.readFile(path.join(__dirname,'../migrations/012_clip_uploads.sql'),'utf8'));
  // PGlite has one connection. Serialize transaction clients, as a pool of size 1 would.
  let tail=Promise.resolve();
  const pool={query:(...args)=>db.query(...args),async connect(){let unlock;const previous=tail;tail=new Promise(r=>unlock=r);await previous;
    return {query:(...args)=>db.query(...args),release:unlock};}};
  const repo=createClipRepository(pool), objects=new Map(), calls=[];
  const storage={
    async poster(k,body){objects.set(k,{body,type:'image/png'});},
    async uploadUrl(k,entry){calls.push(['sign',k]);return {url:'https://test.r2.cloudflarestorage.com/'+k,headers:{'content-md5':entry.md5}};},
    async head(k){const object=objects.get(k);if(!object)throw new Error('Missing object');return{ContentLength:object.body.length,ContentType:object.type,ETag:'test-etag'};},
    async range(k,start,end,etag){if(etag!=='test-etag')throw new Error('Changed');return objects.get(k).body.subarray(start,end+1);},
    async copy(src,dst,etag){calls.push(['copy',src,dst,etag]);objects.set(dst,objects.get(src));},
    async delete(k){calls.push(['delete',k]);objects.delete(k);},
    async readUrl(k,ttl,download){calls.push(['read',k,ttl,download]);return 'https://test.r2.cloudflarestorage.com/'+k;}
  };
  const screenshots=[{id:'s'.repeat(24),originalName:'Test-Screenshot.png',createdAt:new Date().toISOString(),byteSize:200,expiresAt:new Date(Date.now()+604800000).toISOString()}];
  const database={enabled:true,clips:repo,async listScreenshotUploadsForAccount(id){return id===ACCOUNT?screenshots:[];}};
  const json=(res,code,data)=>{res.writeHead(code,{'content-type':'application/json','cache-control':'no-store'});res.end(JSON.stringify(data));};
  const deps={database,storage,env,authenticate(req){return req.headers.authorization==='Bearer test-owner'?{account:{id:ACCOUNT}}:req.headers.authorization==='Bearer test-other'?{account:{id:OTHER}}:null;},
    requireUserSession(req,res){const id=req.headers['x-betteruc-session']==='test-owner'?ACCOUNT:req.headers['x-betteruc-session']==='test-other'?OTHER:null;if(!id){json(res,401,{ok:false});return null;}return{id};},
    json,readJsonBody:async(req,max)=>{let parts=[],bytes=0;for await(const chunk of req){bytes+=chunk.length;if(bytes>max)throw Object.assign(new Error('Too big'),{statusCode:413});parts.push(chunk);}return JSON.parse(Buffer.concat(parts));},
    isRateLimited:()=>false,screenshotGalleryItem:s=>({...s,url:'/s/'+s.id}),publicBaseUrl:'https://betteruc.test',logger:{warn(){}}};
  let routes=createClipRoutes(deps);
  const server=require('node:http').createServer(async(req,res)=>{if(!await routes.handle(req,res,new URL(req.url,'http://localhost'))){res.writeHead(404);res.end();}});
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const base='http://127.0.0.1:'+server.address().port;
  const poster=(await sharp({create:{width:360,height:202,channels:3,background:'#203c50'}}).png().toBuffer()).toString('base64');
  async function request(route,method='GET',body,owner='test-owner',web=false){return fetch(base+route,{method,headers:{[web?'x-betteruc-session':'authorization']:web?owner:'Bearer '+owner,'content-type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),redirect:'manual'});}
  async function reserve(bytes=mp4()){const response=await request('/api/clips','POST',{originalName:'Testclip.mp4',byteSize:bytes.length,md5:crypto.createHash('md5').update(bytes).digest('base64'),poster});const result=await response.json();if(response.status===201)objects.set(key(result.id,'stage'),{body:bytes,type:'video/mp4'});return{response,result};}
  return {db,repo,objects,calls,storage,deps,base,server,request,reserve,poster,resetRoutes(){routes=createClipRoutes(deps);return routes;},get routes(){return routes;},
    async close(){server.closeAllConnections();await new Promise(r=>server.close(r));await db.close();}};
}
module.exports={fixture,mp4,ACCOUNT,OTHER};
