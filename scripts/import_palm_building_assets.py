#!/usr/bin/env python3
"""Import the user's six unmodified palm textures and native building-block bindings.

Inputs are stable authoring files, never tmp. Does not touch any Minecraft save.
"""
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'assets-src/palm_building'
RES = ROOT / 'src/main/resources'
ART = RES / 'assets/stardewcraft'
FILES = {'palm_log': '棕榈树原木side.png', 'palm_log_top': '棕榈树原木top.png',
         'palm_leaves': '棕榈树树叶.png', 'palm_planks': '棕榈树木板.png',
         'palm_trapdoor': '棕榈树活板门.png', 'palm_coconut_block': '椰子.png'}
BLOCKS = ['palm_log', 'palm_leaves', 'palm_planks', 'palm_planks_stairs', 'palm_planks_slab', 'palm_trapdoor', 'palm_coconut_block']
NAMES = {
'en_us': ['Palm Log', 'Palm Leaves', 'Palm Planks', 'Palm Stairs', 'Palm Slab', 'Palm Trapdoor', 'Decorative Coconut Block'],
'zh_cn': ['棕榈原木', '棕榈树叶', '棕榈木板', '棕榈木楼梯', '棕榈木半砖', '棕榈木活板门', '装饰椰子方块'],
'de_de': ['Palmenstamm', 'Palmenblätter', 'Palmenholzbretter', 'Palmenholztreppe', 'Palmenholzstufe', 'Palmenholzfalltür', 'Dekorativer Kokosnussblock'],
'es_es': ['Tronco de palmera', 'Hojas de palmera', 'Tablones de palmera', 'Escaleras de palmera', 'Losa de palmera', 'Trampilla de palmera', 'Bloque de coco decorativo'],
'fr_fr': ['Bûche de palmier', 'Feuilles de palmier', 'Planches de palmier', 'Escalier en palmier', 'Dalle en palmier', 'Trappe en palmier', 'Bloc de noix de coco décoratif'],
'it_it': ['Tronco di palma', 'Foglie di palma', 'Assi di palma', 'Scalini di palma', 'Lastra di palma', 'Botola di palma', 'Blocco di cocco decorativo'],
'hu_hu': ['Pálmarönk', 'Pálmalevelek', 'Pálmadeszka', 'Pálmalépcső', 'Pálmafa lap', 'Pálmafa csapóajtó', 'Díszítő kókuszdióblokk'],
'ja_jp': ['ヤシの原木', 'ヤシの葉', 'ヤシの板材', 'ヤシの階段', 'ヤシのハーフブロック', 'ヤシのトラップドア', '装飾用ココナッツブロック'],
'ko_kr': ['야자나무 원목', '야자나무 잎', '야자나무 판자', '야자나무 계단', '야자나무 반 블록', '야자나무 다락문', '장식용 코코넛 블록'],
'pt_br': ['Tronco de palmeira', 'Folhas de palmeira', 'Tábuas de palmeira', 'Escadas de palmeira', 'Laje de palmeira', 'Alçapão de palmeira', 'Bloco de coco decorativo'],
'ru_ru': ['Пальмовое бревно', 'Пальмовые листья', 'Пальмовые доски', 'Пальмовые ступеньки', 'Пальмовая плита', 'Пальмовый люк', 'Декоративный кокосовый блок'],
 'tr_tr': ['Palmiye Kütüğü', 'Palmiye Yaprakları', 'Palmiye Tahtaları', 'Palmiye Merdiven', 'Palmiye Basamak', 'Palmiye Tuzak Kapısı', 'Dekoratif Hindistan Cevizi Bloğu']}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def tag(kind, name, ids, namespace='minecraft'):
    path = RES / f'data/{namespace}/tags/{kind}/{name}.json'
    data = json.loads(path.read_text()) if path.exists() else {'replace': False, 'values': []}
    for name in ids:
        value = 'stardewcraft:' + name
        if value not in data['values']: data['values'].append(value)
    write(path, data)


def model(name, parent, textures, **kwargs):
    write(ART / f'models/block/wood/{name}.json', {'parent': parent, 'textures': textures, **kwargs})


