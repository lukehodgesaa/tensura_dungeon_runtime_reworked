import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import hub_npcs
import nbt
from nbt import BYTE, INT, DOUBLE, STR, LIST, COMP, LARR

AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}
KEEP_ENTITIES = {hub_npcs.CNPC_ENTITY, hub_npcs.NPC_ENTITY, 'tensura:training_dummy'}
KEEP_DATA_NAMESPACES = {'minecraft', 'neoforge', 'tensura', 'manascore', 'geckolib', 'tensura_dungeon',
                        'tensura_additions', 'tensura_fog_barriers', 'tensuramoreskills', 'tensura_dungeons_unbound'}
REGION_X = range(-2, 2)
REGION_Z = range(-3, 3)
DATA_VERSION = 3955


def unpack(longs, bits, n=4096):
    per = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for value in longs:
        value &= (1 << 64) - 1
        for i in range(per):
            out.append((value >> (i * bits)) & mask)
            if len(out) == n:
                return out
    return out


def decode_pos(v):
    x = v >> 38
    y = v & 0xFFF
    if y >= 2048:
        y -= 4096
    z = (v >> 12) & 0x3FFFFFF
    if z >= (1 << 25):
        z -= 1 << 26
    return [x, y, z]


def namespace_of(value):
    return value.split(':', 1)[0] if ':' in value else 'minecraft'


def strip_foreign_data(entity):
    attachments = entity.get('neoforge:attachments')
    if attachments:
        kept = {k: v for k, v in attachments[1].items() if namespace_of(k) in KEEP_DATA_NAMESPACES}
        if kept:
            entity['neoforge:attachments'] = (COMP, kept)
        else:
            del entity['neoforge:attachments']
    storage = entity.get('ManasCoreStorage')
    if storage:
        registry = storage[1].get('manascore_registry_storage')
        if registry:
            items = [item for item in registry[1][2]
                     if namespace_of(item.get('manascore_registry_storage_id', (STR, ''))[1]) in KEEP_DATA_NAMESPACES]
            storage[1]['manascore_registry_storage'] = (LIST, ('L', COMP, items))


def palette_key(entry):
    return json.dumps(nbt.plain(entry), sort_keys=True)


