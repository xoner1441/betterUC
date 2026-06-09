package com.betteruc.client;

public final class TabBadgeRenderState {
    private static final ThreadLocal<Integer> PLAYER_LIST_RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);

    private TabBadgeRenderState() {
    }

    public static void beginPlayerListRender() {
        PLAYER_LIST_RENDER_DEPTH.set(PLAYER_LIST_RENDER_DEPTH.get() + 1);
    }

    public static void endPlayerListRender() {
        PLAYER_LIST_RENDER_DEPTH.set(Math.max(0, PLAYER_LIST_RENDER_DEPTH.get() - 1));
    }

    public static boolean isPlayerListRendering() {
        return PLAYER_LIST_RENDER_DEPTH.get() > 0;
    }
}
