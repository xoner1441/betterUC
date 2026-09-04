"use strict";

function map(row) {
  if (!row) return null;
  return { id: row.id, accountId: row.account_id, originalName: row.original_name,
    byteSize: Number(row.byte_size), md5: row.content_md5, state: row.state,
    durationSeconds: row.duration_seconds, width: row.width, height: row.height,
    createdAt: new Date(row.created_at).toISOString(), expiresAt: new Date(row.expires_at).toISOString(),
    uploadExpiresAt: new Date(row.upload_expires_at).toISOString(), deletedAt: row.deleted_at,
    stagingCleanedAt: row.staging_cleaned_at };
}
function createClipRepository(pool) {
  return {
    async reserve(entry, limits) {
      const client = await pool.connect();
      try {
        await client.query('begin');
        // Quotas must remain correct for simultaneous requests, even across server processes.
        await client.query("select pg_advisory_xact_lock(hashtext('betteruc:clip-quota'))");
        const daily = (await client.query(`select count(*)::int as count, coalesce(sum(byte_size),0)::text as bytes
          from clip_uploads where account_id=$1 and created_at > now()-interval '24 hours'`, [entry.accountId])).rows[0];
        const global = (await client.query(`select coalesce(sum(byte_size),0)::text as bytes
          from clip_uploads where purged_at is null`)).rows[0];
        if (daily.count >= limits.dailyCount || Number(daily.bytes) + entry.byteSize > limits.dailyBytes
            || Number(global.bytes) + entry.byteSize > limits.totalBytes) {
          throw Object.assign(new Error('Upload-Limit erreicht. Bitte später erneut versuchen.'), { statusCode: 429 });
        }
        const result = await client.query(`insert into clip_uploads
          (id,account_id,original_name,byte_size,content_md5,upload_expires_at,expires_at)
          values($1,$2,$3,$4,$5,$6,$7) returning *`,
          [entry.id,entry.accountId,entry.originalName,entry.byteSize,entry.md5,entry.uploadExpiresAt,entry.expiresAt]);
        await client.query('commit');
        return map(result.rows[0]);
      } catch (error) { await client.query('rollback'); throw error; }
      finally { client.release(); }
    },
    async get(id) { return map((await pool.query('select * from clip_uploads where id=$1', [id])).rows[0]); },
    async list(accountId) {
      return (await pool.query(`select * from clip_uploads where account_id=$1 and state='ready'
        and expires_at>now() order by created_at desc limit 200`, [accountId])).rows.map(map);
    },
    async claim(id, accountId) {
      return map((await pool.query(`update clip_uploads set state='finalizing' where id=$1 and account_id=$2
        and state='pending' and upload_expires_at>now() returning *`, [id,accountId])).rows[0]);
    },
    async ready(id, metadata) {
      return map((await pool.query(`update clip_uploads set state='ready',duration_seconds=$2,width=$3,height=$4
        where id=$1 and state='finalizing' and upload_expires_at>now() returning *`,
        [id,metadata.durationSeconds,metadata.width,metadata.height])).rows[0]);
    },
    async remove(id, accountId) {
      return map((await pool.query(`update clip_uploads set state='deleted',deleted_at=coalesce(deleted_at,now())
        where id=$1 and account_id=$2 returning *`, [id,accountId])).rows[0]);
    },
    async fail(id) { await pool.query(`update clip_uploads set state='deleted',purged_at=null,deleted_at=coalesce(deleted_at,now())
      where id=$1 and state <> 'ready'`, [id]); },
    async cleanupCandidates() {
      return (await pool.query(`select * from clip_uploads where purged_at is null and
        (state='deleted' or expires_at<=now() or (state<>'ready' and upload_expires_at<=now())
          or (staging_cleaned_at is null and upload_expires_at<now()-interval '5 minutes'))
        order by upload_expires_at limit 100`)).rows.map(map);
    },
    async stagingCleaned(id) { await pool.query('update clip_uploads set staging_cleaned_at=now() where id=$1', [id]); },
    async purged(id) { await pool.query(`update clip_uploads set state='deleted',purged_at=now(),
      deleted_at=coalesce(deleted_at,now()) where id=$1`, [id]); }
  };
}
module.exports = { createClipRepository };
