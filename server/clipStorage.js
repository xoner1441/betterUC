"use strict";
const { S3Client, PutObjectCommand, HeadObjectCommand, GetObjectCommand, CopyObjectCommand, DeleteObjectCommand } = require('@aws-sdk/client-s3');
const { getSignedUrl } = require('@aws-sdk/s3-request-presigner');

function createClipStorage(env = process.env) {
  const values = [env.CLIP_R2_ACCOUNT_ID,env.CLIP_R2_BUCKET,env.CLIP_R2_ACCESS_KEY_ID,env.CLIP_R2_SECRET_ACCESS_KEY];
  if (!values.every(v => typeof v === 'string' && v.trim())) return null;
  if (!/^[a-f0-9]{32}$/i.test(env.CLIP_R2_ACCOUNT_ID) || !/^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$/.test(env.CLIP_R2_BUCKET)) {
    throw new Error('Invalid clip R2 account or bucket configuration');
  }
  const jurisdiction = String(env.CLIP_R2_JURISDICTION || '').trim().toLowerCase();
  if (!['', 'eu'].includes(jurisdiction)) throw new Error('Invalid CLIP_R2_JURISDICTION; use eu or leave empty');
  // EU buckets are only reachable through the EU endpoint. Never fall back to the global endpoint.
  const endpoint = `https://${env.CLIP_R2_ACCOUNT_ID}${jurisdiction ? `.${jurisdiction}` : ''}.r2.cloudflarestorage.com`;
  const client = new S3Client({ region: 'auto', endpoint,
    credentials: { accessKeyId: env.CLIP_R2_ACCESS_KEY_ID, secretAccessKey: env.CLIP_R2_SECRET_ACCESS_KEY },
    requestChecksumCalculation: 'WHEN_REQUIRED', responseChecksumValidation: 'WHEN_REQUIRED' });
  const bucket = env.CLIP_R2_BUCKET;
  const input = key => ({ Bucket: bucket, Key: key });
  return {
    async uploadUrl(key, entry, expiresIn) {
      const headers = { 'content-type': 'video/mp4', 'content-md5': entry.md5, 'if-none-match': '*' };
      const url = await getSignedUrl(client, new PutObjectCommand({ ...input(key), ContentType: 'video/mp4',
        ContentLength: entry.byteSize, ContentMD5: entry.md5, IfNoneMatch: '*' }),
        { expiresIn, signableHeaders: new Set(['content-length','content-type','content-md5','if-none-match']) });
      return { url, headers };
    },
    head(key) { return client.send(new HeadObjectCommand(input(key))); },
    async range(key, start, end, etag) {
      const result = await client.send(new GetObjectCommand({ ...input(key), Range: `bytes=${start}-${end}`, IfMatch: etag }));
      return Buffer.from(await result.Body.transformToByteArray());
    },
    copy(source, target, etag) { return client.send(new CopyObjectCommand({ ...input(target),
      CopySource: `/${bucket}/${source}`, CopySourceIfMatch: etag, MetadataDirective: 'REPLACE',
      ContentType: 'video/mp4', CacheControl: 'private, no-store' })); },
    poster(key, body) { return client.send(new PutObjectCommand({ ...input(key), Body: body,
      ContentType: 'image/png', CacheControl: 'private, no-store' })); },
    delete(key) { return client.send(new DeleteObjectCommand(input(key))); },
    readUrl(key, expiresIn, download = false) { return getSignedUrl(client, new GetObjectCommand({ ...input(key),
      ResponseContentType: key.endsWith('.png') ? 'image/png' : 'video/mp4',
      ResponseCacheControl: 'private, no-store',
      ResponseContentDisposition: download ? 'attachment; filename="betterUC-clip.mp4"' : 'inline'
    }), { expiresIn }); }
  };
}
module.exports = { createClipStorage };
