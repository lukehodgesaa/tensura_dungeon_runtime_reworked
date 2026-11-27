import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import snbt

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'src/main/resources/data/tensura_dungeons_unbound/dialogues'
READ_TAG = 'tensura_dungeons_unbound.dialog_read.'

NPCS = {
    'gobta': ('Gobta', [400]),
    'treyni': ('Treyni', [512, 600]),
    'kaijin': ('Kaijin', [300, 301, 302, 303]),
    'kurobe': ('Kurobe', [100]),
    'gard_mjollmile': ('Gard Mjollmile', [200]),
}

DEBRIEF = {
    'magicule': "I don't have enough Magicule to cast my spells.",
    'hurt': "I'm badly hurt.",
    'generic': 'All clear on my end. Ready when you are.',
}

SCORE_OPERATORS = {0: '==', 1: '<', 2: '>'}


def text(value):
    value = value.replace('{player}', '[player]')
    return re.sub(r'@p\b', '[player]', value)


def command(value):
    value = value.strip().lstrip('/')
    return re.sub(r'@dp\b|@p\b', '{player}', value)


def load_dialogs(source):
    dialogs = {}
    for path in sorted(Path(source).glob('*/*.json')):
        dialog = snbt.load(path)
        dialogs[int(dialog['DialogId'])] = dialog
    return dialogs


def availability(dialog):
    conditions = []
    for suffix in ('', '2', '3', '4'):
        mode = dialog.get('AvailabilityDialog' + suffix, 0)
        target = dialog.get('AvailabilityDialog' + suffix + 'Id', -1)
        if mode in (1, 2) and target >= 0:
            conditions.append({'type': 'player_tag', 'id': READ_TAG + str(target), 'invert': mode == 2})
    for suffix in ('', '2'):
        objective = dialog.get('AvailabilityScoreboard' + suffix + 'Objective', '')
        if objective:
            conditions.append({
                'type': 'score',
                'objective': objective,
                'operator': SCORE_OPERATORS.get(dialog.get('AvailabilityScoreboard' + suffix + 'Type', 0), '=='),
                'value': dialog.get('AvailabilityScoreboard' + suffix + 'Value', 0),
            })
    return conditions


def read_targets(dialogs):
    targets = set()
    for dialog in dialogs.values():
        for suffix in ('', '2', '3', '4'):
            if dialog.get('AvailabilityDialog' + suffix, 0) in (1, 2):
                targets.add(dialog.get('AvailabilityDialog' + suffix + 'Id', -1))
    return targets


def options(dialog):
    return [entry['Option'] for entry in sorted(dialog.get('Options', []), key=lambda entry: entry['OptionSlot'])]


def reachable(dialogs, slots):
    seen = []
    queue = [slot for slot in slots if slot in dialogs]
    while queue:
        current = queue.pop(0)
        if current in seen:
            continue
        seen.append(current)
        for option in options(dialogs[current]):
            if option['OptionType'] == 1 and option.get('Dialog', -1) in dialogs:
                queue.append(option['Dialog'])
    return seen


def base(name):
    return {
        'format': 5,
        'char_ticks': 1,
        'hold_ticks': 60,
        'npc_name': {'literal': name},
        'markdown': False,
        'text_effect': 'none',
        'freeze_source': False,
        'source_invulnerable': False,
        'cancel_if_source_missing': True,
        'exclusive_source': False,
        'graph_enabled': True,
    }


def npc_dialogue(name, slots, dialogs, tracked):
    definition = base(name)
    nodes = {}
    for index, slot in enumerate(slots):
        nodes['slot_%d' % index] = {
            'type': 'condition',
            'conditions': availability(dialogs[slot]),
            'next': 'd%d' % slot,
            'else_node': 'slot_%d' % (index + 1) if index + 1 < len(slots) else None,
        }
    for dialog_id in reachable(dialogs, slots):
        dialog = dialogs[dialog_id]
        choices = []
        for option in options(dialog):
            kind = option['OptionType']
            choice = {'literal': text(option['Title']), 'when_unavailable': 'hide', 'conditions': [], 'actions': []}
            if kind == 1:
                target = option.get('Dialog', -1)
                if target not in dialogs:
                    continue
                choice['goto_node'] = 'd%d' % target
                choice['conditions'] = availability(dialogs[target])
            elif kind == 4:
                choice['actions'] = [{'type': 'run_command', 'target': 'player', 'command': command(option['DialogCommand'])}]
            elif kind != 0:
                continue
            choices.append(choice)
        actions = []
        if dialog_id in tracked:
            actions.append({'type': 'add_player_tag', 'id': READ_TAG + str(dialog_id)})
        if dialog.get('DialogCommand'):
            actions.append({'type': 'run_command', 'target': 'player', 'command': command(dialog['DialogCommand'])})
        node = {'line': {'literal': text(dialog['DialogText'])}, 'actions': actions}
        if choices:
            node.update({'type': 'choice', 'choices': choices})
        else:
            node.update({'type': 'line', 'next': None})
        nodes['d%d' % dialog_id] = node
    first = options(dialogs[slots[0]])
    if first:
        definition['layout'] = {'choice_color': '#%06X' % (first[0].get('DialogColor', 0xFFFFFF) & 0xFFFFFF)}
    definition['start_node'] = 'slot_0'
    definition['nodes'] = nodes
    return definition


def debrief_dialogue(line):
    definition = base('')
    definition['start_node'] = 'line'
    definition['nodes'] = {'line': {'type': 'line', 'line': {'literal': line}, 'next': None, 'actions': []}}
    return definition


def write(path, definition):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(definition, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def main():
    if len(sys.argv) != 2:
        print('usage: convert_cnpc_dialogs.py <customnpcs dialogs folder>')
        return 1
    dialogs = load_dialogs(sys.argv[1])
    tracked = read_targets(dialogs)
    for key, (name, slots) in NPCS.items():
        definition = npc_dialogue(name, slots, dialogs, tracked)
        write(OUT / 'npc' / (key + '.json'), definition)
        print(key, sorted(int(node[1:]) for node in definition['nodes'] if node.startswith('d')))
    for key, line in DEBRIEF.items():
        write(OUT / 'debrief' / (key + '.json'), debrief_dialogue(line))
    return 0


if __name__ == '__main__':
    sys.exit(main())