def main():
    world, out_root = sys.argv[1], sys.argv[2]
    dim = os.path.join(world, 'dimensions', 'tensura_dungeon', 'dungeon')
    struct_dir = os.path.join(out_root, 'data', 'tensura_dungeons_unbound', 'structure', 'hub')
    os.makedirs(struct_dir, exist_ok=True)
    for old in os.listdir(struct_dir):
        os.remove(os.path.join(struct_dir, old))

    chunks = {}

    for rx in REGION_X:
        for rz in REGION_Z:
            path = os.path.join(dim, 'region', f'r.{rx}.{rz}.mca')
            if not os.path.exists(path) or os.path.getsize(path) == 0:
                continue
            for lx, lz, root in nbt.region_chunks(path):
                cx, cz = rx * 32 + lx, rz * 32 + lz
                bes = {}
                be_list = root.get('block_entities')
                if be_list:
                    for be in be_list[1][2]:
                        x, y, z = be['x'][1], be['y'][1], be['z'][1]
                        clean = {k: v for k, v in be.items() if k not in ('x', 'y', 'z', 'keepPacked')}
                        bes[(x, y, z)] = clean
                blocks = []
                sections = root.get('sections')
                if not sections:
                    continue
                for sec in sections[1][2]:
                    bs = sec.get('block_states')
                    if not bs:
                        continue
                    sy = sec['Y'][1]
                    palette = bs[1]['palette'][1][2]
                    if len(palette) == 1:
                        if palette[0]['Name'][1] in AIR:
                            continue
                        idx = [0] * 4096
                    else:
                        bits = max(4, (len(palette) - 1).bit_length())
                        idx = unpack(bs[1]['data'][1][1], bits)
                    air = {i for i, e in enumerate(palette) if e['Name'][1] in AIR}
                    for i, p in enumerate(idx):
                        if p in air:
                            continue
                        y = sy * 16 + (i >> 8)
                        z = cz * 16 + ((i >> 4) & 15)
                        x = cx * 16 + (i & 15)
                        blocks.append((x, y, z, palette[p], bes.get((x, y, z))))
                if blocks:
                    chunks.setdefault((cx, cz), {'blocks': [], 'entities': []})['blocks'] = blocks

    kept_entities = 0
    for rx in REGION_X:
        for rz in REGION_Z:
            path = os.path.join(dim, 'entities', f'r.{rx}.{rz}.mca')
            if not os.path.exists(path) or os.path.getsize(path) == 0:
                continue
            for _lx, _lz, root in nbt.region_chunks(path):
                ents = root.get('Entities')
                if not ents:
                    continue
                for ent in ents[1][2]:
                    eid = ent['id'][1]
                    if eid not in KEEP_ENTITIES:
                        continue
                    pos = [p for p in ent['Pos'][1][2]]
                    cx, cz = int(pos[0] // 16), int(pos[2] // 16)
                    clean = {k: v for k, v in ent.items() if k not in ('UUID', 'Leash', 'Passengers')}
                    clean = hub_npcs.convert(clean)
                    if clean is None:
                        continue
                    strip_foreign_data(clean)
                    chunks.setdefault((cx, cz), {'blocks': [], 'entities': []})['entities'].append((pos, clean))
                    kept_entities += 1

    manifest_chunks = []
    total_blocks = 0
    total_bytes = 0
    for (cx, cz), content in sorted(chunks.items()):
        ys = [b[1] for b in content['blocks']] + [int(e[0][1]) for e in content['entities']]
        min_y, max_y = min(ys), max(ys)
        ox, oy, oz = cx * 16, min_y, cz * 16
        palette_index = {}
        palette = []
        block_list = []
        for x, y, z, entry, be in content['blocks']:
            key = palette_key(entry)
            if key not in palette_index:
                palette_index[key] = len(palette)
                palette.append(entry)
            block = {
                'pos': (LIST, ('L', INT, [x - ox, y - oy, z - oz])),
                'state': (INT, palette_index[key]),
            }
            if be is not None:
                block['nbt'] = (COMP, be)
            block_list.append(block)
        entity_list = []
        for pos, ent in content['entities']:
            rel = [pos[0] - ox, pos[1] - oy, pos[2] - oz]
            entity_list.append({
                'pos': (LIST, ('L', DOUBLE, rel)),
                'blockPos': (LIST, ('L', INT, [int(rel[0] // 1), int(rel[1] // 1), int(rel[2] // 1)])),
                'nbt': (COMP, ent),
            })
        template = {
            'size': (LIST, ('L', INT, [16, max_y - min_y + 1, 16])),
            'palette': (LIST, ('L', COMP, palette)),
            'blocks': (LIST, ('L', COMP, block_list)),
            'entities': (LIST, ('L', COMP, entity_list)),
            'DataVersion': (INT, DATA_VERSION),
        }
        name = f'c_{cx}_{cz}'
        target = os.path.join(struct_dir, name + '.nbt')
        nbt.save_gz(target, template)
        total_bytes += os.path.getsize(target)
        total_blocks += len(block_list)
        manifest_chunks.append({'x': cx, 'z': cz, 'y': oy, 'template': 'hub/' + name,
                                'blocks': len(block_list), 'entities': len(entity_list)})

    run = nbt.plain(nbt.load(os.path.join(world, 'data', 'tensura_dungeon_run_manager.dat')))['data']
    spawn = decode_pos(run['HubSpawnAnchorPos'])
    manifest_chunks.sort(key=lambda c: (c['x'] * 16 - spawn[0]) ** 2 + (c['z'] * 16 - spawn[2]) ** 2)
    manifest = {
        'format': 1,
        'dimension': 'tensura_dungeon:dungeon',
        'source': 'MINECLAWD/saves/Tensura Dungeon 2_02',
        'hubSpawnAnchor': spawn,
        'hubReturnAnchor': decode_pos(run['HubReturnAnchorPos']),
        'hubGateAnchor': decode_pos(run['HubGateAnchorPos']),
        'hubSafeZoneCornerA': decode_pos(run['HubSafeZoneCornerA']),
        'hubSafeZoneCornerB': decode_pos(run['HubSafeZoneCornerB']),
        'totalBlocks': total_blocks,
        'chunks': manifest_chunks,
    }
    manifest_dir = os.path.join(out_root, 'data', 'tensura_dungeons_unbound', 'hub')
    os.makedirs(manifest_dir, exist_ok=True)
    with open(os.path.join(manifest_dir, 'manifest.json'), 'w', encoding='utf-8') as fh:
        json.dump(manifest, fh, indent=1)
    print(f'chunks={len(manifest_chunks)} blocks={total_blocks} entities={kept_entities} '
          f'bytes={total_bytes} spawn={spawn}')


if __name__ == '__main__':
    main()
