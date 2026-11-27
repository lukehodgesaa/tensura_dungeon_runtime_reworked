# Tensura: Dungeons Unbound

All of the Tensura Dungeon mods from the MINECLAWD pack merged into one NeoForge 1.21.1 mod,
built from source, plus a first-run bootstrap that builds the dungeon hub on a brand-new world.

Drop `tensura_dungeons_unbound-1.0.0.jar` and its dependencies into a mods folder and the
dungeon works: hub, NPCs, rifts, procedurally generated floors, HUD, UI, weapons.

## Getting started (IntelliJ)

1. Install JDK 21.
2. `File > Open` this folder and import it as a Gradle project.
3. Run `build-mod.bat`, or `gradlew build stageDistribution`.
   - `build/libs/tensura_dungeons_unbound-1.0.0.jar` is the mod.
   - `dist/mods/` is the mod plus every dependency, ready to copy into a mods folder.
3. `gradlew runClient` / `gradlew runServer` start a dev game with everything in `libs/` loaded
   (ModDevGradle also generates IntelliJ run configurations).

## What is inside

One jar, eight mod ids. Every original mod keeps its id, so registries, saved data, configs and
existing MINECLAWD worlds keep working. Each mod writes its own default config.

| Mod id | Version | Source package | What it does |
|---|---|---|---|
| `tensura_dungeons_unbound` | 1.0.0 | `com.mineclawd.dungeonsunbound` | Hub bootstrap, hub NPCs and their dialogues, `/dungeonsunbound` |
| `tensura_dungeon` | 0.20.20-stage20_20_instance_recovery | `com.mineclawd.tensuradungeon` | Procedural dungeon runtime: rifts, hub, generated floors, instances, rewards, shop, bank, forge, 347 room/segment structures |
| `tensura_additions` | 0.1.0 | `com.mineclawd.tensuraadditions` | Weapons, NPC models, visuals, building additions |
| `tensura_fog_barriers` | 0.5.2 | `com.mineclawd.tensurafogbarriers` | Fog gates |
| `tensura_hud_native` | 1.0.0 | `com.mineclawd.tensurahudnative` | Native HUD (client) |
| `tensura_dungeon_ui` | 1.1.15 | `dev.luke.tensuradungeon` | Floor selector, mailbox, rankings, dungeon log, shop/bank/forge screens (client) |
| `tensura_dungeon_lockon` | 0.5.0 | `libs/bundled` (no source) | Lock-on camera (client) |
| `tensura_health_unbound` | 1.0.0 | `com.mineclawd.tensurahealthunbound` | No max health cap for mobs inside the dungeon; everything else keeps Tensura's cap |

All metadata lives in one file: `src/main/resources/META-INF/neoforge.mods.toml`.

### Where the source came from

- The five mods with source come from the MCXT handoff (`Tensura-Dungeon-Helper-Handoff.zip`,
  sources dated Aug 10).
- The jars actually installed in MINECLAWD carried later hotfixes (Sept 5-6) that were not in
  that source. They were recovered from those jars and merged into the source:
  - exit portal opens from anywhere in its 1x6 column with any item in hand, plus an empty-hand
    client packet (`DungeonExitInteractPayload`, `DungeonExitPortalInputEvents`)
  - players stuck inside a cleaning instance cell after re-login are sent back to the hub
    (`DungeonInstanceRecoveryService.retryPendingCleanupHubReturns`)
  - a reused instance cell with leftover geometry is cleaned up instead of failing preparation
    (`GeneratedLinearConnectorPlacementCommands.beginSoloTeleporterStartup`)
  - failed startup preparation fully clears its plan (`GeneratedFloorWorldWorkScheduler.failStartupPreparation`)
  - overhead health text "HP 12,345 / 20,000 62%" (`OverheadStatusRenderer`)
- The Aug 10 source also had a few things the installed jars did not (extra encounter modifiers,
  a core tooltip in the loot summary). They are kept.
- Lock-On has no source anywhere. Its original jar sits in `libs/bundled/` and is unpacked into
  the mod at build time (`unpackBundled`).
- Health Unbound was rewritten from scratch here (the original lifted the cap for every entity
  in every dimension). Now any mob (not players) inside `tensura_dungeon:dungeon` has no max
  health cap; players and all mobs elsewhere keep Tensura's `maxHP` config value (10,000 by
  default). A mob carried out of the dungeon is clamped back down when it arrives.

