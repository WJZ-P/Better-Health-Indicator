package com.wjz.betterhealthindicator.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Minecraft-independent heart layout and animation state used by the old Forge renderers.
 *
 * <p>The version modules only translate Forge events and draw sprites. Keeping health layering,
 * right-to-left draining, hit scatter, blinking, low-health shake, falling hearts and death
 * shards here makes 1.7.10 through 1.16 behave consistently without a Kotlin runtime.</p>
 */
public final class LegacyHeartAnimation {
    public static final int HEARTS_PER_ROW = 10;
    public static final float HEART_SIZE = 9.0F;
    public static final float HEART_SPACING = 8.0F;
    public static final float LAYER_HEALTH = 20.0F;

    private static final long BLINK_DURATION_MS = 1_000L;
    private static final long BLINK_INTERVAL_MS = 150L;
    private static final long SCATTER_DURATION_MS = 320L;
    private static final int MAX_PARTICLES = 180;
    private static final long STALE_STATE_MS = 15_000L;
    private static final float TWO_PI = (float) (Math.PI * 2.0D);

    /** The same upper-layer palette as the current renderer. Layer zero remains vanilla red. */
    private static final int[] TIER_COLORS = {
            0xFF6B00, 0xFFA600, 0xFFD900, 0xBBE300, 0x5CE62E,
            0x00E68A, 0x00D5FF, 0x3385FF, 0x8F4DFF, 0xFF33B5
    };

    public enum Fill {
        NONE,
        HALF,
        FULL
    }

    public enum ParticleKind {
        HEART,
        SHARD
    }

    public static final class Slot {
        /** Local billboard X. Positive values appear on the left after the world matrix mirrors X. */
        public final float x;
        public final Fill top;
        public final int topLayer;
        /** -1 means the empty black container is visible below the top layer. */
        public final int baseLayer;
        public final int logicalIndex;

        private Slot(float x, Fill top, int topLayer, int baseLayer, int logicalIndex) {
            this.x = x;
            this.top = top;
            this.topLayer = topLayer;
            this.baseLayer = baseLayer;
            this.logicalIndex = logicalIndex;
        }
    }

    public static final class View {
        public final Slot[] slots;
        public final int multiplier;
        public final boolean blinking;
        public final boolean hardcoreContainer;

        private View(Slot[] slots, int multiplier, boolean blinking, boolean hardcoreContainer) {
            this.slots = slots;
            this.multiplier = multiplier;
            this.blinking = blinking;
            this.hardcoreContainer = hardcoreContainer;
        }
    }

    public static final class Offset {
        public final float x;
        public final float y;
        public final float rotation;

        private Offset(float x, float y, float rotation) {
            this.x = x;
            this.y = y;
            this.rotation = rotation;
        }
    }

