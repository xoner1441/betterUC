package com.betteruc.gui;

import com.betteruc.client.BetterUCAuthClient;
import com.betteruc.client.ClientCompat;
import com.betteruc.client.clips.ClipUploadClient;
import com.betteruc.client.clips.ClipUploadTask;
import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;

/** Opening a chat action never uploads anything; only this explicit confirmation starts transfer. */
public final class ClipUploadScreen extends Screen {
    private final Screen parent;
    private final Path path;
    private ClipUploadTask task;
    private Button upload,close,copy,open;
    private String error="";
    public ClipUploadScreen(Screen parent,Path path,ClipUploadTask task) {
        super(Component.literal("Clip teilen")); this.parent=parent; this.path=path; this.task=task;
    }
    @Override protected void init() {
        int w=Math.min(480,width-24),x=(width-w)/2,y=height-32;
        int buttonW=(w-12)/3;
        close=addRenderableWidget(Button.builder(Component.literal("Zurück"),b->onClose()).bounds(x,y,buttonW,20).build());
        upload=addRenderableWidget(Button.builder(Component.literal("Jetzt hochladen"),b->{
            try { task=ClipUploadClient.start(path,ClipUploadTask.apiUri(BetterUCConfig.INSTANCE.pingRelayUrl),BetterUCAuthClient.credential()); }
            catch (RuntimeException e) { error="Ungültige Relay-Adresse; HTTPS erforderlich."; }
        }).bounds(x+2*(buttonW+6),y,buttonW,20).build());
        copy=addRenderableWidget(Button.builder(Component.literal("Link kopieren"),b->{
            if (task!=null && !task.resultUrl().isBlank()) minecraft.keyboardHandler.setClipboard(task.resultUrl());
        }).bounds(x+2*(buttonW+6),y,buttonW,20).build());
        open=addRenderableWidget(Button.builder(Component.literal("Galerie"),b->{
            URI api=ClipUploadTask.apiUri(BetterUCConfig.INSTANCE.pingRelayUrl);
            Util.getPlatform().openUri(api.resolve("/panel"));
        }).bounds(x+buttonW+6,y,buttonW,20).build());
        addRenderableWidget(Button.builder(Component.literal("Lokal ansehen"),b->{ if(path!=null) Util.getPlatform().openPath(path); })
                .bounds(x,Math.max(40,height-60),120,20).build()).active=path!=null;
        tick();
    }
    @Override public void tick() {
        if (upload==null) return;
        upload.visible=task==null || task.done() && task.resultUrl().isBlank(); upload.active=path!=null;
        copy.visible=task!=null && !task.resultUrl().isBlank();
        close.setMessage(Component.literal(task!=null && !task.done()?"Abbrechen":"Zurück"));
        open.active=task==null || task.done();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xED141A22);
        int w=Math.min(480,width-24),x=(width-w)/2,y=18;
        g.text(font,getTitle(),x,y,0xFF38BDF8); y+=22;
        String[] paragraphs={path!=null?path.getFileName().toString():task!=null?task.fileName():"Kein lokaler Clip gefunden. Bitte zuerst speichern.",
            "Enthaltene Stimmen und Ton werden mitgeteilt. Vorher ansehen; nur mit nötiger Zustimmung hochladen!",
            "Jeder mit dem Link kann den Clip sehen. Galerie privat. Ablauf: standardmäßig 7 Tage (siehe Galerie).",
            "Lokale Datei bleibt. Upload in der Galerie löschbar."};
        for (String paragraph:paragraphs) {
            for (var line:font.split(Component.literal(paragraph),w)) { g.text(font,line,x,y,0xFFCFD8E3); y+=10; }
            y+=4;
        }
        String status=task==null?error:task.status();
        if (task!=null && !task.done()) status+=" "+String.format(Locale.ROOT,"%.0f%%",task.progress()*100);
        for (var line:font.split(Component.literal(status),w)) { g.text(font,line,x,y,0xFFFBBF24); y+=10; }
        if (task!=null && !task.done()) {
            g.fill(x,y+4,x+w,y+7,0xFF334155); g.fill(x,y+4,x+(int)(w*task.progress()),y+7,0xFF38BDF8);
        }
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose() {
        if (task!=null && !task.done()) task.cancel();
        ClientCompat.setScreen(minecraft,parent);
    }
    @Override public boolean isPauseScreen() { return false; }
}
