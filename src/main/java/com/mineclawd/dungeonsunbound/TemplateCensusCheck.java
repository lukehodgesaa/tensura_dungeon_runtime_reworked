package com.mineclawd.dungeonsunbound;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.mixin.StructureTemplateAccessor;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalMetadata;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadataRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeTemplateDescriptor;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class TemplateCensusCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> OPTIONAL_NAMESPACES = Set.of("domum_ornamentum", "iceandfire", "minecolonies");

    private TemplateCensusCheck() {
    }

    public record Result(int checked, int skipped, int disabled, List<String> failures) {
    }

    public static Result run(ServerLevel level) {
        DungeonTemplateMechanicalRegistry.Snapshot snapshot =
            DungeonTemplateMechanicalRegistry.get(level, DungeonTemplateMetadataRegistry.get(level));
        int checked = 0;
        int skipped = 0;
        int disabled = 0;
        Map<String, Integer> optionalBlocks = new TreeMap<>();
        List<String> failures = new ArrayList<>();
        for (DungeonTemplateMechanicalMetadata metadata : snapshot.templates().values()) {
            if (CompositeTemplateDescriptor.isCompositeId(metadata.id())) {
                skipped++;
                continue;
            }
            if (!metadata.solverEligible()) {
                disabled++;
                continue;
            }
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                TensuraDungeonRuntime.MOD_ID, "rooms/linear/" + metadata.id()
            );
            Optional<StructureTemplate> loaded = level.getStructureManager().get(id);
            level.getStructureManager().remove(id);
            if (loaded.isEmpty()) {
                failures.add(metadata.id() + " missing");
                continue;
            }
            List<StructureTemplate.Palette> palettes =
                ((StructureTemplateAccessor) (Object) loaded.get()).tensuraDungeon$getPalettes();
            if (palettes.size() != 1) {
                failures.add(metadata.id() + " palettes=" + palettes.size());
                continue;
            }
            List<StructureTemplate.StructureBlockInfo> blocks = palettes.getFirst().blocks();
            int nonAir = 0;
            int fluid = 0;
            for (StructureTemplate.StructureBlockInfo info : blocks) {
                BlockState state = info.state();
                if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
                    continue;
                }
                nonAir++;
                String namespace = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace();
                if (OPTIONAL_NAMESPACES.contains(namespace)) {
                    optionalBlocks.merge(namespace, 1, Integer::sum);
                }
                if (!state.getFluidState().isEmpty()) {
                    fluid++;
                }
            }
            checked++;
            if (blocks.size() != metadata.serializedBlocks()
                || nonAir != metadata.nonAirBlocks()
                || fluid != metadata.fluidBearingBlocks()) {
                failures.add(metadata.id() + " serialized=" + blocks.size() + "/" + metadata.serializedBlocks()
                    + " nonAir=" + nonAir + "/" + metadata.nonAirBlocks()
                    + " fluid=" + fluid + "/" + metadata.fluidBearingBlocks());
            }
        }
        for (String failure : failures) {
            LOGGER.warn("[Dungeons Unbound] Template census failed: {}", failure);
        }
        LOGGER.info("[Dungeons Unbound] Template census: checked={} composite={} disabled={} failed={} optionalModBlocks={}",
            checked, skipped, disabled, failures.size(), optionalBlocks);
        return new Result(checked, skipped, disabled, failures);
    }
}