    /** A billboard-local particle. Coordinates are measured in the same 9-pixel heart space. */
    public static final class Particle {
        public final int entityId;
        public final ParticleKind kind;
        public final Fill fill;
        public final int layer;
        /** Container atlas quadrant for a shard: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
        public final int shardQuadrant;
        public final int maxAge;
        public final int delay;

        private float previousX;
        private float previousY;
        private float x;
        private float y;
        private float velocityX;
        private float velocityY;
        private float previousRotation;
        private float rotation;
        private final float spin;
        private int age;

        private Particle(
                int entityId,
                ParticleKind kind,
                Fill fill,
                int layer,
                int shardQuadrant,
                float x,
                float y,
                float velocityX,
                float velocityY,
                float rotation,
                float spin,
                int maxAge,
                int delay
        ) {
            this.entityId = entityId;
            this.kind = kind;
            this.fill = fill;
            this.layer = layer;
            this.shardQuadrant = shardQuadrant;
            this.x = this.previousX = x;
            this.y = this.previousY = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.rotation = this.previousRotation = rotation;
            this.spin = spin;
            this.maxAge = maxAge;
            this.delay = delay;
            this.age = -delay;
        }

        public boolean isVisible() {
            return age >= 0;
        }

        public float renderX(float partialTick) {
            return previousX + (x - previousX) * partialTick;
        }

        public float renderY(float partialTick) {
            return previousY + (y - previousY) * partialTick;
        }

        public float renderRotation(float partialTick) {
            return previousRotation + (rotation - previousRotation) * partialTick;
        }

        public float alpha(float partialTick) {
            if (age < 0) return 0.0F;
            float renderAge = age + partialTick;
            float fadeStart = maxAge - 8.0F;
            if (renderAge <= fadeStart) return 1.0F;
            return clamp((maxAge - renderAge) / 8.0F, 0.0F, 1.0F);
        }

        private boolean tick() {
            previousX = x;
            previousY = y;
            previousRotation = rotation;
            age++;
            if (age < 0) return false;

            x += velocityX;
            y += velocityY;
            rotation += spin;
            velocityX *= 0.94F;
            velocityY += kind == ParticleKind.SHARD ? 0.12F : 0.09F;
            velocityY *= 0.98F;
            return age >= maxAge;
        }
    }

    private static final class State {
        float lastHealth;
        float maxHealth;
        long blinkUntilMs;
        long scatterStartedMs;
        float scatterAmplitude;
        float scatterSwing;
        int scatterSeed;
        boolean deathShardsSpawned;
        long lastSeenMs;

        State(float health, float maximum, long now, int entityId) {
            lastHealth = health;
            maxHealth = maximum;
            lastSeenMs = now;
            scatterSeed = mix(entityId * 0x9E3779B9);
        }
    }

    private static final Map<Integer, State> STATES = new HashMap<Integer, State>();
    private static final List<Particle> PARTICLES = new ArrayList<Particle>();
    private static final Random RANDOM = new Random();
    private static long lastCleanupMs;

    private LegacyHeartAnimation() {
    }

    /** Samples health and starts all animations caused by a health transition. */
    public static View observe(int entityId, float rawHealth, float rawMaximum) {
        float maximum = Math.max(1.0F, rawMaximum);
        float health = clamp(rawHealth, 0.0F, maximum);
        long now = System.currentTimeMillis();
        State state = STATES.get(entityId);
        if (state == null) {
            state = new State(health, maximum, now, entityId);
            STATES.put(entityId, state);
        } else {
            state.lastSeenMs = now;
            state.maxHealth = maximum;
            if (Math.abs(health - state.lastHealth) > 0.001F) {
                state.blinkUntilMs = now + BLINK_DURATION_MS;
                if (health < state.lastHealth) {
                    float damage = state.lastHealth - health;
                    state.scatterStartedMs = now;
                    state.scatterAmplitude = damage >= 10.0F ? 4.0F : damage >= 7.0F ? 2.0F : 1.0F;
                    state.scatterSwing = 0.20F + state.scatterAmplitude * 0.07F;
                    state.scatterSeed = mix(state.scatterSeed ^ Float.floatToIntBits(health) ^ (int) now);
                    spawnLostHearts(entityId, health, state.lastHealth, maximum, state.scatterAmplitude);
                    if (health <= 0.0F && !state.deathShardsSpawned) {
                        spawnDeathShards(entityId, maximum);
                        state.deathShardsSpawned = true;
                    }
                } else {
                    state.deathShardsSpawned = false;
                }
                state.lastHealth = health;
            }
        }
        cleanupStates(now);
        return computeView(health, maximum, isBlinking(state, now));
    }

