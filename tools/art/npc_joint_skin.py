"""Turn matching cuboid limb cuts into a continuous, weighted joint surface.

The editor's rigid segments, UVs and animation channels remain the authoring source.
Only the small bridge around a real shared cut is tessellated; hands, soles and props
retain their original rigid transforms. No rounded filler cuboids are rendered.
"""
def skin_joints(bones, cubes, quads, hip_skin=0):
    names = {b['name']: i for i, b in enumerate(bones)}
    primary = {names[n] for side in ('left', 'right')
               for n in (f'arm_{side}', f'forearm_{side}', f'leg_{side}',
                         f'shin_{side}', f'foot_{side}') if n in names}

    def main(owner):
        while owner >= 0:
            if owner in primary:
                return owner
            # Only neutral detail groups share a limb's transform.
            if any(bones[owner]['rotation']):
                return -1
            owner = bones[owner]['parent']
        return -1

    def ancestor(child, parent):
        while child >= 0:
            if child == parent:
                return True
            child = bones[child]['parent']
        return False

    entries = []
    for owner, c in cubes:
        part = main(owner)
        if part < 0 or any(c.get('rotation', [])) or 'fill_' in c['name']:
            continue
        entries.append((part, c))
    bindings, joints = {}, {}
    for upper, a in entries:
        for lower, b in entries:
            if upper == lower or not ancestor(lower, upper):
                continue
            y = a['from'][1]
            if abs(y-b['to'][1]) > 1e-5:
                continue
            if a['to'][1]-y <= 1e-5 or y-b['from'][1] <= 1e-5:
                continue
            ia, ib = a.get('inflate', 0), b.get('inflate', 0)
            if any(abs(a[end][axis]+sign*ia-b[end][axis]-sign*ib) > .04
                   for axis in (0, 2) for end, sign in [('from', -1), ('to', 1)]):
                continue
            key = (upper, lower, y)
            width = min(2.5, (a['to'][1]-y)*.45, (y-b['from'][1])*.45)
            joints[key] = min(joints.get(key, width), width)
            ring = tuple((a[end][axis]+sign*ia+b[end][axis]+sign*ib)/2
                         for axis in (0, 2) for end, sign in [('from', -1), ('to', 1)])
            for c, role in ((a, 'upper'), (b, 'lower')):
                binding = (key, role, ring)
                if binding not in bindings.setdefault(c['name'], []):
                    bindings[c['name']].append(binding)

    fillers = set()
    for upper, lower, _ in joints:
        side = bones[lower]['name'].rsplit('_', 1)[-1]
        if bones[upper]['name'].startswith('arm_'):
            fillers.add('elbow_fill_'+side)
        elif bones[upper]['name'].startswith('leg_'):
            fillers.add('knee_fill_'+side)

    def slice_y(q, low, high):
        vs = q['vertices']
        minimum, maximum = min(v[1] for v in vs), max(v[1] for v in vs)
        if maximum-minimum < 1e-8:
            return [v[:] for v in vs]
        out = []
        for i, v in enumerate(vs):
            target = low if abs(v[1]-minimum) < 1e-6 else high
            other = next(vs[j] for j in ((i+1) % 4, (i-1) % 4)
                         if abs(vs[j][1]-v[1]) > 1e-6)
            t = (target-v[1])/(other[1]-v[1])
            out.append([v[k]+t*(other[k]-v[k]) for k in range(5)])
        return out

    waist = {}
    body = names.get('body', -1)
    if body >= 0 and bones[body]['parent'] >= 0:
        y = bones[body]['origin'][1]
        for owner, c in cubes:
            if bones[owner]['name'] not in ('body', 'chest_breath') or any(c.get('rotation', [])):
                continue
            # Long hanging panels already belong to the garment solver, not the waist.
            if y-.5 <= c['from'][1] <= y+.5 and c['to'][1] >= y+4:
                waist[c['name']] = (bones[body]['parent'], owner, y, y+min(3., (c['to'][1]-y)*.3))

    output = []
    for q in quads:
        if q['sourcePart'] in fillers:
            continue
        active = bindings.get(q['sourcePart'], [])
        if not active:
            if q['sourcePart'] in waist:
                upper, lower, start, end = waist[q['sourcePart']]
                low, high = min(v[1] for v in q['vertices']), max(v[1] for v in q['vertices'])
                cuts = sorted({low, high} | {start+(end-start)*i/8 for i in range(9)
                                            if low < start+(end-start)*i/8 < high})
                spans = list(zip(cuts, cuts[1:])) if high-low > 1e-8 else [(low, high)]
                for a, b in spans:
                    part = dict(q, vertices=slice_y(q, a, b))
                    if a < end:
                        part['skin'] = dict(upper=upper, lower=lower,
                                            weights=[max(0., min(1., (v[1]-start)/(end-start)))
                                                     for v in part['vertices']])
                    output.append(part)
                continue
            output.append(q)
            continue
        low, high = min(v[1] for v in q['vertices']), max(v[1] for v in q['vertices'])
        horizontal = high-low < 1e-8
        internal = False
        for key, role, ring in active:
            y = key[2]
            if horizontal and ((role == 'upper' and q['normal'][1] < -.9 and low <= y+.5)
                               or (role == 'lower' and q['normal'][1] > .9 and high >= y-.5)):
                internal = True
            if not horizontal:
                if role == 'upper':
                    low = max(low, y)
                else:
                    high = min(high, y)
        if internal or high < low:
            continue
        cuts = {low, high}
        for key, role, ring in active:
            y, width = key[2], joints[key]
            # Eight strips across a complete joint; subdivisions do not add texels.
            cuts.update(y+width*i/4 for i in range(-4, 5) if low < y+width*i/4 < high)
        rows = sorted(cuts)
        spans = [(low, high)] if horizontal else list(zip(rows, rows[1:]))
        for a, b in spans:
            vs = slice_y(q, a, b)
            part = dict(q, vertices=vs)
            for key, role, ring in active:
                upper, lower, y = key
                width = joints[key]
                if b < y-width-1e-6 or a > y+width+1e-6:
                    continue
                weights = [max(0., min(1., (y+width-v[1])/(2*width))) for v in vs]
                for v in vs:
                    blend = max(0., 1-abs(v[1]-y)/width)
                    for axis, offset in [(0, 0), (2, 2)]:
                        target = min(ring[offset:offset+2], key=lambda value: abs(value-v[axis]))
                        # Heal the old 0.018-unit seam workaround only within the bridge.
                        if abs(target-v[axis]) <= .021:
                            v[axis] += (target-v[axis])*blend
                part['skin'] = dict(upper=upper, lower=lower, weights=weights)
                break
            output.append(part)
    if not hip_skin:
        return output
    root=names['root']
    hips={names['leg_'+side]:bones[names['leg_'+side]]['origin'][1] for side in ('left','right')}
    bound=[]
    for q in output:
        owner=main(q['bone'])
        # Rigid rows above a knee bridge carry an all-zero binding; those
        # rows still need the independent pelvis-to-thigh bridge.
        if owner not in hips or (q.get('skin') and any(q['skin']['weights'])):
            bound.append(q)
            continue
        y=hips[owner]
        low,high=min(v[1] for v in q['vertices']),max(v[1] for v in q['vertices'])
        if high<=y-hip_skin:
            bound.append(q)
            continue
        cuts=sorted({low,high} | {y-hip_skin+i*hip_skin/8 for i in range(9) if low<y-hip_skin+i*hip_skin/8<high})
        spans=list(zip(cuts,cuts[1:])) if high-low>1e-8 else [(low,high)]
        for a,b in spans:
            part=dict(q,vertices=slice_y(q,a,b))
            if b>y-hip_skin:
                # A planted-foot turn may translate the thigh. Pants stay sewn
                # to the pelvis and blend into that motion below their top edge.
                part['skin']=dict(upper=root,lower=owner,
                    weights=[max(0.,min(1.,(y-v[1])/hip_skin)) for v in part['vertices']])
            bound.append(part)
    return bound