`reference/mineclawd-installed-jars/` (git-ignored, local only) holds the original jars.
`python -I tools/compare_with_installed.py build/libs/tensura_dungeons_unbound-1.0.0.jar reference/mineclawd-installed-jars`
lists anything the build is missing compared to them.

## First start on a new world

1. The hub exported from `MINECLAWD/saves/Tensura Dungeon 2_02` (551 chunks, 4,477,040 blocks,
   5 NPCs, 6 training dummies) is built in `tensura_dungeon:dungeon` at its original
   coordinates. It takes about 20 seconds in the background; progress survives restarts.
2. The hub is registered with the runtime (spawn 17 325 0, return 17 325 -1, gate -80 394 0,
   safe zone -225 278 166 .. 186 534 -159), the same as `/tensuradungeon hub setspawn` etc.
3. From then on it plays like the pack: the Shattered Halo rift rolls once per in-game day
   (10% chance) near a player and pulls players into the hub; the Expedition Table starts runs,
   NPCs handle shop, bank and forge; floors are generated procedurally in instances.

Worlds that already have a configured hub (the original MINECLAWD saves) are left alone.

Operator commands: `/dungeonsunbound hub status`, `/dungeonsunbound hub rebuild`,
`/dungeonsunbound verifytemplates` (loads every dungeon room and checks its block census against
`template_mechanics.json`; works from the server console), `/tensuradungeon rift open` (force a
rift near you in the Overworld).

## Optional decoration mods

About 100 rooms and corridor segments were built with blocks from Domum Ornamentum, Ice and Fire
and MineColonies. The structure files keep those blocks. When a template loads, each such block
is kept if its mod is installed and swapped for a vanilla look-alike if not, so the dungeon works
with any combination: neither mod, Domum only, Ice and Fire only, or both.

- Domum pillars become the material they were textured with (calcite, prismarine, tuff bricks
  and so on); sand bricks become cut or smooth sandstone; decorative barrels become barrels.
- Ice and Fire dragonscale becomes polished blackstone, dread stone becomes deepslate bricks
  (stairs keep their shape), gold piles become gold pressure plates.
- MineColonies hut blocks become a smithing table, composter or loom; racks and barrels become
  barrels.

Every swap is one non-air block for one non-air block, so the room census the generator checks
still matches.

## Client settings