def legacy_definition():
    return {'id': 'ginger_south_palm', 'model': 'stardewcraft:block/ginger_island/south_palm_state_0',
            'kind': 'state_decor', 'type': 'hidden', 'state_property': 'variant', 'state_role': 'environment',
            'state_models': {str(i): f'stardewcraft:block/ginger_island/south_palm_state_{i}' for i in range(4)},
            'retired': True, 'placement_mode': 'floor', 'replacement': BLOCKS}


def write_legacy_bindings():
    """Old ID survives solely to migrate saved cells; the rejected art is never exported."""
    catalog = RES / 'data/stardewcraft/ginger_island/assets.json'
    data = json.loads(catalog.read_text())
    data['blocks'] = [a for a in data['blocks'] if a['id'] != 'ginger_south_palm'] + [legacy_definition()]
    write(catalog, data)
    for i in range(4):
        write(ART / f'models/block/ginger_island/south_palm_state_{i}.json', {
            'parent': 'stardewcraft:block/wood/palm_log',
            'textures': {'particle': 'stardewcraft:block/wood/palm_log'},
            'stardewcraft:collision': {'mode': 'custom', 'policy': 'retired_palm_root', 'boxes': [{'from': [0,0,0], 'to': [16,16,16]}]}})
    empty = 'stardewcraft:block/ginger_island/south_palm_retired_extension'
    write(ART / 'models/block/ginger_island/south_palm_retired_extension.json', {
        'textures': {'particle': 'stardewcraft:block/wood/palm_log'}, 'elements': []})
    variants = {}
    for facing in ('north', 'east', 'south', 'west'):
        for i in range(6):
            variants[f'facing={facing},part=main,variant={i}'] = {'model': f'stardewcraft:block/ginger_island/south_palm_state_{min(i,3)}'}
            variants[f'facing={facing},part=extension,variant={i}'] = {'model': empty}
    write(ART / 'blockstates/ginger_south_palm.json', {'variants': variants})
    write(ART / 'models/item/ginger_south_palm.json', {'parent': 'stardewcraft:block/wood/palm_log'})
    write(RES / 'data/stardewcraft/loot_tables/blocks/ginger_south_palm.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'conditions': [
            {'condition': 'minecraft:block_state_property', 'block': 'stardewcraft:ginger_south_palm', 'properties': {'part':'main'}},
            {'condition': 'minecraft:survives_explosion'}], 'entries': [{'type':'minecraft:item','name':'stardewcraft:palm_log'}]}]})
    tag('items', 'hidden', ['ginger_south_palm'], 'stardewcraft')


