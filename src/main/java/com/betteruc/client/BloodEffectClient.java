package com.betteruc.client;

import com.betteruc.ServerGate;
import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Locale;

/** Renders bounded client-side damage effects and suppresses matching legacy fragments on UnicaCity. */
public final class BloodEffectClient {
    private static final BloodEffectBurstLimiter LIMITER = new BloodEffectBurstLimiter();
    private static final ArrayDeque<DamageMark> RECENT_DAMAGE = new ArrayDeque<>();
    private static final long DAMAGE_MARK_LIFETIME_TICKS = 4;
    private static final double DAMAGE_PARTICLE_RADIUS_SQUARED = 4.0D;
    private static ClientLevel limitedLevel;

    public enum Mode {
        OFF("Aus"), SUBTLE("Dezent"), REDUX("Redux"), VOLUMETRIC("3D");

        private final String label;
        Mode(String label) { this.label = label; }
        public String label() { return label; }
    }

    private BloodEffectClient() {}

    public static void initialize() {
        ParticleProviderRegistry.getInstance().register(BloodEffectParticles.BURST,
                sprites -> (options, level, x, y, z, scale, spin, unused, random) ->
                        new BurstParticle(level, x, y, z, scale, spin, sprites));
        ParticleProviderRegistry.getInstance().register(BloodEffectParticles.DROP,
                sprites -> (options, level, x, y, z, dx, dy, dz, random) ->
                        new DropParticle(level, x, y, z, dx, dy, dz, sprites));
        ParticleProviderRegistry.getInstance().register(BloodEffectParticles.STREAK,
                sprites -> (options, level, x, y, z, dx, dy, dz, random) ->
                        new StreakParticle(level, x, y, z, dx, dy, dz, sprites));
    }

