package com.betteruc.client;

import com.betteruc.ServerGate;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Handles the permitted Gärtner delivery shortcut and passive pot markers.
 * Flower and dead-bush collection deliberately remains fully manual.
 */
public final class AutoGaertnerClient {
    private static final Pattern FLOWER_TARGET_PATTERN = Pattern.compile("\\bpfluecke\\s+(\\d+)\\s+blumen\\s+an\\b");
    private static final long COMMAND_DELAY_MS = 250L;
    private static final long POT_INTERACTION_WINDOW_MS = 3_000L;
    private static final long MENU_SETTLE_MS = 350L;
    private static final int COMPLETED_POT_COLOR = 0xFF4ADE80;
    private static final VoxelShape COMPLETED_POT_SHAPE =
            Shapes.box(0.14D, -0.02D, 0.14D, 0.86D, 1.04D, 0.86D);

    private static boolean awaitingGardenerArrival;
    private static boolean potMarkerActive;
    private static long lastDropFlowersAtMs;
    private static int currentContainerId = -1;
    private static BlockPos pendingPotPos;
    private static long pendingPotAtMs;
    private static BlockPos activePotPos;
    private static long menuOpenedAtMs;
    private static boolean activePotCompleted;
    private static final Set<BlockPos> completedPotPositions = new HashSet<>();

    private AutoGaertnerClient() {
    }

