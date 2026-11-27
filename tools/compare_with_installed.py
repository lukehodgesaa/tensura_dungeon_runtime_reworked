import sys, zipfile, glob, os, struct
fused = zipfile.ZipFile(sys.argv[1]); F = {i.filename: i for i in fused.infolist()}
IGN = {'META-INF/MANIFEST.MF', 'META-INF/neoforge.mods.toml', 'pack.mcmeta'}
def members(data):
    p = 10; cp = [None]
    n = struct.unpack('>H', data[8:10])[0]; i = 1
    while i < n:
        t = data[p]; p += 1
        if t == 1:
            l = struct.unpack('>H', data[p:p+2])[0]; cp.append(data[p+2:p+2+l].decode('utf-8', 'replace')); p += 2 + l
        elif t in (3, 4): cp.append(None); p += 4
        elif t in (5, 6): cp += [None, None]; p += 8; i += 1
        elif t in (7, 8, 16, 19, 20): cp.append(None); p += 2
        elif t == 15: cp.append(None); p += 3
        else: cp.append(None); p += 4
        i += 1
    p += 6; ic = struct.unpack('>H', data[p:p+2])[0]; p += 2 + 2 * ic
    out = set()
    for kind in ('F', 'M'):
        c = struct.unpack('>H', data[p:p+2])[0]; p += 2
        for _ in range(c):
            _, ni, di, ac = struct.unpack('>HHHH', data[p:p+8]); p += 8
            if not cp[ni].startswith('lambda$'): out.add((kind, cp[ni], cp[di]))
            for _ in range(ac):
                l = struct.unpack('>I', data[p+2:p+6])[0]; p += 6 + l
    return out
for jar in sorted(glob.glob(os.path.join(sys.argv[2], '*.jar'))):
    z = zipfile.ZipFile(jar); missing = []; resdiff = []; memdiff = []
    for e in z.infolist():
        n = e.filename
        if n.endswith('/') or n in IGN: continue
        if n not in F: missing.append(n); continue
        a, b = z.read(n), fused.read(n)
        if a == b: continue
        if n.endswith('.class'):
            ma, mb = members(a), members(b)
            if ma != mb: memdiff.append((n, sorted(ma - mb)[:4], sorted(mb - ma)[:4]))
        else: resdiff.append(n)
    print(f'== {os.path.basename(jar)}: missing={len(missing)} resource-diff={len(resdiff)} class-member-diff={len(memdiff)}')
    for m in missing[:15]: print('   MISSING', m)
    for r in resdiff[:10]: print('   RESDIFF', r)
    for n, x, y in memdiff[:15]: print('   MEMBERS', n, 'installed-only', x, 'source-only', y)
