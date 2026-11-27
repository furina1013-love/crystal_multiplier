package cn.furina.crystalmultiplier.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Gives the five non-front faces of the machine a random crystal block texture taken from the
 * installed mods.
 *
 * <p>The textures are picked exactly once: the first time the client bakes models without a saved
 * choice, five distinct crystal-like block textures are drawn from the stitched block atlas and
 * written to {@code config/crystal_multiplier_side_textures.json}. Every later launch reads that
 * file, so the machine always keeps the same five faces (no reshuffling, no pack reload needed).</p>
 *
 * <p>Only the baked quads are re-sprited, geometry comes from Mekanism's {@code block/machine}
 * parent, so the machine still rotates with its facing property.</p>
 */
@EventBusSubscriber(modid = "crystal_multiplier", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RandomSideTextures {

    /** The front face in model space; the blockstate rotates the whole model around it. */
    private static final Direction FRONT = Direction.NORTH;

    /** The five faces that get a random crystal texture, in a stable order. */
    private static final List<Direction> SIDES = List.of(Direction.UP, Direction.DOWN, Direction.EAST,
            Direction.WEST, Direction.SOUTH);

    private static final String[] TIER_MODELS = {"crystal_multiplier", "crystal_multiplier_advanced",
            "crystal_multiplier_elite", "crystal_multiplier_ultimate"};

    /** Mods whose textures are never used as side faces. */
    private static final Set<String> SKIP_NAMESPACES = Set.of("minecraft", "crystal_multiplier", "mekanism");

    private RandomSideTextures() {
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<Direction, ResourceLocation> picks = loadOrPick();
        if (picks.isEmpty()) {
            return;
        }
        for (String tier : TIER_MODELS) {
            for (String suffix : new String[]{"", "_active"}) {
                ResourceLocation plainId = ResourceLocation.fromNamespaceAndPath("crystal_multiplier", "block/" + tier + suffix);
                ModelResourceLocation modelId = ModelResourceLocation.standalone(plainId);
                BakedModel original = event.getModels().get(modelId);
                if (original != null) {
                    event.getModels().put(modelId, new SidedModel(original, picks));
                }
            }
        }
    }

    /** Reads the saved choice, or picks (and stores) a new one when there is none yet. */
    private static Map<Direction, ResourceLocation> loadOrPick() {
        Map<Direction, ResourceLocation> saved = readChoice();
        if (!saved.isEmpty()) {
            return saved;
        }
        Map<Direction, ResourceLocation> picked = pick();
        if (!picked.isEmpty()) {
            writeChoice(picked);
        }
        return picked;
    }

    private static Path choiceFile() {
        return FMLPaths.CONFIGDIR.get().resolve("crystal_multiplier_side_textures.json");
    }

    private static Map<Direction, ResourceLocation> readChoice() {
        Map<Direction, ResourceLocation> result = new EnumMap<>(Direction.class);
        Path file = choiceFile();
        if (!Files.isRegularFile(file)) {
            return result;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (Direction side : SIDES) {
                if (root.has(side.getSerializedName())) {
                    ResourceLocation id = ResourceLocation.tryParse(root.get(side.getSerializedName()).getAsString());
                    if (id != null) {
                        result.put(side, id);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            result.clear();
        }
        return result;
    }

    private static void writeChoice(Map<Direction, ResourceLocation> picks) {
        JsonObject root = new JsonObject();
        for (Direction side : SIDES) {
            ResourceLocation id = picks.get(side);
            if (id != null) {
                root.addProperty(side.getSerializedName(), id.toString());
            }
        }
        try {
            Path file = choiceFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // A missing config directory only means the choice is picked again next launch.
        }
    }

    /** Picks five distinct crystal block textures; repeats one if fewer than five exist. */
    private static Map<Direction, ResourceLocation> pick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return new EnumMap<>(Direction.class);
        }
        Map<ResourceLocation, ?> found =
                minecraft.getResourceManager().listResources("textures/block", path -> path.getPath().endsWith(".png"));
        List<ResourceLocation> candidates = new ArrayList<>();
        for (ResourceLocation raw : found.keySet()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(raw.getNamespace(),
                    raw.getPath().substring("textures/".length(), raw.getPath().length() - ".png".length()));
            if (SKIP_NAMESPACES.contains(id.getNamespace())) {
                continue;
            }
            String path = id.getPath();
            if (path.startsWith("block/") && path.toLowerCase(Locale.ROOT).contains("crystal")) {
                candidates.add(id);
            }
        }
        Map<Direction, ResourceLocation> picks = new EnumMap<>(Direction.class);
        if (candidates.isEmpty()) {
            return picks;
        }
        candidates.sort(null);
        long seed = System.nanoTime();
        RandomSource random = RandomSource.create(seed);
        List<ResourceLocation> pool = new ArrayList<>(new LinkedHashSet<>(candidates));
        java.util.Collections.shuffle(pool, new java.util.Random(seed));
        for (int i = 0; i < SIDES.size(); i++) {
            picks.put(SIDES.get(i), pool.get(i % pool.size()));
        }
        return picks;
    }

    /** Baked model wrapper that swaps the sprite of every side quad. */
    private static final class SidedModel implements BakedModel {
        private final BakedModel base;

        SidedModel(BakedModel base, Map<Direction, ResourceLocation> picks) {
            this.base = base;
            this.textureIds = picks;
        }

        private final Map<Direction, ResourceLocation> textureIds;
        private final Map<Direction, TextureAtlasSprite> sprites = new EnumMap<>(Direction.class);

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction,
                                                 @NotNull RandomSource random) {
            List<BakedQuad> quads = this.base.getQuads(state, direction, random);
            TextureAtlasSprite sprite = direction == null || direction == FRONT ? null : spriteFor(direction);
            if (sprite == null || quads.isEmpty()) {
                return quads;
            }
            List<BakedQuad> out = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                out.add(new BakedQuad(quad.getVertices().clone(), quad.getTintIndex(), quad.getDirection(), sprite,
                        true));
            }
            return out;
        }

        @Nullable
        private TextureAtlasSprite spriteFor(Direction side) {
            if (this.sprites.containsKey(side)) {
                return this.sprites.get(side);
            }
            ResourceLocation id = this.textureIds.get(side);
            if (id == null) {
                return null;
            }
            TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
            this.sprites.put(side, sprite);
            return sprite;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return this.base.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return this.base.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return this.base.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return this.base.isCustomRenderer();
        }

        @Override
        public @NotNull TextureAtlasSprite getParticleIcon() {
            return this.base.getParticleIcon();
        }

        @Override
        public @NotNull ItemTransforms getTransforms() {
            return this.base.getTransforms();
        }

        @Override
        public @NotNull ItemOverrides getOverrides() {
            return this.base.getOverrides();
        }
    }
}