    /** Advances falling hearts and container fragments once per client tick. */
    public static void tick() {
        Iterator<Particle> iterator = PARTICLES.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().tick()) iterator.remove();
        }
    }

    /** Immutable snapshot list is unnecessary on the single render thread; callers must not mutate it. */
    public static List<Particle> particlesFor(int entityId) {
        if (PARTICLES.isEmpty()) return Collections.emptyList();
        List<Particle> result = new ArrayList<Particle>();
        for (Particle particle : PARTICLES) {
            if (particle.entityId == entityId && particle.isVisible()) result.add(particle);
        }
        return result;
    }

    /** Combines the short hit scatter with the independent low-health heartbeat shake. */
    public static Offset offset(int entityId, int heartIndex, float health, float maximum) {
        float ox = 0.0F;
        float oy = 0.0F;
        float rotation = 0.0F;
        long now = System.currentTimeMillis();
        State state = STATES.get(entityId);
        if (state != null && state.scatterStartedMs > 0L) {
            long elapsed = now - state.scatterStartedMs;
            if (elapsed >= 0L && elapsed < SCATTER_DURATION_MS) {
                float t = elapsed / (float) SCATTER_DURATION_MS;
                float envelope = 1.0F - t;
                float amplitude = state.scatterAmplitude * envelope;
                ox += amplitude * sin(TWO_PI * 0.5F * t + phase(state.scatterSeed, heartIndex, 0));
                oy += amplitude * sin(TWO_PI * 1.0F * t + phase(state.scatterSeed, heartIndex, 1));
                rotation += state.scatterSwing * envelope
                        * sin(TWO_PI * 2.0F * t + phase(state.scatterSeed, heartIndex, 2));
            }
        }

        if (health > 0.0F && maximum > 0.0F && health / maximum <= 0.20F) {
            // Per-heart phases deliberately differ so the row never moves as a rigid block.
            oy += (float) Math.sin(now * 0.045D + heartIndex * 1.731D) * 1.2F;
            rotation += (float) Math.sin(now * 0.031D + heartIndex * 0.913D) * 0.055F;
        }
        return new Offset(ox, oy, rotation);
    }

    public static int colorForLayer(int layer) {
        if (layer <= 0) return 0xFFFFFF;
        return TIER_COLORS[(layer - 1) % TIER_COLORS.length];
    }

    public static int multiplierColor(int multiplier) {
        if (multiplier >= 20) return 0xAA55FF;
        if (multiplier >= 15) return 0xFFD700;
        if (multiplier >= 10) return 0x55AAFF;
        if (multiplier >= 5) return 0x55FF55;
        return 0xFFFFFF;
    }

    private static View computeView(float health, float maximum, boolean blinking) {
        boolean tiered = maximum > LAYER_HEALTH;
        int count = tiered ? HEARTS_PER_ROW : clamp((int) Math.ceil(maximum / 2.0F), 1, HEARTS_PER_ROW);
        Slot[] slots = new Slot[count];
        int multiplier = 0;
        int topLayer = 0;
        int baseLayer = -1;
        float healthInLayer = health;
        boolean hardcore = false;

        if (tiered) {
            topLayer = Math.max(0, (int) Math.ceil(health / LAYER_HEALTH) - 1);
            baseLayer = topLayer > 0 ? topLayer - 1 : -1;
            healthInLayer = health - topLayer * LAYER_HEALTH;
            int activeLayers = topLayer + 1;
            multiplier = activeLayers >= 2 ? activeLayers : 0;
            int totalLayers = (int) Math.ceil(maximum / LAYER_HEALTH);
            hardcore = totalLayers >= 5 && (topLayer == 0 || baseLayer == 0);
        }

        float hpPerHeart = tiered ? 2.0F : maximum / count;
        for (int logical = 0; logical < count; logical++) {
            float remainder = healthInLayer - logical * hpPerHeart;
            Fill fill = fillFor(remainder, hpPerHeart);
            // drainFromRight=true: logical zero is screen-left and is emptied last.
            float localX = ((count - 1) * 0.5F - logical) * HEART_SPACING;
            slots[logical] = new Slot(localX, fill, topLayer, baseLayer, logical);
        }
        return new View(slots, multiplier, blinking, hardcore);
    }

    private static Fill fillFor(float remainder, float hpPerHeart) {
        if (remainder >= hpPerHeart * 0.75F) return Fill.FULL;
        if (remainder >= hpPerHeart * 0.25F) return Fill.HALF;
        return Fill.NONE;
    }

    private static boolean isBlinking(State state, long now) {
        return now < state.blinkUntilMs && ((state.blinkUntilMs - now) / BLINK_INTERVAL_MS) % 2L == 1L;
    }

    private static void spawnLostHearts(int entityId, float health, float previousHealth, float maximum, float severity) {
        float cursor = previousHealth;
        int spawned = 0;
        while (cursor - health > 0.001F && spawned < 20 && PARTICLES.size() < MAX_PARTICLES) {
            float amount = Math.min(2.0F, cursor - health);
            float sampleHealth = Math.max(0.0F, cursor - 0.01F);
            HeartRef ref = heartRefAt(sampleHealth, maximum);
            Fill fill = amount >= 1.5F ? Fill.FULL : Fill.HALF;
            float horizontal = (RANDOM.nextFloat() - 0.5F) * (0.55F + severity * 0.30F);
            float vertical = -1.25F - RANDOM.nextFloat() * (0.65F + severity * 0.16F);
            PARTICLES.add(new Particle(
                    entityId,
                    ParticleKind.HEART,
                    fill,
                    ref.layer,
                    -1,
                    ref.x,
                    0.0F,
                    horizontal,
                    vertical,
                    (RANDOM.nextFloat() - 0.5F) * 0.2F,
                    (RANDOM.nextFloat() - 0.5F) * 0.18F,
                    25 + RANDOM.nextInt(10),
                    spawned / 3
            ));
            cursor -= amount;
            spawned++;
        }
        trimParticles();
    }

    private static void spawnDeathShards(int entityId, float maximum) {
        int count = maximum > LAYER_HEALTH
                ? HEARTS_PER_ROW
                : clamp((int) Math.ceil(maximum / 2.0F), 1, HEARTS_PER_ROW);
        for (int logical = count - 1; logical >= 0 && PARTICLES.size() < MAX_PARTICLES; logical--) {
            float center = ((count - 1) * 0.5F - logical) * HEART_SPACING;
            int sequence = count - 1 - logical;
            for (int quadrant = 0; quadrant < 4 && PARTICLES.size() < MAX_PARTICLES; quadrant++) {
                float side = (quadrant & 1) == 0 ? -1.0F : 1.0F;
                float verticalSide = quadrant < 2 ? -1.0F : 1.0F;
                PARTICLES.add(new Particle(
                        entityId,
                        ParticleKind.SHARD,
                        Fill.NONE,
                        0,
                        quadrant,
                        center + side * 1.8F,
                        verticalSide * 1.8F,
                        side * (0.35F + RANDOM.nextFloat() * 0.55F),
                        -0.65F + verticalSide * 0.25F - RANDOM.nextFloat() * 0.7F,
                        RANDOM.nextFloat() * TWO_PI,
                        (RANDOM.nextFloat() - 0.5F) * 0.35F,
                        22 + RANDOM.nextInt(8),
                        4 + sequence
                ));
            }
        }
        trimParticles();
    }

    private static final class HeartRef {
        final float x;
        final int layer;

        HeartRef(float x, int layer) {
            this.x = x;
            this.layer = layer;
        }
    }

    private static HeartRef heartRefAt(float hp, float maximum) {
        if (maximum > LAYER_HEALTH) {
            int layer = Math.max(0, (int) Math.floor(hp / LAYER_HEALTH));
            float inLayer = hp - layer * LAYER_HEALTH;
            int logical = clamp((int) Math.floor(inLayer / 2.0F), 0, HEARTS_PER_ROW - 1);
            return new HeartRef(((HEARTS_PER_ROW - 1) * 0.5F - logical) * HEART_SPACING, layer);
        }
        int count = clamp((int) Math.ceil(maximum / 2.0F), 1, HEARTS_PER_ROW);
        float hpPer = maximum / count;
        int logical = clamp((int) Math.floor(hp / hpPer), 0, count - 1);
        return new HeartRef(((count - 1) * 0.5F - logical) * HEART_SPACING, 0);
    }

    private static void trimParticles() {
        while (PARTICLES.size() > MAX_PARTICLES) PARTICLES.remove(0);
    }

    private static void cleanupStates(long now) {
        if (now - lastCleanupMs < 5_000L) return;
        lastCleanupMs = now;
        Iterator<Map.Entry<Integer, State>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().lastSeenMs > STALE_STATE_MS) iterator.remove();
        }
    }

    private static float phase(int seed, int index, int salt) {
        int mixed = mix(seed ^ index * 0x9E3779B9 ^ salt * 0x85EBCA6B);
        return (mixed & 0xFFFF) / 65535.0F * TWO_PI;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x85EBCA6B;
        value ^= value >>> 13;
        value *= 0xC2B2AE35;
        return value ^ value >>> 16;
    }

    private static float sin(float value) {
        return (float) Math.sin(value);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