def main():
    records = []
    for name, original in FILES.items():
        src = SOURCE / 'textures' / original
        target = ART / f'textures/block/wood/{name}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, target)
        records.append({'source': str(src.relative_to(ROOT)), 'runtime': str(target.relative_to(ROOT)),
                        'sha256': hashlib.sha256(src.read_bytes()).hexdigest(), 'transformation': 'none'})
    tex = lambda n: 'stardewcraft:block/wood/' + n
    model('palm_log', 'minecraft:block/cube_column', {'side':tex('palm_log'),'end':tex('palm_log_top'),'particle':tex('palm_log')})
    model('palm_planks', 'minecraft:block/cube_all', {'all':tex('palm_planks'),'particle':tex('palm_planks')})
    model('palm_coconut_block', 'minecraft:block/cube_all', {'all':tex('palm_coconut_block'),'particle':tex('palm_coconut_block')})
    # No tintindex: the supplied leaf colors must not be multiplied by biome/season tints.
    model('palm_leaves', 'minecraft:block/cube_all', {'all':tex('palm_leaves'),
          'particle':'stardewcraft:block/tree/forest_canopy/deep_spring_particle'}, render_type='minecraft:cutout_mipped')
    for n in ('palm_planks','palm_leaves','palm_coconut_block'):
        write(ART / f'blockstates/{n}.json', {'variants': {'': {'model':tex(n)}}})
    write(ART / 'blockstates/palm_log.json', {'variants': {
        'axis=y': {'model':tex('palm_log')}, 'axis=x': {'model':tex('palm_log'),'x':90,'y':90},
        'axis=z': {'model':tex('palm_log'),'x':90}}})
    for suffix in ('stairs','stairs_inner','stairs_outer','slab','slab_top'):
        data = json.loads((ART / f'models/block/wood/maple_planks_{suffix}.json').read_text().replace('maple_planks','palm_planks'))
        data['textures']['particle'] = tex('palm_planks')
        write(ART / f'models/block/wood/palm_planks_{suffix}.json', data)
    for suffix in ('stairs','slab'):
        for folder in ('assets/stardewcraft/blockstates','data/stardewcraft/loot_tables/blocks','data/stardewcraft/recipes'):
            source = RES / folder / f'maple_planks_{suffix}.json'
            write(RES / folder / f'palm_planks_{suffix}.json', json.loads(source.read_text().replace('maple_planks','palm_planks')))
    for suffix in ('bottom','top','open'):
        model('palm_trapdoor_'+suffix, 'minecraft:block/template_orientable_trapdoor_'+suffix,
              {'texture':tex('palm_trapdoor'),'particle':tex('palm_trapdoor')})
    variants = {}
    for facing, turn in [('north',0),('east',90),('south',180),('west',270)]:
        for half in ('bottom','top'):
            for opened in ('false','true'):
                v={'model':tex('palm_trapdoor_'+('open' if opened=='true' else half))}
                if turn: v['y']=turn
                if opened=='true' and half=='top': v['x']=180; v['y']=(turn+180)%360
                variants[f'facing={facing},half={half},open={opened}']=v
    write(ART / 'blockstates/palm_trapdoor.json', {'variants':variants})
    for name in BLOCKS:
        write(ART / f'models/item/{name}.json', {'parent':tex(name if name!='palm_trapdoor' else name+'_bottom')})
        if name!='palm_planks_slab':
            write(RES / f'data/stardewcraft/loot_tables/blocks/{name}.json', {
                'type':'minecraft:block','pools':[{'rolls':1,'conditions':[{'condition':'minecraft:survives_explosion'}],
                 'entries':[{'type':'minecraft:item','name':'stardewcraft:'+name}]}]})
    data=json.loads((RES/'data/stardewcraft/recipes/maple_planks.json').read_text().replace('maple_', 'palm_'))
    write(RES/'data/stardewcraft/recipes/palm_planks.json', data)
    write(RES/'data/stardewcraft/recipes/palm_trapdoor.json', {
        'type':'minecraft:crafting_shaped','category':'redstone','group':'wooden_trapdoor',
        'pattern':['###','###'],'key':{'#':{'item':'stardewcraft:palm_planks'}},'result':{'item':'stardewcraft:palm_trapdoor','count':2}})
    for kind in ('blocks','items'):
        for tagname, names in [('logs',['palm_log']),('logs_that_burn',['palm_log']),('planks',['palm_planks']),
            ('leaves',['palm_leaves']),('wooden_stairs',['palm_planks_stairs']),('wooden_slabs',['palm_planks_slab']),
            ('wooden_trapdoors',['palm_trapdoor'])]: tag(kind,tagname,names)
    tag('blocks','mineable/axe',[n for n in BLOCKS if n!='palm_leaves'])
    tag('blocks','mineable/hoe',['palm_leaves'])
    for language, names in NAMES.items():
        path=ART / f'lang/{language}.json'; data=json.loads(path.read_text())
        data.update({'block.stardewcraft.'+n:label for n,label in zip(BLOCKS,names)})
        write(path,data)
    write(SOURCE / 'sources.json', {'origin':'User supplied tmp/棕榈树, 2026-10-23', 'textures':records,
           'blocks':BLOCKS, 'note':'Building kit only; no prefab or automatic tree generation. Existing edible coconut unchanged.'})
    write_legacy_bindings()
    print('Imported six unchanged textures, seven building blocks, and retired-palm compatibility bindings.')

if __name__=='__main__': main()
