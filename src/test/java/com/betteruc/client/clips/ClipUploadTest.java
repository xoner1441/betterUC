package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ClipUploadTest {
    @Test void relayRequiresHttpsAndNeverUsesQueryCredentials() {
        assertEquals("https://betteruc.de/api/clips",ClipUploadTask.apiUri("wss://betteruc.de/relay?token=not-forwarded").toString());
        for(String url:List.of("ws://betteruc.de","http://betteruc.de","https://user:pass@betteruc.de","file:///clip.mp4"))
            assertThrows(IllegalArgumentException.class,()->ClipUploadTask.apiUri(url));
    }
    @Test void onlyDirectHttpsR2DestinationsAllowed() {
        assertNotNull(ClipUploadTask.validateR2("https://account.r2.cloudflarestorage.com/key?X-Amz-Signature=test"));
        for(String url:List.of("http://a.r2.cloudflarestorage.com/key","https://evil.test/key","https://a.r2.cloudflarestorage.com.evil.test/key",
                "https://a.r2.cloudflarestorage.com:123/key","https://secret@a.r2.cloudflarestorage.com/key","https://a.r2.cloudflarestorage.com/key#x"))
            assertThrows(IllegalArgumentException.class,()->ClipUploadTask.validateR2(url));
    }
    @Test void progressPublisherPreservesContentLengthBytesAndBackpressure() throws Exception {
        byte[] bytes=new byte[180_123]; for(int i=0;i<bytes.length;i++)bytes[i]=(byte)i;
        var progress=new AtomicLong();var output=new java.io.ByteArrayOutputStream();var done=new CompletableFuture<Void>();
        var publisher=ClipUploadTask.progressPublisher(HttpRequest.BodyPublishers.ofByteArray(bytes),progress);
        assertEquals(bytes.length,publisher.contentLength());
        publisher.subscribe(new Flow.Subscriber<>() {
            Flow.Subscription subscription;
            public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
            public void onNext(ByteBuffer item){byte[] part=new byte[item.remaining()];item.get(part);output.writeBytes(part);subscription.request(1);}
            public void onError(Throwable error){done.completeExceptionally(error);}
            public void onComplete(){done.complete(null);}
        });
        done.get(5,TimeUnit.SECONDS);assertArrayEquals(bytes,output.toByteArray());assertEquals(bytes.length,progress.get());
    }
    @Test void cancellationStopsPreparationBeforeNativeDecoder(@TempDir Path dir) throws Exception {
        Path path=dir.resolve("clip.mp4");Files.write(path,new byte[200]);
        assertThrows(InterruptedException.class,()->ClipUploadFile.prepare(path,()->true));
        assertEquals(200,Files.size(path));
    }
    @Test void absentLoginFailsWithoutTouchingFileOrNetwork(@TempDir Path dir) {
        var task=new ClipUploadTask(dir.resolve("missing.mp4"),ClipUploadTask.apiUri("https://betteruc.de"),"");
        task.run();assertTrue(task.done());assertTrue(task.status().contains("noch nicht verbunden"));assertEquals("",task.resultUrl());
    }
    @Test @EnabledIfSystemProperty(named="betteruc.clipHardwareTest",matches="true")
    void syntheticExportGetsBoundedPngAndCorrectTransferChecksum(@TempDir Path dir) throws Exception {
        Path path=dir.resolve("clip_äöü.mp4");var packets=new ArrayList<ClipPacket>();
        var settings=new ClipSettings(320,180,30,5,2_000_000);
        try(var encoder=HardwareClipEncoder.open(settings,s->{})) {
            var rgba=ByteBuffer.allocateDirect(320*180*4);
            for(int i=0;i<320*180;i++)rgba.putInt(0x2299DDFF);rgba.flip();
            for(int i=0;i<35;i++)encoder.encode(rgba,i,packets::add);
            encoder.flush(packets::add);HardwareClipEncoder.writeMp4(path,encoder.header(),packets);
        }
        var prepared=ClipUploadFile.prepare(path,()->false);
        assertEquals(Files.size(path),prepared.size());
        assertEquals(Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(Files.readAllBytes(path))),prepared.md5());
        byte[] png=Base64.getDecoder().decode(prepared.poster());assertTrue(prepared.poster().length()<500_000);
        var image=ImageIO.read(new ByteArrayInputStream(png));assertEquals(320,image.getWidth());assertEquals(180,image.getHeight());
        int pixel=image.getRGB(100,100);assertTrue((pixel&255)>100);assertTrue(((pixel>>8)&255)>70);
    }
}