    public static Mode mode() {
        String raw = BetterUCConfig.INSTANCE.bloodEffectMode;
        if (raw == null) return Mode.SUBTLE;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "off" -> Mode.OFF;
            case "redux" -> Mode.REDUX;
            case "3d", "volumetric" -> Mode.VOLUMETRIC;
            default -> Mode.SUBTLE;
        };
    }

    public static String modeLabel() { return mode().label(); }

    public static void cycleMode() {
        BetterUCConfig.INSTANCE.bloodEffectMode = switch (mode()) {
            case OFF -> "subtle";
            case SUBTLE -> "redux";
            case REDUX -> "volumetric";
            case VOLUMETRIC -> "off";
        };
    }

    public static boolean replace(ParticleOptions option, double x, double y, double z) {
        Mode mode = mode();
        Minecraft client = Minecraft.getInstance();
        if (mode == Mode.OFF || !ServerGate.isAllowedServer(client) || client.level == null) return false;

        ClientLevel level = client.level;
        ensureLevel(level);
        boolean fragment = option instanceof BlockParticleOption || option instanceof ItemParticleOption;
        return fragment && isNearRecentDamage(level, x, y, z);
    }

    /** Uses the real player-damage packet as the reliable trigger, independent of server particle encoding. */
    public static void onPlayerDamage(ClientboundDamageEventPacket packet) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (mode() == Mode.OFF || level == null || !ServerGate.isAllowedServer(client)) return;

        Entity entity = level.getEntity(packet.entityId());
        if (!(entity instanceof Player) || entity == client.player) return;

        ensureLevel(level);
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.58D;
        double z = entity.getZ();
        long tick = level.getGameTime();
        rememberDamage(tick, x, y, z);
        if (LIMITER.accept(tick, x, y, z)) {
            spawn(level, mode(), x, y, z, damageDirection(client, level, entity, packet));
        }
    }

    private static Vec3 damageDirection(Minecraft client, ClientLevel level, Entity target,
                                        ClientboundDamageEventPacket packet) {
        Vec3 source = packet.sourcePosition().orElse(null);
        if (source == null && packet.sourceDirectId() != 0) {
            Entity direct = level.getEntity(packet.sourceDirectId());
            if (direct != null) source = direct.position();
        }
        if (source == null && packet.sourceCauseId() != 0) {
            Entity cause = level.getEntity(packet.sourceCauseId());
            if (cause != null) source = cause.position();
        }
        if (source == null && client.player != null) source = client.player.position();
        if (source == null) return new Vec3(0, 0.15D, 0);

        Vec3 raw = target.position().subtract(source);
        double horizontalLength = Math.sqrt(raw.x * raw.x + raw.z * raw.z);
        if (horizontalLength < 0.001D) return new Vec3(0, 0.15D, 0);
        return new Vec3(raw.x / horizontalLength, 0.12D, raw.z / horizontalLength);
    }

    private static void ensureLevel(ClientLevel level) {
        if (limitedLevel == level) return;
        limitedLevel = level;
        LIMITER.reset();
        RECENT_DAMAGE.clear();
    }

    private static void rememberDamage(long tick, double x, double y, double z) {
        discardExpiredDamage(tick);
        RECENT_DAMAGE.addLast(new DamageMark(tick, x, y, z));
        while (RECENT_DAMAGE.size() > 8) RECENT_DAMAGE.removeFirst();
    }

    private static boolean isNearRecentDamage(ClientLevel level, double x, double y, double z) {
        long tick = level.getGameTime();
        discardExpiredDamage(tick);
        for (DamageMark mark : RECENT_DAMAGE) {
            double dx = x - mark.x;
            double dy = y - mark.y;
            double dz = z - mark.z;
            if (dx * dx + dy * dy + dz * dz <= DAMAGE_PARTICLE_RADIUS_SQUARED) return true;
        }
        return false;
    }

    private static void discardExpiredDamage(long tick) {
        while (!RECENT_DAMAGE.isEmpty() && tick - RECENT_DAMAGE.peekFirst().tick > DAMAGE_MARK_LIFETIME_TICKS) {
            RECENT_DAMAGE.removeFirst();
        }
    }

    private record DamageMark(long tick, double x, double y, double z) {}

    /** Shows the selected style in front of the player without requiring a server hit. */
    public static boolean preview() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return false;
        Vec3 position = client.player.getEyePosition().add(client.player.getLookAngle().scale(2.2D));
        Mode previewMode = mode() == Mode.OFF ? Mode.SUBTLE : mode();
        spawn(client.level, previewMode, position.x, position.y, position.z, client.player.getLookAngle());
        return true;
    }

    private static void spawn(ClientLevel level, Mode mode, double x, double y, double z, Vec3 rawDirection) {
        boolean redux = mode == Mode.REDUX;
        boolean volumetric = mode == Mode.VOLUMETRIC;
        var random = level.getRandom();
        Vec3 direction = normalizeDirection(rawDirection);
        double burstScale = volumetric ? 0.92D : redux ? 1.15D : 0.72D;
        double spin = (random.nextDouble() - 0.5D) * (volumetric ? 0.28D : redux ? 0.20D : 0.12D);
        level.addParticle(BloodEffectParticles.BURST, x, y, z, burstScale, spin, 0);

        if (volumetric) {
            Vec3 side = horizontalPerpendicular(direction);
            level.addParticle(BloodEffectParticles.BURST,
                    x + direction.x * 0.16D + side.x * 0.13D,
                    y + 0.10D,
                    z + direction.z * 0.16D + side.z * 0.13D,
                    0.54D, -spin * 1.35D, 0);
            level.addParticle(BloodEffectParticles.BURST,
                    x + direction.x * 0.30D - side.x * 0.11D,
                    y - 0.07D,
                    z + direction.z * 0.30D - side.z * 0.11D,
                    0.38D, spin * 1.65D, 0);
        }

        int drops = volumetric ? 12 : redux ? 9 : 4;
        double speed = volumetric ? 0.25D : redux ? 0.22D : 0.13D;
        double directionalPush = volumetric ? 0.19D : redux ? 0.08D : 0.035D;
        for (int i = 0; i < drops; i++) {
            double dx = random.triangle(0, speed) + direction.x * directionalPush;
            double dy = random.nextDouble() * speed + 0.035D;
            double dz = random.triangle(0, speed) + direction.z * directionalPush;
            level.addParticle(BloodEffectParticles.DROP,
                    x + random.triangle(0, 0.08D),
                    y + random.triangle(0, 0.08D),
                    z + random.triangle(0, 0.08D), dx, dy, dz);
        }

        int streaks = volumetric ? 9 : redux ? 2 : 0;
        for (int i = 0; i < streaks; i++) {
            double spread = volumetric ? 0.10D : 0.06D;
            double push = volumetric ? 0.19D : 0.14D;
            double dx = direction.x * push + random.triangle(0, spread);
            double dy = 0.025D + random.nextDouble() * (volumetric ? 0.10D : 0.07D);
            double dz = direction.z * push + random.triangle(0, spread);
            level.addParticle(BloodEffectParticles.STREAK,
                    x + direction.x * 0.08D + random.triangle(0, 0.13D),
                    y + random.triangle(0, 0.13D),
                    z + direction.z * 0.08D + random.triangle(0, 0.13D), dx, dy, dz);
        }
    }

    private static Vec3 normalizeDirection(Vec3 raw) {
        if (raw == null) return new Vec3(0, 0.12D, 0);
        double length = Math.sqrt(raw.x * raw.x + raw.y * raw.y + raw.z * raw.z);
        return length < 0.001D ? new Vec3(0, 0.12D, 0) : raw.scale(1D / length);
    }

    private static Vec3 horizontalPerpendicular(Vec3 direction) {
        double length = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        return length < 0.001D
                ? new Vec3(1, 0, 0)
                : new Vec3(-direction.z / length, 0, direction.x / length);
    }

    private static final class BurstParticle extends SingleQuadParticle {
        private final float baseSize;
        private final float spin;

        private BurstParticle(ClientLevel level, double x, double y, double z,
                              double scale, double spin, SpriteSet sprites) {
            super(level, x, y, z, sprites.first());
            this.baseSize = (float) scale;
            this.spin = (float) spin;
            lifetime = scale > 0.9D ? 13 : 9;
            hasPhysics = false;
            gravity = 0;
            friction = 1;
            roll = random.nextFloat() * Mth.TWO_PI;
            oRoll = roll;
            setColor(1, 1, 1);
            setAlpha(0);
        }

        @Override public void tick() {
            super.tick();
            oRoll = roll;
            roll += spin;
            float progress = Math.min(1, age / (float) lifetime);
            setAlpha(progress < 0.12F ? progress / 0.12F : 1F - Mth.clamp((progress - 0.58F) / 0.42F, 0, 1));
        }

        @Override public float getQuadSize(float partialTick) {
            float progress = Mth.clamp((age + partialTick) / lifetime, 0, 1);
            float appear = Mth.clamp(progress * 5F, 0, 1);
            return baseSize * appear * (1F - progress * 0.18F);
        }

        @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    }

    private static final class DropParticle extends SingleQuadParticle {
        private final float baseSize;
        private final float spin;

        private DropParticle(ClientLevel level, double x, double y, double z,
                             double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, sprites.first());
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            baseSize = 0.055F + random.nextFloat() * 0.075F;
            spin = (random.nextFloat() - 0.5F) * 0.45F;
            lifetime = 11 + random.nextInt(10);
            gravity = 0.55F;
            friction = 0.88F;
            hasPhysics = true;
            roll = random.nextFloat() * Mth.TWO_PI;
            oRoll = roll;
            setColor(0.88F + random.nextFloat() * 0.12F, 0.78F, 0.78F);
        }

        @Override public void tick() {
            super.tick();
            oRoll = roll;
            roll += spin;
            float progress = Math.min(1, age / (float) lifetime);
            setAlpha(1F - Mth.clamp((progress - 0.62F) / 0.38F, 0, 1));
        }

        @Override public float getQuadSize(float partialTick) {
            return baseSize * (1F - 0.25F * Mth.clamp((age + partialTick) / lifetime, 0, 1));
        }

        @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    }

    private static final class StreakParticle extends SingleQuadParticle {
        private final float baseSize;
        private final float spin;

        private StreakParticle(ClientLevel level, double x, double y, double z,
                               double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, sprites.first());
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            baseSize = 0.40F + random.nextFloat() * 0.20F;
            spin = (random.nextFloat() - 0.5F) * 0.12F;
            lifetime = 14 + random.nextInt(6);
            gravity = 0.10F;
            friction = 0.94F;
            hasPhysics = false;
            roll = random.nextFloat() * Mth.TWO_PI;
            oRoll = roll;
            setColor(1F, 1F, 1F);
            setAlpha(1F);
        }

        @Override public void tick() {
            super.tick();
            oRoll = roll;
            roll += spin;
            float progress = Math.min(1, age / (float) lifetime);
            setAlpha(1F - Mth.clamp((progress - 0.58F) / 0.42F, 0, 1));
        }

        @Override public float getQuadSize(float partialTick) {
            float progress = Mth.clamp((age + partialTick) / lifetime, 0, 1);
            float appear = Mth.clamp(progress * 7F, 0, 1);
            return baseSize * appear * (1F - progress * 0.24F);
        }

        @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    }
}
