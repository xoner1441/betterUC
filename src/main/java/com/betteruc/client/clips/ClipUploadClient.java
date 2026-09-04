package com.betteruc.client.clips;

import com.betteruc.client.ClientCompat;
import com.betteruc.gui.ClipUploadScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClipUploadClient {
    private static final Map<String, Path> SAVED = new LinkedHashMap<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread=new Thread(r,"betteruc-clip-upload"); thread.setDaemon(true); return thread;
    });
    private static Path latest;
    private static ClipUploadTask task;
    private static String pendingOpen;
    private ClipUploadClient() {}
    public static void requestOpen(String id) { pendingOpen=id==null?"":id; }
    public static void tick() {
        if (pendingOpen==null) return;
        String id=pendingOpen; pendingOpen=null;
        Minecraft client=Minecraft.getInstance();
        // Commands can close the chat after returning. Open on the next client tick, not inside command dispatch.
        open(ClientCompat.currentScreen(client),id.isEmpty()?null:id);
    }
    public static void saved(Path path) {
        Minecraft client=Minecraft.getInstance();
        client.execute(() -> {
            latest=path;
            String id=UUID.randomUUID().toString(); SAVED.put(id,path);
            while (SAVED.size()>30) SAVED.remove(SAVED.keySet().iterator().next());
            if (client.player!=null) client.player.sendSystemMessage(Component.literal("§a[betterUC Clips] §fClip lokal gespeichert. ")
                    .append(Component.literal("§b[Hochladen …]").setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent.RunCommand("/buclip upload "+id)))));
        });
    }
    public static void open(Screen parent,String id) {
        if (task!=null && !task.done()) { ClientCompat.setScreen(Minecraft.getInstance(),new ClipUploadScreen(parent,null,task)); return; }
        if (id!=null) {
            Path path=SAVED.get(id);
            ClientCompat.setScreen(Minecraft.getInstance(),new ClipUploadScreen(parent,path,null));
            return;
        }
        // Also allow the last saved clip after a game restart. File discovery is local and bounded.
        Path current=latest;
        String directory=ClipCaptureClient.storageLocationLabel();
        WORKER.execute(() -> {
            Path found=current;
            try {
                if (found==null || !Files.isRegularFile(found)) {
                    long newest=Long.MIN_VALUE; int count=0;
                    try (var entries=Files.newDirectoryStream(Path.of(directory),"*.mp4")) {
                        for (Path entry:entries) {
                            if (++count>10000) break;
                            if (!Files.isRegularFile(entry) || !entry.getFileName().toString().startsWith("clip_")) continue;
                            long time=Files.getLastModifiedTime(entry).toMillis();
                            if (time>newest) { newest=time; found=entry; }
                        }
                    }
                }
            } catch (Exception ignored) { }
            Path selected=found;
            Minecraft client=Minecraft.getInstance();
            client.execute(() -> {
                if (ClientCompat.currentScreen(client)==parent) ClientCompat.setScreen(client,new ClipUploadScreen(parent,selected,null));
            });
        });
    }
    public static ClipUploadTask start(Path path,java.net.URI api,String token) {
        if (task!=null && !task.done()) return task;
        task=new ClipUploadTask(path,api,token); WORKER.execute(task); return task;
    }
    public static void shutdown() { if (task!=null) task.cancel(); WORKER.shutdown(); }
}
