import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import hub_npcs
import nbt
from nbt import COMP, LIST

HUB = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'data',
                   'tensura_dungeons_unbound', 'structure', 'hub')


def main():
    changed = 0
    for name in sorted(os.listdir(HUB)):
        path = os.path.join(HUB, name)
        root = nbt.load(path)
        entities = root.get('entities')
        if not entities or not entities[1][2]:
            continue
        kept = []
        dirty = False
        for entry in entities[1][2]:
            converted = hub_npcs.convert(entry['nbt'][1])
            if converted is not entry['nbt'][1]:
                dirty = True
            if converted is None:
                continue
            entry['nbt'] = (COMP, converted)
            kept.append(entry)
        if dirty:
            root['entities'] = (LIST, ('L', COMP, kept))
            nbt.save_gz(path, root)
            changed += 1
            print(name, [e['nbt'][1]['id'][1] + ':' + e['nbt'][1].get('NpcId', ('', ''))[1] for e in kept])
    print('templates changed:', changed)


if __name__ == '__main__':
    main()
