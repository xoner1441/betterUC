package com.betteruc.client.clips;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** One explicit upload. Mod credentials go only to the configured HTTPS relay, never to R2. */
public final class ClipUploadTask implements Runnable {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Path path;
    private final URI api;
    private final String token;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicLong sent = new AtomicLong();
    private volatile CompletableFuture<?> request;
    private volatile String status = "Vorbereitung …", resultUrl = "";
    private volatile boolean done;
    private volatile long size;
    private String id;
    public ClipUploadTask(Path path, URI api, String token) { this.path=path; this.api=api; this.token=token; }
    public String status() { return status; }
    public String fileName() { return path.getFileName().toString(); }
    public String resultUrl() { return resultUrl; }
    public boolean done() { return done; }
    public double progress() { return size == 0 ? 0 : Math.min(1, (double) sent.get() / size); }
    public void cancel() { cancelled.set(true); var current=request; if (current!=null) current.cancel(true); }

    @Override public void run() {
        boolean completed = false;
        try {
            if (token == null || token.isBlank()) throw new IOException("betterUC ist noch nicht verbunden. Bitte erneut versuchen.");
            var prepared = ClipUploadFile.prepare(path, cancelled::get);
            size=prepared.size();
            JsonObject body = new JsonObject();
            body.addProperty("originalName", path.getFileName().toString()); body.addProperty("byteSize", size);
            body.addProperty("md5", prepared.md5()); body.addProperty("poster", prepared.poster());
            status="Upload wird angefragt …";
            var reservation=api("", "POST", body);
            id=reservation.get("id").getAsString();
            if (!id.matches("[A-Za-z0-9_-]{32}")) { id=null; throw new IOException("Ungültige Upload-Antwort."); }
            ClipUploadFile.checkCancelled(cancelled::get);
            var upload=reservation.getAsJsonObject("upload");
            URI target=validateR2(upload.get("url").getAsString());
            if (size != Files.size(path) || prepared.modified() != Files.getLastModifiedTime(path).toMillis()) throw new IOException("Clip wurde verändert. Bitte erneut auswählen.");
            var builder=HttpRequest.newBuilder(target).timeout(Duration.ofMinutes(55));
            Set<String> allowed=Set.of("content-type","content-md5","if-none-match");
            for (var entry: upload.getAsJsonObject("headers").entrySet()) {
                if (!allowed.contains(entry.getKey())) throw new IOException("Ungültige Upload-Header.");
                builder.header(entry.getKey(),entry.getValue().getAsString());
            }
            status="Clip wird hochgeladen …";
            var future=HTTP.sendAsync(builder.PUT(progressPublisher(HttpRequest.BodyPublishers.ofFile(path),sent)).build(),HttpResponse.BodyHandlers.discarding());
            request=future;
            if (cancelled.get()) future.cancel(true);
            int code=future.get().statusCode();
            if (code < 200 || code >= 300) throw new IOException("Cloud-Upload fehlgeschlagen (HTTP " + code + ").");
            ClipUploadFile.checkCancelled(cancelled::get);
            status="Clip wird geprüft und freigegeben …";
            var result=api("/"+id+"/complete", "POST", new JsonObject());
            URI share=api.resolve("/c/"+id); // Never trust a returned arbitrary URL.
            if (!result.get("id").getAsString().equals(id)) throw new IOException("Ungültige Upload-Antwort.");
            ClipUploadFile.checkCancelled(cancelled::get);
            resultUrl=share.toString(); status="Hochgeladen. Link kopieren oder Galerie öffnen."; completed=true;
        } catch (Exception | LinkageError error) {
            status=cancelled.get() ? "Upload abgebrochen. Die lokale Datei bleibt erhalten."
                    : error instanceof IOException ? error.getMessage() : "Upload fehlgeschlagen. Bitte Verbindung prüfen und erneut versuchen.";
            // Never log exceptions containing signed URLs or bearer credentials.
        } finally {
            if (!completed && id != null) {
                try {
                    HTTP.sendAsync(auth("/"+id).DELETE().build(),HttpResponse.BodyHandlers.discarding()).get();
                } catch (Exception ignored) { /* Server expiry/cleanup also covers offline cancellations. */ }
            }
            done=true;
        }
    }
    private HttpRequest.Builder auth(String suffix) {
        return HttpRequest.newBuilder(URI.create(api.toString()+suffix)).timeout(Duration.ofSeconds(60))
                .header("Authorization","Bearer "+token).header("Content-Type","application/json");
    }
    private JsonObject api(String suffix,String method,JsonObject body) throws Exception {
        ClipUploadFile.checkCancelled(cancelled::get);
        var future=HTTP.sendAsync(auth(suffix).method(method,HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());
        request=future;
        if (cancelled.get()) future.cancel(true);
        var response=future.get();
        if (response.statusCode()<200 || response.statusCode()>=300) {
            throw new IOException(switch(response.statusCode()) {
                case 401,403 -> "betterUC-Anmeldung ungültig. Bitte neu verbinden.";
                case 404,503 -> "Clip-Uploads sind auf dem Server noch nicht eingerichtet.";
                case 413 -> "Clip überschreitet das Upload-Limit.";
                case 429 -> "Upload-Limit erreicht. Bitte später erneut versuchen.";
                case 409 -> "Upload abgelaufen oder abgebrochen. Bitte erneut versuchen.";
                case 415 -> "Clipformat wird nicht unterstützt (H.264 / AAC bis 1080p).";
                default -> "Upload-Anfrage fehlgeschlagen (HTTP "+response.statusCode()+").";
            });
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    public static URI apiUri(String relay) {
        URI source=URI.create(relay.trim());
        if (!("https".equalsIgnoreCase(source.getScheme()) || "wss".equalsIgnoreCase(source.getScheme()))
                || source.getHost()==null || source.getRawUserInfo()!=null) throw new IllegalArgumentException("Clip-Uploads benötigen eine HTTPS-Verbindung.");
        return URI.create("https://"+source.getRawAuthority()+"/api/clips");
    }
    static URI validateR2(String value) {
        URI target=URI.create(value);
        if (!"https".equalsIgnoreCase(target.getScheme()) || target.getHost()==null
                || !target.getHost().endsWith(".r2.cloudflarestorage.com") || target.getRawUserInfo()!=null
                || target.getFragment()!=null || (target.getPort()!=-1 && target.getPort()!=443)) throw new IllegalArgumentException("Ungültiger Cloud-Speicher.");
        return target;
    }
    static HttpRequest.BodyPublisher progressPublisher(HttpRequest.BodyPublisher delegate, AtomicLong sent) {
        return new HttpRequest.BodyPublisher() {
            public long contentLength() { return delegate.contentLength(); }
            public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
                sent.set(0);
                delegate.subscribe(new Flow.Subscriber<>() {
                    public void onSubscribe(Flow.Subscription subscription) { subscriber.onSubscribe(subscription); }
                    public void onNext(ByteBuffer item) { sent.addAndGet(item.remaining()); subscriber.onNext(item); }
                    public void onError(Throwable error) { subscriber.onError(error); }
                    public void onComplete() { subscriber.onComplete(); }
                });
            }
        };
    }
}
