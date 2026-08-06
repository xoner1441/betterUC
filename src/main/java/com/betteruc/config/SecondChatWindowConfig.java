package com.betteruc.config;

import java.util.UUID;

public final class SecondChatWindowConfig {

    public String id = UUID.randomUUID().toString();
    public int x = 8;
    public int y = 62;
    public int width = 330;
    public int height = 120;
    public boolean locked = true;
    public String activeTabId = "";

    public void sanitize(int index) {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        x = Math.max(0, x);
        y = Math.max(0, y);
        width = Math.max(180, Math.min(600, width));
        height = Math.max(60, Math.min(320, height));
        if (activeTabId == null) {
            activeTabId = "";
        }
    }
}
