import copy
import json
import unittest
from pathlib import Path
from compile_native_npc import compile_model

ROOT = Path(__file__).resolve().parents[2]

class NativeCompilerTest(unittest.TestCase):
    def test_seated_gil_keeps_its_single_surface_waist_and_rigid_rocking(self):
        from unittest.mock import patch
        base=ROOT/'assets-src/npc/gil'
        model=json.loads((base/'gil.bbmodel').read_text())
        profile=json.loads((base/'motion.json').read_text())
        compiled,png=compile_model(model,profile,'gil',required_clips=profile['requiredClips'])
        with patch('compile_native_npc.skin_joints',lambda bones,cubes,quads,hip_skin=0:quads):
            authored,original_png=compile_model(model,profile,'gil',required_clips=profile['requiredClips'])
        self.assertEqual(original_png,png)
        for field in ('bones','clips','profile'):self.assertEqual(authored[field],compiled[field])
        surfaces=[q for q in compiled['quads'] if q.get('skin')]
        self.assertTrue(surfaces)
        self.assertEqual({'body_shirt'},{q['sourcePart'] for q in surfaces})
        self.assertEqual({('person','body')},{(compiled['bones'][q['skin']['upper']]['name'],
                                             compiled['bones'][q['skin']['lower']]['name']) for q in surfaces})
        self.assertTrue(any(0<w<1 for q in surfaces for w in q['skin']['weights']))
        self.assertEqual({'root'},{compiled['bones'][t['bone']]['name']
                                 for clip in compiled['clips'].values() for t in clip['tracks']})

    def test_gunther_pants_remain_sewn_to_pelvis(self):
        base=ROOT/'assets-src/npc/gunther'
        model=json.loads((base/'gunther.bbmodel').read_text())
        profile=json.loads((base/'motion.json').read_text())
        compiled,png=compile_model(model,profile,'gunther')
        import base64
        self.assertEqual(base64.b64decode(model['textures'][0]['source'].split(',',1)[1]),png)
        for side in ('left','right'):
            leg=next(i for i,b in enumerate(compiled['bones']) if b['name']=='leg_'+side)
            rows=[q for q in compiled['quads'] if q['bone']==leg
                  and min(v[1] for v in q['vertices'])>10.5]
            self.assertGreater(len(rows),8)
            for q in rows:
                self.assertEqual('root',compiled['bones'][q['skin']['upper']]['name'])
                self.assertEqual(leg,q['skin']['lower'])
                for v,w in zip(q['vertices'],q['skin']['weights']):
                    if v[1]>=12:self.assertEqual(0,w)
        invalid=copy.deepcopy(profile);invalid['hipSkinHeight']=float('nan')
        with self.assertRaisesRegex(ValueError,'hip skin height'):
            compile_model(model,invalid,'gunther')

    def test_evelyn_daily_sources_preserve_approved_model_and_closed_eyes(self):
        base=ROOT/'assets-src/npc/evelyn'
        original=json.loads((base/'evelyn.bbmodel').read_text())
        for variant in ('sit','sleep','garden'):
            model=json.loads((base/f'evelyn_{variant}.bbmodel').read_text())
            for field in ('elements','groups'):
                actual={entry['uuid']:entry for entry in model[field]}
                for entry in original[field]:self.assertEqual(entry,actual[entry['uuid']])
            self.assertEqual(original['textures'][0]['source'],model['textures'][0]['source'])
            actual={a['name']:a for a in model['animations']}
            for a in original['animations']:self.assertEqual(a,actual[a['name']])
            profile=json.loads((base/f'evelyn_{variant}.motion.json').read_text())
            compiled,_=compile_model(model,profile,'evelyn_'+variant,required_clips=profile['requiredClips'])
            for stage in ('enter','play','exit'):
                clip=actual[f'animation.evelyn.{variant}_{stage}']
                self.assertFalse(any(t['name'].startswith(('eye_','lid_')) for t in clip['animators'].values()))
            model['animations']=[a for a in model['animations'] if a['name']!=f'animation.evelyn.{variant}_exit']
            with self.assertRaisesRegex(ValueError,'Missing pilot clip'):
                compile_model(model,profile,'evelyn_'+variant,required_clips=profile['requiredClips'])

    def test_gus_support_preserves_approved_appearance_and_base_clips(self):
        base=ROOT/'assets-src/npc/gus'
        original=json.loads((base/'gus.bbmodel').read_text())
        for variant in ('sit','sleep'):
            model=json.loads((base/f'gus_{variant}.bbmodel').read_text())
            for field in ('elements','groups','outliner'):
                self.assertEqual(original[field],model[field])
            self.assertEqual(original['textures'][0]['source'],model['textures'][0]['source'])
            actual={a['name']:a for a in model['animations']}
            for a in original['animations']:self.assertEqual(a['animators'],actual[a['name']]['animators'])
            profile=json.loads((base/f'gus_{variant}.motion.json').read_text())
            compiled,_=compile_model(model,profile,'gus_'+variant,required_clips=profile['requiredClips'])
            for stage in ('enter','play','exit'):self.assertIn(f'animation.gus.{variant}_{stage}',compiled['clips'])
            model['animations']=[a for a in model['animations'] if a['name']!=f'animation.gus.{variant}_exit']
            with self.assertRaisesRegex(ValueError,'Missing pilot clip'):
                compile_model(model,profile,'gus_'+variant,required_clips=profile['requiredClips'])

    def test_gus_clean_preserves_approved_base_and_requires_all_stages(self):
        base=ROOT/'assets-src/npc/gus'
        original=json.loads((base/'gus.bbmodel').read_text())
        model=json.loads((base/'gus_clean.bbmodel').read_text())
        profile=json.loads((base/'gus_clean.motion.json').read_text())
        for field in ('elements','groups'):
            actual={entry['uuid']:entry for entry in model[field]}
            for entry in original[field]:
                for key in ('name','from','to','origin','rotation','faces','inflate'):
                    if key in entry:self.assertEqual(entry[key],actual[entry['uuid']].get(key))
        self.assertEqual(original['textures'][0]['source'],model['textures'][0]['source'])
        clips={clip['name']:clip for clip in model['animations']}
        for clip in original['animations']:
            self.assertEqual(clip['animators'],clips[clip['name']]['animators'])
        compiled,_=compile_model(model,profile,'gus_clean',required_clips=profile['requiredClips'])
        self.assertEqual(1.6,compiled['clips']['animation.gus.clean_enter']['length'])
        self.assertEqual(1.6,compiled['clips']['animation.gus.clean_exit']['length'])
        model['animations']=[a for a in model['animations'] if a['name']!='animation.gus.clean_exit']
        with self.assertRaisesRegex(ValueError,'Missing pilot clip'):
            compile_model(model,profile,'gus_clean',required_clips=profile['requiredClips'])
        with self.assertRaisesRegex(ValueError,'required_clips'):
            compile_model(model,profile,'gus_clean',required_clips=[])

    def setUp(self):
        base = ROOT/'assets-src/npc/sam'
        self.model = json.loads((base/'sam.bbmodel').read_text())
        self.profile = json.loads((base/'motion.json').read_text())

    def test_pilot_and_uv_orientation(self):
        result, png = compile_model(self.model, self.profile)
        self.assertEqual(29, len(result['bones']))
        self.assertEqual(416, len(result['quads']))
        for side in ('right', 'left'):
            foot = next(b for b in result['bones'] if b['name'] == 'foot_'+side)
            self.assertEqual('shin_'+side, result['bones'][foot['parent']]['name'])
        self.assertTrue(png.startswith(b'\x89PNG'))
        # Head north: +X is left in the front-face UV, matching Blockbench BoxGeometry.
        head = next(q for q in result['quads'] if q['normal']==[0.,0.,-1.] and q['vertices'][0][:3]==[4.,32.,-4.])
        self.assertEqual([0.,0.], head['vertices'][0][3:])
        self.assertEqual([0.0625,0.0625], head['vertices'][2][3:])
        self.assertTrue(all(b['parent']<i for i,b in enumerate(result['bones'])))

    def test_joint_surface_keeps_animation_texture_and_rigid_parts(self):
        from unittest.mock import patch
        with patch('compile_native_npc.skin_joints', lambda bones, cubes, quads, hip_skin=0: quads):
            rigid, original_png = compile_model(self.model, self.profile)
        smooth, png = compile_model(self.model, self.profile)
        self.assertEqual(original_png, png)
        self.assertEqual({k:v for k,v in rigid.items() if k!='quads'},
                         {k:v for k,v in smooth.items() if k!='quads'})
        self.assertFalse(any(q['sourcePart'].startswith(('elbow_fill_', 'knee_fill_')) for q in smooth['quads']))
        changed = {q['sourcePart'] for q in smooth['quads'] if 'skin' in q}
        changed.update(q['sourcePart'] for q in rigid['quads'] if q['sourcePart'].startswith(('elbow_fill_', 'knee_fill_')))
        self.assertEqual([q for q in rigid['quads'] if q['sourcePart'] not in changed],
                         [q for q in smooth['quads'] if q['sourcePart'] not in changed])
        # The cut face formerly exposed at the elbow must be absent, even at rest.
        self.assertFalse(any(q['sourcePart'] in ('upper_arm_right', 'forearm_right_base')
                             and abs(q['normal'][1])>.9
                             and all(abs(v[1]-18)<1e-5 for v in q['vertices']) for q in smooth['quads']))

    def test_activity_narrowed_limb_seam_is_welded(self):
        base=ROOT/'assets-src/npc/sam'
        model=json.loads((base/'sam_sit.bbmodel').read_text())
        profile=json.loads((base/'motion.json').read_text())
        result,_=compile_model(model,profile,'sam_sit')
        def edge(part):
            return {tuple(round(value,6) for value in v[:3])
                    for q in result['quads'] if q['sourcePart']==part
                    for v in q['vertices'] if abs(v[1]-18)<1e-6}
        self.assertGreaterEqual(len(edge('upper_arm_right')),4)
        self.assertEqual(edge('upper_arm_right'),edge('forearm_right_base'))

    def test_jas_lossless_multi_material_and_inverted_hull(self):
        from npc_texture_atlas import png_data,rgba
        base=ROOT/'assets-src/npc/jas'
        model=json.loads((base/'jas.bbmodel').read_text())
        profile=json.loads((base/'motion.json').read_text())
        result,png=compile_model(model,profile,'jas')
        width,height,rows=rgba(png)
        self.assertEqual((128,256),(width,height))
        top=0
        for texture in model['textures']:
            w,h,source=rgba(png_data(texture))
            self.assertEqual(source,[r[:w*4] for r in rows[top:top+h]])
            top+=h
        hulls=[c for c in model['elements'] if 'inverted_hull' in c['name']]
        self.assertEqual(2,len(hulls))
        for cube in hulls:
            self.assertTrue(all(a>b for a,b in zip(cube['from'],cube['to'])))
            quads=[q for q in result['quads'] if q['sourcePart']==cube['name']]
            self.assertEqual(5,len(quads))
            self.assertTrue(all(q['cull'] and not q['translucent'] for q in quads))
            self.assertTrue(all(v[4]>=.5 for q in quads for v in q['vertices']))
        self.assertFalse(any(q['cull'] for q in result['quads'] if 'inverted_hull' not in q['sourcePart']))

    def test_character_gaits_are_distinct_in_normalized_motion(self):
        signatures=[]
        ids=('jas','vincent','jodi','kent','willy','gus','abigail','alex','caroline','demetrius','elliott','emily','evelyn','haley','harvey','leah','lewis','marnie','maru','mister_qi','penny','pierre','robin','sebastian','shane','wizard')
        for id in ids:
            base=ROOT/'assets-src/npc'/id
            model=json.loads((base/(id+'.bbmodel')).read_text())
            profile=json.loads((base/'motion.json').read_text())
            compiled,_=compile_model(model,profile,id)
            g=profile['gait']
            self.assertGreaterEqual(g['stance'],.5)
            self.assertLess(g['stance'],.7)
            # Ratios/angles remain different even after removing height scaling.
            tracks=compiled['clips']['animation.'+id+'.walk']['tracks']
            lookup={(compiled['bones'][t['bone']]['name'],t['channel']):t for t in tracks}
            signatures.append(tuple(round(k['after'][axis],4)
                for bone,axis in [('body',1),('arm_left',0),('head',0)]
                for k in lookup[bone,'rotation']['keys'][::30]))
            self.assertEqual(2 if profile.get('blinkMode') in ('closed','occluded') else 3,len(compiled['clips']))
            owners={compiled['bones'][t['bone']]['name'] for clip in compiled['clips'].values() for t in clip['tracks']}
            self.assertFalse({'eye_left','eye_right','iris_left','iris_right'} & owners)
        self.assertEqual(len(ids),len(set(signatures)))

    def test_harvey_translucent_lenses_only(self):
        base = ROOT/'assets-src/npc/harvey'
        model = json.loads((base/'harvey.bbmodel').read_text())
        profile = json.loads((base/'motion.json').read_text())
        result, _ = compile_model(model, profile, 'harvey')
        lenses = [q for q in result['quads'] if q['translucent']]
        self.assertEqual(2, len(lenses))
        self.assertTrue(all(q['normal'] == [0., 0., -1.] for q in lenses))
        profile['translucentParts'].append('missing_lens')
        with self.assertRaises(ValueError):
            compile_model(model, profile, 'harvey')

    def test_maru_daily_rig_and_asymmetric_boots(self):
        base = ROOT/'assets-src/npc/maru'
        model = json.loads((base/'maru.bbmodel').read_text())
        profile = json.loads((base/'motion.json').read_text())
        result, _ = compile_model(model, profile, 'maru')
        self.assertEqual({'animation.maru.'+n for n in ('idle','walk','blink')}, set(result['clips']))
        self.assertEqual({'yaw':16,'pitch':7}, profile['lookLimits'])
        self.assertEqual(2.7, profile['attentionRig']['toeDepth'])
        self.assertEqual(0, profile['groundOffset'])
        for name in ('boot_left','boot_right'):
            boot = next(c for c in model['elements'] if c['name']==name+'_foot')
            self.assertEqual((0,-2.7,2.3), (boot['from'][1],boot['from'][2],boot['to'][2]))
        blink = result['clips']['animation.maru.blink']
        self.assertEqual({'lid_left','lid_right'}, {result['bones'][t['bone']]['name'] for t in blink['tracks']})
        for clip in result['clips'].values():
            self.assertFalse({'eye_left','eye_right','hair'} & {result['bones'][t['bone']]['name'] for t in clip['tracks']})

    def test_penny_continuous_skirt_and_fixed_face(self):
        base = ROOT/'assets-src/npc/penny'
        model = json.loads((base/'penny.bbmodel').read_text())
        profile = json.loads((base/'motion.json').read_text())
        result, _ = compile_model(model, profile, 'penny')
        self.assertEqual({'animation.penny.'+n for n in ('idle','walk','blink')}, set(result['clips']))
        owner = next(i for i,b in enumerate(result['bones']) if b['name']=='skirt')
        skirt = [q for q in result['quads'] if q['bone']==owner]
        self.assertEqual({'skirt_core','skirt_front','skirt_back','skirt_left','skirt_right'}, {q['sourcePart'] for q in skirt})
        self.assertGreater(len(skirt), 40)
        self.assertEqual(0, profile['groundOffset'])
        self.assertEqual(2.5, profile['attentionRig']['toeDepth'])
        for clip in result['clips'].values():
            self.assertFalse({'eye_left','eye_right','hair','skirt'} & {result['bones'][t['bone']]['name'] for t in clip['tracks']})

    def test_emily_long_dress_and_fixed_face(self):
        base = ROOT/'assets-src/npc/emily'
        model = json.loads((base/'emily.bbmodel').read_text())
        profile = json.loads((base/'motion.json').read_text())
        result, _ = compile_model(model, profile, 'emily')
        self.assertEqual({'animation.emily.'+n for n in ('idle','walk','blink')}, set(result['clips']))
        owner = next(i for i,b in enumerate(result['bones']) if b['name']=='dress_skirt')
        self.assertEqual({'dress_core','dress_front','dress_back','dress_left','dress_right'},
                         {q['sourcePart'] for q in result['quads'] if q['bone']==owner})
        self.assertEqual('dress_core', profile['cloth']['clearancePart'])
        invalid=copy.deepcopy(profile);invalid['cloth']['contactBlend']=float('nan')
        with self.assertRaises(ValueError):
            compile_model(model,invalid,'emily')
        self.assertLess(profile['cloth']['standingDepthMargin'], profile['cloth']['margin'])
        for clip in result['clips'].values():
            self.assertFalse({'eye_left','eye_right','hair','dress_skirt'} &
                             {result['bones'][t['bone']]['name'] for t in clip['tracks']})

    def test_pierre_lens_pass_and_eye_coverage(self):
        base = ROOT/'assets-src/npc/pierre'
        model = json.loads((base/'pierre.bbmodel').read_text())
        profile = json.loads((base/'motion.json').read_text())
        result, _ = compile_model(model, profile, 'pierre')
        lenses=[q for q in result['quads'] if q['translucent']]
        self.assertEqual({'lens_left','lens_right'}, {q['sourcePart'] for q in lenses})
        self.assertEqual(2,len(lenses))
        self.assertTrue(all(c.get('render_order')=='in_front' for c in model['elements'] if c['name'].startswith('lens_')))
        self.assertTrue(all(not q['translucent'] for q in result['quads'] if q['sourcePart']=='glasses_front'))
        # Eye coverage lies in front of the iris, behind the lens, and reaches the actual eye top.
        for side in ('left','right'):
            lid=next(c for c in model['elements'] if c['name']=='mask_'+side)
            self.assertEqual([25.7,27.7], [lid['from'][1],lid['to'][1]])
        self.assertEqual({'lid_left','lid_right'},
                         {result['bones'][t['bone']]['name'] for t in result['clips']['animation.pierre.blink']['tracks']})
        for clip in result['clips'].values():
            self.assertFalse({'glasses','eye_left','eye_right','hair'} &
                             {result['bones'][t['bone']]['name'] for t in clip['tracks']})

    def test_skirt_clearance_configuration(self):
        base = ROOT/'assets-src/npc/penny'
        model = json.loads((base/'penny.bbmodel').read_text())
        profile = json.loads((base/'motion.json').read_text())
        profile['cloth']['clearancePart']='missing_surface'
        with self.assertRaises(ValueError):
            compile_model(model,profile,'penny')
        profile['cloth']['clearancePart']='skirt_core'
        profile['cloth']['standingDepthMargin']=2
        with self.assertRaises(ValueError):
            compile_model(model,profile,'penny')

    def reject(self):
        with self.assertRaises((ValueError,KeyError)):
            compile_model(self.model,self.profile)

    def test_reject_mesh(self):
        self.model['elements'][0]['type']='mesh';self.reject()

    def test_guitar_production_atlas_and_rig(self):
        model = json.loads((ROOT/'assets-src/npc/sam/sam_guitar.bbmodel').read_text())
        result, png = compile_model(model, self.profile, 'sam_guitar')
        self.assertEqual((256,128), tuple(int.from_bytes(png[i:i+4],'big') for i in (16,20)))
        self.assertEqual({'animation.sam.guitar_play','animation.sam.guitar_hold','animation.sam.idle'},set(result['clips']))
        self.assertTrue({'forearm_left','forearm_right','shin_left','shin_right','foot_left','foot_right',
                         'guitar','music_note_1','music_note_2','music_note_3'} <= {b['name'] for b in result['bones']})
        for quad in result['quads']:
            for v in quad['vertices']:
                self.assertTrue(0 <= v[3] <= 1 and 0 <= v[4] <= 1)
        del model['animations'][0]
        with self.assertRaises(ValueError):
            compile_model(model,self.profile,'sam_guitar')

    def test_sebastian_static_eyes_and_character_binding(self):
        base = ROOT/'assets-src/npc/sebastian'
        model = json.loads((base/'sebastian.bbmodel').read_text())
        profile = json.loads((base/'motion.json').read_text())
        result, _ = compile_model(model,profile,'sebastian')
        self.assertEqual({'animation.sebastian.'+n for n in ('idle','blink','walk')},set(result['clips']))
        fixed = {'eye_left','eye_right','pupil_left','pupil_right','eyelashes_left','eyelashes_right'}
        for clip in result['clips'].values():
            self.assertFalse(fixed & {result['bones'][t['bone']]['name'] for t in clip['tracks']})
        self.assertEqual(13,next(b['origin'][1] for b in result['bones'] if b['name']=='leg_right'))
        profile['attentionRig']['toeDepth'] = 0
        with self.assertRaises(ValueError):
            compile_model(model,profile,'sebastian')

    def test_gameboy_candidate_preserves_eyes_and_native_tracks(self):
        model = json.loads((ROOT/'assets-src/npc/sam/sam_gameboy.bbmodel').read_text())
        result, _ = compile_model(model, self.profile, 'sam_gameboy')
        self.assertEqual({'animation.sam.gameboy_play', 'animation.sam.gameboy_hold', 'animation.sam.idle'}, set(result['clips']))
        names = {b['name'] for b in result['bones']}
        self.assertIn('handheld', names)
        self.assertNotIn('guitar', names)
        self.assertFalse(any(n.startswith('music_note') for n in names))
        for clip in result['clips'].values():
            animated = {result['bones'][t['bone']]['name'] for t in clip['tracks']}
            self.assertFalse(animated & {'eye_left', 'eye_right', 'iris_left', 'iris_right',
                                         'blink_left', 'blink_right'})
        # BB's expression evaluator misreads scientific notation even when Python accepts it.
        for animation in model['animations']:
            for animator in animation['animators'].values():
                for key in animator.get('keyframes', []):
                    for point in key['data_points']:
                        for axis in 'xyz':
                            self.assertNotIn('e', str(point[axis]).lower())
        del model['animations'][0]
        with self.assertRaises(ValueError):
            compile_model(model, self.profile, 'sam_gameboy')

    def test_abigail_painted_face_and_native_clips(self):
        base = ROOT/'assets-src/npc/abigail'
        model = json.loads((base/'abigail.bbmodel').read_text())
        profile = json.loads((base/'motion.json').read_text())
        result, _ = compile_model(model,profile,'abigail')
        self.assertEqual({'animation.abigail.'+n for n in ('idle','blink','walk')},set(result['clips']))
        self.assertEqual(11,next(b['origin'][1] for b in result['bones'] if b['name']=='leg_right'))
        self.assertFalse(any('pupil' in b['name'] or 'iris' in b['name'] for b in result['bones']))
        blink = result['clips']['animation.abigail.blink']
        self.assertEqual({'lid_left','lid_right'},{result['bones'][t['bone']]['name'] for t in blink['tracks']})
        for side in ('left','right'):
            mask = next(e for e in model['elements'] if e['name']=='mask_'+side)
            self.assertEqual([25,27],[mask['from'][1],mask['to'][1]])
            self.assertEqual(2,mask['to'][0]-mask['from'][0])

    def test_robin_rig_keeps_eyes_fixed_and_breath_isolated(self):
        base = ROOT/'assets-src/npc/robin'
        model = json.loads((base/'robin.bbmodel').read_text())
        profile = json.loads((base/'motion.json').read_text())
        result, _ = compile_model(model,profile,'robin')
        self.assertEqual({'animation.robin.'+n for n in ('idle','blink','walk')},set(result['clips']))
        self.assertEqual(11,next(b['origin'][1] for b in result['bones'] if b['name']=='leg_right'))
        fixed = {'eye_left','eye_right','pupil_left','pupil_right','eyelashes_left','eyelashes_right'}
        for clip in result['clips'].values():
            self.assertFalse(fixed & {result['bones'][t['bone']]['name'] for t in clip['tracks']})
        scales = {result['bones'][t['bone']]['name'] for t in result['clips']['animation.robin.idle']['tracks'] if t['channel']=='scale'}
        self.assertEqual({'chest_breath'},scales)

    def test_reject_expression(self):
        animator=next(a for a in self.model['animations'][0]['animators'].values() if a.get('keyframes'))
        animator['keyframes'][0]['data_points'][0]['x']='math.sin(query.anim_time)';self.reject()

    def test_evelyn_keeps_closed_eyes_without_blink(self):
        base=ROOT/'assets-src/npc/evelyn'
        model=json.loads((base/'evelyn.bbmodel').read_text())
        profile=json.loads((base/'motion.json').read_text())
        result,_=compile_model(model,profile,'evelyn')
        self.assertEqual('closed',profile['blinkMode'])
        self.assertEqual({'animation.evelyn.idle','animation.evelyn.walk'},set(result['clips']))
        self.assertFalse(any(b['name'].startswith('lid_') for b in result['bones']))
        for clip in result['clips'].values():
            self.assertFalse(any(result['bones'][t['bone']]['name'].startswith('eye_') for t in clip['tracks']))
        model['animations'].append(dict(name='animation.evelyn.blink',length=.23,loop='once',animators={}))
        with self.assertRaises(ValueError):compile_model(model,profile,'evelyn')

    def test_remaining_characters_visibility_and_garments(self):
        for id in ('haley','elliott','wizard','mister_qi'):
            with self.subTest(id=id):
                base=ROOT/'assets-src/npc'/id
                model=json.loads((base/(id+'.bbmodel')).read_text())
                profile=json.loads((base/'motion.json').read_text())
                result,_=compile_model(model,profile,id)
                names={b['name'] for b in result['bones']}
                expected={'animation.'+id+'.'+n for n in ('idle','walk')}
                if id!='mister_qi': expected.add('animation.'+id+'.blink')
                self.assertEqual(expected,set(result['clips']))
                self.assertGreaterEqual(profile['walkStride'],.65 if profile.get('gait',{}).get('levelWalk') else 1.02)
                if profile.get('gait',{}).get('levelWalk'):
                    self.assertAlmostEqual(profile['walkStride']*16,profile['gait']['stride'],places=6)
                self.assertLessEqual(profile['walkStride'],1.25)
                self.assertGreaterEqual(profile['gait']['reachMargin'],.035 if profile['gait'].get('levelWalk') else .05)
                for clip in result['clips'].values():
                    animated={result['bones'][t['bone']]['name'] for t in clip['tracks']}
                    self.assertFalse({'eye_left','eye_right','pupil_left','pupil_right','eyelashes_left','eyelashes_right'} & animated)
                if id=='mister_qi':
                    self.assertEqual('occluded',profile['blinkMode'])
                    self.assertFalse(any(n.startswith('lid_') for n in names))
                    self.assertEqual(30,len(model['elements']))
                    profile['blinkMode']='typo'
                    with self.assertRaises(ValueError):compile_model(model,profile,id)
                    profile['blinkMode']='occluded'
                    model['animations'].append(dict(name='animation.mister_qi.blink',length=.23,loop='once',animators={}))
                    with self.assertRaises(ValueError):compile_model(model,profile,id)
                elif id=='wizard':
                    for side in ('left','right'):
                        mask=next(e for e in model['elements'] if e['name']=='mask_'+side)
                        self.assertEqual([26,28],[mask['from'][1],mask['to'][1]])
                    walk=result['clips']['animation.wizard.walk']
                    self.assertTrue(any(result['bones'][t['bone']]['name']=='cloth_motion' for t in walk['tracks']))
                    self.assertEqual('cape',profile['cloth']['kind'])
                elif id=='haley':
                    self.assertFalse({'skirt_left','skirt_right'} & names)
                    self.assertEqual(44,len(model['elements']))
                    self.assertEqual('skirt',profile['cloth']['kind'])
                    self.assertEqual('qunzi',profile['cloth']['bone'])
                else:
                    # The static waist-spanning garments must no longer bridge both swinging thighs.
                    self.assertGreater(sum(1 for q in result['quads'] if result['bones'][q['bone']]['name']=='leg_left'),12)
                    self.assertGreater(sum(1 for q in result['quads'] if result['bones'][q['bone']]['name']=='leg_right'),12)

    def test_garment_tessellation_preserves_surface_and_uv(self):
        import math
        def area(q,axes):
            v=q['vertices'];total=0
            for a,b,c in ((v[0],v[1],v[2]),(v[0],v[2],v[3])):
                u=[b[i]-a[i] for i in axes];w=[c[i]-a[i] for i in axes]
                total+=(abs(u[0]*w[1]-u[1]*w[0]) if len(axes)==2 else math.sqrt(sum(x*x for x in (u[1]*w[2]-u[2]*w[1],u[2]*w[0]-u[0]*w[2],u[0]*w[1]-u[1]*w[0]))))/2
            return total
        for id in ('haley','wizard'):
            base=ROOT/'assets-src/npc'/id
            model=json.loads((base/(id+'.bbmodel')).read_text());profile=json.loads((base/'motion.json').read_text())
            split,_=compile_model(model,profile,id)
            owner=profile.pop('cloth')['bone'];plain,_=compile_model(model,profile,id)
            qs=lambda m:[q for q in m['quads'] if m['bones'][q['bone']]['name']==owner]
            self.assertGreater(len(qs(split)),len(qs(plain)))
            for axes in ((0,1,2),(3,4)):
                self.assertAlmostEqual(sum(area(q,axes) for q in qs(plain)),sum(area(q,axes) for q in qs(split)),places=5)

    def test_shane_exposed_knee_material_and_short_hem(self):
        model=json.loads((ROOT/'assets-src/npc/shane/shane.bbmodel').read_text())
        # The knee lies in the bare-skin rows, below the short hem. Joint caps
        # must use those rows too, not the blue hip cap copied during splitting.
        for c in model['elements']:
            if c['name'].startswith('knee_fill_'):
                for face in c['faces'].values():
                    self.assertGreaterEqual(min(face['uv'][1],face['uv'][3]),41)
                    self.assertLessEqual(max(face['uv'][1],face['uv'][3]),43)
            if c['name'].startswith('shorts_'):
                self.assertGreater(c['from'][1],7)
                self.assertLessEqual(c['to'][1],9.15)
                self.assertIsNone(c['faces']['up']['texture'])
                self.assertIsNone(c['faces']['down']['texture'])

    def test_approved_alex_shane_contact_and_eyelids(self):
        for id,height in (('alex',2),('shane',1)):
            base=ROOT/'assets-src/npc'/id
            model=json.loads((base/(id+'.bbmodel')).read_text());profile=json.loads((base/'motion.json').read_text())
            result,_=compile_model(model,profile,id)
            bones=result['bones'];by={b['name']:i for i,b in enumerate(bones)}
            self.assertEqual(12,profile['attentionRig']['hipHeight'])
            self.assertEqual(0,profile['groundOffset'])
            for side in ('right','left'):
                leg=by['leg_'+side];detail=by['leg_detail_'+side]
                self.assertEqual(leg,bones[detail]['parent'])
                contact=[v for q in result['quads'] if q['bone']==by['foot_'+side] for v in q['vertices']]
                self.assertEqual(0,min(v[1] for v in contact))
                self.assertEqual(2,max(abs(v[2]) for v in contact))
                # Raised transparent folds cannot manufacture a lower shoe-contact plane.
                self.assertTrue(any(q['bone']==detail for q in result['quads']))
                mask=next(e for e in model['elements'] if e['name']=='mask_'+side)
                self.assertEqual(height,mask['to'][1]-mask['from'][1])
                self.assertEqual(27,mask['to'][1])
            for clip in result['clips'].values():
                moving={bones[t['bone']]['name'] for t in clip['tracks']}
                self.assertFalse({'eye_left','eye_right','iris_left','iris_right'} & moving)
            # Shane's hood must stay outside chest scaling, preventing expansion into the head.
            if id=='shane':
                hood=next(e for e in model['elements'] if e['name']=='hood')['uuid']
                body=next(g['uuid'] for g in model['groups'] if g['name']=='body')
                def group(node):
                    if isinstance(node,dict):
                        if node['uuid']==body:return node
                        for child in node.get('children',[]):
                            found=group(child)
                            if found:return found
                self.assertIn(hood,group(model['outliner'][0])['children'])

    def test_reject_dangling_bone(self):
        self.model['outliner'][0]['uuid']='missing';self.reject()

    def test_reject_atlas_overflow(self):
        self.model['elements'][0]['faces']['north']['uv'][2]=500;self.reject()

    def test_reject_new_format(self):
        self.model['meta']['format_version']='6.0';self.reject()

    def test_reject_unsupported_curve(self):
        animator=next(a for a in self.model['animations'][0]['animators'].values() if a.get('keyframes'))
        animator['keyframes'][0]['interpolation']='bezier';self.reject()

    def test_reject_singular_scale(self):
        animator=next(a for a in self.model['animations'][0]['animators'].values() if a.get('keyframes'))
        animator['keyframes'][0]['data_points'][0]['x']=0;self.reject()

if __name__=='__main__': unittest.main()
