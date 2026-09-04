"use strict";
function invalid() { throw Object.assign(new Error('Kein unterstützter H.264-MP4-Clip (maximal 300 Sekunden).'), { statusCode: 415 }); }
function atoms(buffer, start = 0, end = buffer.length) {
  const result = [];
  while (start < end) {
    if (end-start<8 || result.length>20000) invalid();
    let size=buffer.readUInt32BE(start), header=8;
    if (size===1) { if (end-start<16) invalid(); size=Number(buffer.readBigUInt64BE(start+8)); header=16; }
    if (!size) size=end-start;
    if (!Number.isSafeInteger(size) || size<header || start+size>end) invalid();
    result.push({ type: buffer.toString('ascii',start+4,start+8), start:start+header,end:start+size }); start+=size;
  }
  return result;
}
function child(buffer, atom, type) { return atoms(buffer,atom.start,atom.end).find(a=>a.type===type); }
function parseMoov(buffer) {
  const root={start:0,end:buffer.length}, mvhd=child(buffer,root,'mvhd');
  if (!mvhd || mvhd.end-mvhd.start<32) invalid();
  const p=mvhd.start, version=buffer[p];
  if (version!==0 && version!==1) invalid();
  const timescale=buffer.readUInt32BE(p+(version===1?20:12));
  const duration=version===1?Number(buffer.readBigUInt64BE(p+24)):buffer.readUInt32BE(p+16);
  const seconds=duration/timescale;
  if (!Number.isFinite(seconds) || seconds<=0 || seconds>302) invalid();
  let width=0,height=0,videos=0;
  for (const track of atoms(buffer).filter(a=>a.type==='trak')) {
    const mdia=child(buffer,track,'mdia'), tkhd=child(buffer,track,'tkhd');
    if (!mdia) invalid();
    const hdlr=child(buffer,mdia,'hdlr'), minf=child(buffer,mdia,'minf');
    if (!hdlr || hdlr.end-hdlr.start<12 || !minf) invalid();
    const type=buffer.toString('ascii',hdlr.start+8,hdlr.start+12);
    const stbl=child(buffer,minf,'stbl'), stsd=stbl && child(buffer,stbl,'stsd');
    if (!stsd || stsd.end-stsd.start<16) invalid();
    const entries=atoms(buffer,stsd.start+8,stsd.end);
    if (type==='vide') {
      if (!tkhd || tkhd.end-tkhd.start<84 || entries.length!==1 || entries[0].type!=='avc1') invalid();
      width=buffer.readUInt32BE(tkhd.end-8)/65536; height=buffer.readUInt32BE(tkhd.end-4)/65536; videos++;
    } else if (type!=='soun' || entries.length!==1 || entries[0].type!=='mp4a') invalid();
  }
  if (videos!==1 || width<1 || height<1 || width>1920 || height>1080) invalid();
  return { durationSeconds:seconds,width:Math.round(width),height:Math.round(height) };
}
async function inspectMp4(read, size) {
  let at=0, count=0, movie=null, media=false, ftyp=false;
  while (at<size) {
    if (++count>128 || size-at<8) invalid();
    const head=await read(at,Math.min(size-1,at+31));
    if (head.length<8) invalid();
    let length=head.readUInt32BE(0), offset=8;
    if (length===1) { if (head.length<16) invalid(); length=Number(head.readBigUInt64BE(8)); offset=16; }
    if (!length) length=size-at;
    if (!Number.isSafeInteger(length) || length<offset || at+length>size) invalid();
    const type=head.toString('ascii',4,8);
    if (at===0 && type!=='ftyp') invalid();
    if (type==='ftyp') ftyp=true;
    if (type==='mdat') media=length>offset;
    if (type==='moov') {
      if (movie || length>4*1024*1024) invalid();
      movie=await read(at+offset,at+length-1);
      if (movie.length!==length-offset) invalid();
    }
    at+=length;
  }
  if (!ftyp || !media || !movie) invalid();
  return parseMoov(movie);
}
module.exports = { inspectMp4, parseMoov };
