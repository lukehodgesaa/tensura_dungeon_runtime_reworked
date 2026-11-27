from nbt import BYTE, STR

NPC_ENTITY = 'tensura_dungeons_unbound:dungeon_npc'
CNPC_ENTITY = 'customnpcs:customnpc'
PROFILES = {
    'Gobta': 'gobta',
    'Treyni': 'treyni',
    'Kaijin': 'kaijin',
    'Kurobe': 'kurobe',
    'Gard Mjollmile': 'gard_mjollmile',
}


def convert(entity):
    if entity['id'][1] != CNPC_ENTITY:
        return entity
    profile = PROFILES.get(entity.get('Name', (STR, ''))[1])
    if profile is None:
        return None
    return {
        'id': (STR, NPC_ENTITY),
        'NpcId': (STR, profile),
        'Pos': entity['Pos'],
        'Rotation': entity['Rotation'],
        'PersistenceRequired': (BYTE, 1),
        'Invulnerable': (BYTE, 1),
    }