- The HUD starts on `automatic`: Tensura's own HUD everywhere except during a dungeon run, where
  Tensura's HUD is hidden and the dungeon HUD (the cosmetic equipped in the shop, Gothic if none)
  takes over. When the run ends it goes back to Tensura's own HUD.
  `/tensurahud theme <gothic|frey|rimuru|benimaru|souei|vanilla>` pins one theme everywhere
  (`vanilla` = always Tensura's own HUD); `/tensurahud theme automatic` goes back to the default.
- The dungeon quest tracker is on by default and only shows inside the dungeon.
  `/tensurahud tracker hide` hides it, `/tensurahud tracker show` brings it back.
- NPC dialogue starts in the vanilla box (plain panel, speaker name in yellow, options in the NPC's
  old CustomNPCs colour). `/tensurahud dialogue themed` switches to the dungeon dialogue panel
  (nameplate with the NPC's colours and icons), `/tensurahud dialogue vanilla` switches back.
- The shop, bank, forge, dungeon list and loot summary scale themselves and use your GUI scale.
  The dungeon preparation screens have a fixed layout that overlaps below about 800x450 GUI
  pixels, so while one of them is open the GUI scale is lowered just enough to give at least that
  (scale 2 on a 1080p screen, the same as the MINECLAWD pack) and put back when it closes. It is
  never raised above your setting and nothing else is affected.
- These are stored in `config/tensura_hud_native-client.properties`.

## Hub NPCs

Kurobe, Gard Mjollmile, Kaijin, Gobta and Treyni are `tensura_dungeons_unbound:dungeon_npc`
entities (`com.mineclawd.dungeonsunbound.npc`). The `NpcId` tag picks the profile in
`DungeonNpcProfile`: name, title, size, model and dialogue. They stand where the CustomNPCs versions
stood, look at the nearest player within 48 blocks, cannot be pushed or hurt (only `/kill` or a
creative player removes them), and show the same name and title plaques.

Their talk runs on Dialogue Studio (`dlgstd`). The dialogues live in
`data/tensura_dungeons_unbound/dialogues/`: `npc/<id>.json` for the NPCs and `debrief/*.json` for
the subordinate debrief lines. They were converted 1:1 from the old CustomNPCs dialogs with
`tools/convert_cnpc_dialogs.py`: same text, options, commands, first-available slot order, and
read-dialog gates (player tags `tensura_dungeons_unbound.dialog_read.<id>`).

Dialogue Studio only runs the graph; its own dialogue box is never shown. Our dialogues are drawn
by `DungeonDialogueScreen`, in the vanilla box or the themed dungeon panel (see Client settings),
with typed text and numbered options. Click, Space, Enter or E shows the full line at once, then continues; 1-9 or the
arrow keys pick an option; Esc skips the rest of the conversation. The dungeon voice cues play
for each line.

To spawn one by hand: `/summon tensura_dungeons_unbound:dungeon_npc ~ ~ ~ {NpcId:"kurobe"}`.

## Dependencies (`libs/`)

Everything in `libs/` is on the compile classpath, loaded in dev runs, and copied to `dist/mods`.

Required:

- `tensura-neoforge-2.0.1.0`, `manascore-neoforge-4.0.0.2`, `architectury-13.0.8`,
  `geckolib-neoforge-1.21.1-4.9`, `SmartBrainLib 1.16.11`, `TerraBlender 4.1.0.8`
- `ldlib2-neoforge-1.21.1-2.2.29-all` (dungeon UI; its nested Taffy/Yoga jars are extracted for
  compiling only)
- `dlgstdneo-1.0.0` (Dialogue Studio, MIT): runs the NPC and debrief dialogues

Optional:

- `Loot Beams Refork 3.4.7` + `Nirvana Lib 2.2.0` + `fzzy_config 0.7.6` + `common-networking 1.0.21`
  + `kotlinforforge 5.12.0`: loot beams on dungeon drops.
- `tensuramoreskills 0.0.4.8`: optional Additions integration.

NeoForge 21.1.216 or newer, required by LDLib2 (built and run in dev on 21.1.233, tested on 21.1.234).

## Layout

- `src/main/java/` all mod source (packages listed above)
- `src/main/resources/data/tensura_dungeon/structure/` the dungeon room and segment structures
- `src/main/resources/data/tensura_dungeons_unbound/` hub templates and manifest
- `src/main/resources/data/tensura_dungeons_unbound/dialogues/` NPC and debrief dialogues
- `src/main/resources/assets/tensuranpc/` Gobta's skin (from the pack's loose customnpcs folder)
- `libs/` dependency jars, `libs/bundled/` source-less fused mods
- `tools/export_hub.py` re-export the hub from a save:
  `python -I tools/export_hub.py "<world dir>" src/main/resources`
- `tools/compare_with_installed.py` compare the build with the original MINECLAWD jars
- `tools/nbt.py` small NBT reader and writer used by the tools
- `tools/convert_cnpc_dialogs.py` turns a CustomNPCs `dialogs` folder into the Dialogue Studio
  files (`tools/snbt.py` reads the CustomNPCs files)
- `tools/hub_npcs.py` maps CustomNPCs hub entities to `dungeon_npc` (used by `export_hub.py`);
  `tools/convert_hub_npcs.py` applies it to the hub templates already in the repo

## Notes

- The hub chests keep the items they had in the source world. The two goblins named "Astra" and
  the magicule benchmark area were left out of the hub export.
- CustomNPCs and the CNPC Gecko addon are no longer used; their jars are kept in
  `reference/removed-libs/` (git-ignored). Gard's model and animations come from the addon and now
  ship as `tensura_additions:geo/gard_mjollmile.geo.json`. Testarossa, The Watcher and the Eternal
  Orrery were never placed in the hub, so they were not carried over. Treyni opens her "Open Shop"
  line every time, as before: her briefing dialog sits in a later slot behind an always-available
  one, the same as in CustomNPCs.
- The largest file is a 30 MB structure, so the repo works on GitHub without Git LFS
  (about 620 MB including `libs/`).
- Do not install this next to the old separate dungeon jars; they share mod ids.
- The source has no comments; this README is the documentation.
- Floors are placed lazily: the next room is built a slice per tick while you walk, so the server
  stays at 20 TPS. The slice is 15 ms per tick when the server is healthy, 6 ms when the average
  tick is over 35 ms and 2 ms over 45 ms. The room you are heading into keeps being placed even
  under heavy load (it used to pause completely, which could stall a run for minutes in a
  single-player or dev client).
- The `[TD-TICK]` heartbeat, `[TD-RECOVER]` retry and per-tick cleanup `[TD-PERF]` lines log at
  DEBUG, so the normal log stays quiet.
