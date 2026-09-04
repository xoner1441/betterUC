"use strict";
const crypto = require('crypto');
const sharp = require('sharp');
const { inspectMp4 } = require('./clipMp4');

const ID = /^[a-zA-Z0-9_-]{32}$/;
const key = (id, kind) => kind === 'stage' ? `clips/staging/${id}.mp4`
  : kind === 'poster' ? `clips/posters/${id}.png` : `clips/ready/${id}.mp4`;
const fail = (statusCode,message) => Object.assign(new Error(message),{statusCode});
const limit = (env,name,fallback,min,max) => {
  const value=Number(env[name] ?? fallback);
  if (!Number.isSafeInteger(value) || value<min || value>max) throw new Error(`Invalid ${name}`);
  return value;
};
function mediaItem(clip) {
  return { id:clip.id,kind:'clip',originalName:clip.originalName,byteSize:clip.byteSize,
    durationSeconds:clip.durationSeconds,width:clip.width,height:clip.height,createdAt:clip.createdAt,
    expiresAt:clip.expiresAt,url:`/c/${clip.id}`,previewUrl:`/api/clips/${clip.id}/poster`,
    downloadUrl:`/api/clips/${clip.id}/download` };
}
function createClipRoutes({database,storage,authenticate,requireUserSession,json,readJsonBody,isRateLimited,
  screenshotGalleryItem,publicBaseUrl,env=process.env,now=Date.now,logger=console}) {
  const limits={ maxBytes:limit(env,'CLIP_MAX_BYTES',1073741824,1024,1073741824),
    dailyBytes:limit(env,'CLIP_DAILY_BYTES',3221225472,1024,107374182400),
    dailyCount:limit(env,'CLIP_DAILY_COUNT',5,1,100),
    totalBytes:limit(env,'CLIP_TOTAL_BYTES',107374182400,1024,10995116277760),
    ttlSeconds:limit(env,'CLIP_TTL_SECONDS',604800,3600,2592000) };
  const uploadSeconds=3600,repo=database.clips;
  let cleaning=false;
  const available=()=>{ if (!database.enabled || !repo || !storage) throw fail(503,'Clip-Uploads sind noch nicht eingerichtet. Screenshots bleiben verfügbar.'); };
  async function shared(id) {
    available();
    const clip=await repo.get(id);
    if (!clip || clip.state!=='ready' || Date.parse(clip.expiresAt)<=now()) throw fail(404,'Clip nicht mehr verfügbar oder Link ungültig.');
    return clip;
  }
  async function discard(clip) {
    // Revoke the application link before attempting physical cleanup; failures are retried.
    await Promise.all([storage.delete(key(clip.id,'ready')),storage.delete(key(clip.id,'poster'))]);
    // A presigned PUT can be reused. Do not remove its staging key while its grant is valid.
    if (Date.parse(clip.uploadExpiresAt)+300000<=now()) {
      await storage.delete(key(clip.id,'stage'));
      await repo.purged(clip.id);
    }
  }
  async function cleanup() {
    if (!repo || !storage || cleaning) return;
    cleaning=true;
    try {
      for (const clip of await repo.cleanupCandidates()) {
        try {
          if (clip.state==='ready' && Date.parse(clip.expiresAt)>now()) {
            await storage.delete(key(clip.id,'stage')); await repo.stagingCleaned(clip.id);
          } else await discard(clip);
        } catch { logger.warn('Clip cleanup will retry',clip.id); }
      }
    } finally { cleaning=false; }
  }
  async function handle(req,res,url) {
    const path=url.pathname;
    const owned=path.match(/^\/api\/user\/clips\/([a-zA-Z0-9_-]{32})$/);
    const action=path.match(/^\/api\/clips\/([a-zA-Z0-9_-]{32})(?:\/(complete|poster|download|play))?$/);
    if (path!=='/api/user/media' && path!=='/api/clips' && !owned && !action) return false;
    try {
      if (path==='/api/user/media' && req.method==='GET') {
        const account=requireUserSession(req,res,url); if (!account) return true;
        if (!database.enabled) throw fail(503,'Mediengalerie ist gerade nicht verfügbar.');
        const [screenshots,clips]=await Promise.all([database.listScreenshotUploadsForAccount(account.id,200),repo.list(account.id)]);
        const media=[...screenshots.map(s=>({...screenshotGalleryItem(s),kind:'screenshot',previewUrl:`/s/${s.id}`,downloadUrl:`/s/${s.id}?download=1`})),
          ...clips.map(mediaItem)].sort((a,b)=>Date.parse(b.createdAt)-Date.parse(a.createdAt)).slice(0,200);
        json(res,200,{ok:true,media,clipUploadsEnabled:Boolean(storage),limits}); return true;
      }
      if (owned && req.method==='DELETE') {
        const account=requireUserSession(req,res,url); if (!account) return true;
        available(); const clip=await repo.remove(owned[1],account.id);
        if (!clip) throw fail(404,'Clip nicht gefunden.');
        try { await discard(clip); } catch { logger.warn('Clip deletion queued',clip.id); }
        json(res,200,{ok:true}); return true;
      }
      if (req.method==='POST' || req.method==='DELETE') {
        const auth=authenticate(req,url);
        if (!auth?.account || auth.account.id==='legacy') throw fail(401,'betterUC-Anmeldung fehlt oder ist ungültig.');
        available();
        if (isRateLimited(req,'clip-actions',15,60000)) throw fail(429,'Zu viele Clip-Anfragen. Bitte kurz warten.');
        const accountId=auth.account.id;
        if (path==='/api/clips' && req.method==='POST') {
          const body=await readJsonBody(req,600000);
          if (!Number.isSafeInteger(body.byteSize) || body.byteSize<32 || body.byteSize>limits.maxBytes) throw fail(413,'Clip ist leer oder überschreitet das Upload-Limit.');
          if (typeof body.md5!=='string' || !/^[A-Za-z0-9+/]{22}==$/.test(body.md5)) throw fail(400,'Ungültige Datei-Prüfsumme.');
          if (typeof body.poster!=='string' || !/^[A-Za-z0-9+/]+={0,2}$/.test(body.poster) || body.poster.length>500000) throw fail(400,'Vorschaubild fehlt oder ist zu groß.');
          let poster;
          try {
            poster=await sharp(Buffer.from(body.poster,'base64'),{limitInputPixels:640*360}).resize(480,270,{fit:'inside',withoutEnlargement:true}).png().toBuffer();
          } catch { throw fail(400,'Ungültiges Vorschaubild.'); }
          const id=crypto.randomBytes(24).toString('base64url');
          const originalName=String(body.originalName || 'betterUC-clip.mp4').replace(/[\x00-\x1f\x7f/\\]/g,'_').slice(0,120);
          const clip=await repo.reserve({id,accountId,originalName,byteSize:body.byteSize,md5:body.md5,
            uploadExpiresAt:new Date(now()+uploadSeconds*1000).toISOString(),expiresAt:new Date(now()+limits.ttlSeconds*1000).toISOString()},limits);
          try {
            await storage.poster(key(id,'poster'),poster);
            const upload=await storage.uploadUrl(key(id,'stage'),clip,uploadSeconds);
            json(res,201,{ok:true,id,upload,expiresAt:clip.expiresAt,uploadExpiresAt:clip.uploadExpiresAt});
          } catch (error) { await repo.fail(id); throw error; }
          return true;
        }
        if (action && req.method==='DELETE' && !action[2]) {
          const clip=await repo.remove(action[1],accountId); if (!clip) throw fail(404,'Clip nicht gefunden.');
          try { await discard(clip); } catch { logger.warn('Clip cancellation queued',clip.id); }
          json(res,200,{ok:true}); return true;
        }
        if (action?.[2]==='complete' && req.method==='POST') {
          let clip=await repo.get(action[1]);
          if (!clip || clip.accountId!==accountId) throw fail(404,'Clip nicht gefunden.');
          if (clip.state==='ready' && Date.parse(clip.expiresAt)>now()) { json(res,200,{ok:true,...mediaItem(clip),url:`${publicBaseUrl}/c/${clip.id}`}); return true; }
          clip=await repo.claim(clip.id,accountId); if (!clip) throw fail(409,'Upload ist abgelaufen, wird geprüft oder wurde abgebrochen.');
          try {
            const head=await storage.head(key(clip.id,'stage'));
            if (head.ContentLength!==clip.byteSize || head.ContentType!=='video/mp4' || !head.ETag) throw fail(415,'Dateigröße oder Dateityp stimmt nicht überein.');
            const metadata=await inspectMp4((start,end)=>storage.range(key(clip.id,'stage'),start,end,head.ETag),clip.byteSize);
            await storage.copy(key(clip.id,'stage'),key(clip.id,'ready'),head.ETag);
            const ready=await repo.ready(clip.id,metadata);
            if (!ready) throw fail(409,'Upload wurde zwischenzeitlich abgebrochen.');
            json(res,200,{ok:true,...mediaItem(ready),url:`${publicBaseUrl}/c/${clip.id}`});
          } catch (error) {
            await repo.fail(clip.id);
            try { await discard(clip); } catch { logger.warn('Failed clip cleanup queued',clip.id); }
            throw error;
          }
          return true;
        }
      }
      if (action && req.method==='GET' && action[2]!=='complete') {
        const clip=await shared(action[1]);
        if (!action[2]) { json(res,200,{ok:true,...mediaItem(clip)}); return true; }
        const ttl=Math.max(1,Math.min(1800,Math.floor((Date.parse(clip.expiresAt)-now())/1000)));
        const target=await storage.readUrl(key(clip.id,action[2]==='poster'?'poster':'ready'),ttl,action[2]==='download');
        res.writeHead(302,{'location':target,'cache-control':'private, no-store','referrer-policy':'no-referrer','x-robots-tag':'noindex, nofollow'}); res.end(); return true;
      }
      throw fail(405,'Methode nicht erlaubt.');
    } catch (error) {
      // Never log signed URLs, credentials or SDK request objects.
      if (!error.statusCode) logger.warn('Clip service request failed',error.name || 'Error');
      json(res,error.statusCode || 502,{ok:false,error:error.statusCode?error.message:'Clip-Speicher ist gerade nicht erreichbar. Bitte später erneut versuchen.'});
      return true;
    }
  }
  return {handle,cleanup,limits};
}
module.exports={createClipRoutes,mediaItem,key};