    public static void initialize() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide()
                    && hand == InteractionHand.MAIN_HAND
                    && potMarkerActive
                    && AutomationController.isGaertnerEnabled()
                    && isFlowerPot(level.getBlockState(hitResult.getBlockPos()).getBlock())) {
                recordPotInteraction(hitResult.getBlockPos(), System.currentTimeMillis());
            }
            return InteractionResult.PASS;
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(AutoGaertnerClient::renderCompletedPots);
    }

    public static void handleChatLine(Minecraft client, String raw) {
        String clean = key(raw);

        if (potMarkerActive
                && clean.contains("payday")
                && clean.contains("du bekommst dein gehalt")
                && clean.contains("ausgezahlt")) {
            reset();
            return;
        }

        if (!clean.contains("gaertner")) return;

        Matcher targetMatcher = FLOWER_TARGET_PATTERN.matcher(clean);
        if (targetMatcher.find()) {
            awaitingGardenerArrival = false;
            stopPotMarkers();
            int flowers = parsePositiveInt(targetMatcher.group(1));
            if (client != null && client.player != null && flowers > 0) {
                client.player.sendSystemMessage(Component.literal(
                        "\u00A7e[betterUC] G\u00E4rtner: \u00A7f" + flowers + " Blumen manuell einsammeln."
                ));
            }
            return;
        }

        if (clean.contains("bring die blumen") && clean.contains("dropblumen")) {
            awaitingGardenerArrival = true;
            return;
        }

        if (awaitingGardenerArrival && clean.contains("du bist beim gaertner angekommen")) {
            awaitingGardenerArrival = false;
            sendDropFlowers(client);
            return;
        }

        if (clean.contains("gehe nun zum blumenstand")
                && clean.contains("entferne")
                && clean.contains("verwelkten buesche")) {
            startPotMarkers(client);
        }
    }

    /** Observes the menu state only; it never clicks a slot or moves an item. */
    public static void tick(Minecraft client) {
        if (!potMarkerActive) return;
        if (client == null || client.player == null || !ServerGate.isAllowedServer(client)) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        Screen screen = ClientCompat.currentScreen(client);
        if (!(screen instanceof MenuAccess<?> access)
                || !isFlowerStandMenu(screen)
                || !(access.getMenu() instanceof AbstractContainerMenu menu)) {
            captureTargetedPot(client, now);
            currentContainerId = -1;
            activePotPos = null;
            activePotCompleted = false;
            return;
        }

        if (menu.containerId != currentContainerId) {
            currentContainerId = menu.containerId;
            activePotPos = consumeRecentPotInteraction(now);
            if (activePotPos == null) activePotPos = targetedPotPosition(client);
            menuOpenedAtMs = now;
            activePotCompleted = false;
        }

        if (!activePotCompleted
                && activePotPos != null
                && now - menuOpenedAtMs >= MENU_SETTLE_MS
                && !containsDeadBush(client, menu)) {
            markPotCompleted(activePotPos);
            activePotCompleted = true;
        }
    }

    public static void reset() {
        awaitingGardenerArrival = false;
        lastDropFlowersAtMs = 0L;
        stopPotMarkers();
    }

    private static void startPotMarkers(Minecraft client) {
        potMarkerActive = true;
        clearPotProgress();
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00A7a[betterUC] G\u00E4rtner: \u00A7fErledigte T\u00F6pfe werden gr\u00FCn markiert; Einsammeln bleibt manuell."
            ));
        }
    }

    private static void stopPotMarkers() {
        potMarkerActive = false;
        clearPotProgress();
    }

    private static void sendDropFlowers(Minecraft client) {
        long now = System.currentTimeMillis();
        if (now - lastDropFlowersAtMs < 3_000L) return;
        lastDropFlowersAtMs = now;
        ClientScheduler.runDelayedOnClient(client, COMMAND_DELAY_MS, () -> {
            if (AutomationController.isGaertnerEnabled()) {
                ServerCommandUtil.send(client, "dropblumen", false);
            }
        });
    }

    private static boolean containsDeadBush(Minecraft client, AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) continue;
            if (client.player != null && slot.container == client.player.getInventory()) continue;
            if (isDeadBush(slot.getItem())) return true;
        }
        return false;
    }

    static void recordPotInteraction(BlockPos pos, long now) {
        pendingPotPos = pos == null ? null : pos.immutable();
        pendingPotAtMs = pendingPotPos == null ? 0L : now;
    }

    static BlockPos consumeRecentPotInteraction(long now) {
        BlockPos result = pendingPotPos != null && now - pendingPotAtMs <= POT_INTERACTION_WINDOW_MS
                ? pendingPotPos
                : null;
        pendingPotPos = null;
        pendingPotAtMs = 0L;
        return result;
    }

    static Set<BlockPos> completedPotPositions() {
        return Set.copyOf(completedPotPositions);
    }

    static void markPotCompleted(BlockPos pos) {
        if (pos != null) completedPotPositions.add(pos.immutable());
    }

    private static void captureTargetedPot(Minecraft client, long now) {
        BlockPos targetedPot = targetedPotPosition(client);
        if (targetedPot != null) recordPotInteraction(targetedPot, now);
    }

    private static BlockPos targetedPotPosition(Minecraft client) {
        if (client == null || client.level == null || client.hitResult == null) return null;
        HitResult hitResult = client.hitResult;
        if (hitResult.getType() == HitResult.Type.MISS) return null;

        if (hitResult instanceof BlockHitResult blockHit) {
            BlockPos directPos = blockHit.getBlockPos();
            if (isFlowerPot(client.level.getBlockState(directPos).getBlock())) {
                return directPos.immutable();
            }
        }

        Vec3 hitLocation = hitResult.getLocation();
        BlockPos center = BlockPos.containing(hitLocation);
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int x = center.getX() - 2; x <= center.getX() + 2; x++) {
            for (int y = center.getY() - 2; y <= center.getY() + 2; y++) {
                for (int z = center.getZ() - 2; z <= center.getZ() + 2; z++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (!isFlowerPot(client.level.getBlockState(candidate).getBlock())) continue;
                    double distance = hitLocation.distanceToSqr(x + 0.5D, y + 0.5D, z + 0.5D);
                    if (distance < nearestDistance) {
                        nearest = candidate;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest == null ? null : nearest.immutable();
    }

    private static void clearPotProgress() {
        currentContainerId = -1;
        pendingPotPos = null;
        pendingPotAtMs = 0L;
        activePotPos = null;
        menuOpenedAtMs = 0L;
        activePotCompleted = false;
        completedPotPositions.clear();
    }

    private static void renderCompletedPots(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
        if (!potMarkerActive || completedPotPositions.isEmpty()) return;
        if (context.poseStack() == null
                || context.levelState().cameraRenderState == null
                || context.levelState().cameraRenderState.pos == null) {
            return;
        }

        PoseStack poseStack = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        for (BlockPos pos : Set.copyOf(completedPotPositions)) {
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            context.submitNodeCollector().submitShapeOutline(
                    poseStack,
                    COMPLETED_POT_SHAPE,
                    RenderTypes.lines(),
                    COMPLETED_POT_COLOR,
                    5.0F,
                    true
            );
            poseStack.popPose();
        }
    }

    private static boolean isFlowerStandMenu(Screen screen) {
        String title = key(screen.getTitle().getString());
        return title.contains("blumenstand");
    }

    private static boolean isFlowerPot(net.minecraft.world.level.block.Block block) {
        return block instanceof FlowerPotBlock;
    }

    private static boolean isDeadBush(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null
                && "minecraft".equals(itemId.getNamespace())
                && "dead_bush".equals(itemId.getPath());
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String key(String value) {
        return value == null ? "" : value
                .replaceAll("\u00A7.", "")
                .toLowerCase(Locale.ROOT)
                .replace("\u00E4", "ae")
                .replace("\u00F6", "oe")
                .replace("\u00FC", "ue")
                .replace("\u00DF", "ss")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
