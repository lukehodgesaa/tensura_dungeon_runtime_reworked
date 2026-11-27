import gzip, zlib, struct, sys, io, json

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BARR, STR, LIST, COMP, IARR, LARR = range(13)


class R:
    def __init__(s, b): s.b = b; s.p = 0
    def take(s, n): v = s.b[s.p:s.p + n]; s.p += n; return v
    def u(s, f): n = struct.calcsize(f); return struct.unpack('>' + f, s.take(n))[0]


def read_payload(r, t):
    if t == BYTE: return r.u('b')
    if t == SHORT: return r.u('h')
    if t == INT: return r.u('i')
    if t == LONG: return r.u('q')
    if t == FLOAT: return r.u('f')
    if t == DOUBLE: return r.u('d')
    if t == BARR: n = r.u('i'); return ('B', list(r.take(n)))
    if t == STR: n = r.u('H'); return r.take(n).decode('utf-8', 'replace')
    if t == LIST:
        et = r.u('b'); n = r.u('i'); return ('L', et, [read_payload(r, et) for _ in range(n)])
    if t == COMP:
        d = {}
        while True:
            tt = r.u('b')
            if tt == END: return d
            name = r.take(r.u('H')).decode('utf-8', 'replace')
            d[name] = (tt, read_payload(r, tt))
    if t == IARR: n = r.u('i'); return ('I', [r.u('i') for _ in range(n)])
    if t == LARR: n = r.u('i'); return ('J', [r.u('q') for _ in range(n)])
    raise ValueError(t)


def parse(b):
    r = R(b); t = r.u('b'); r.take(r.u('H')); return read_payload(r, t)


def load(path):
    b = open(path, 'rb').read()
    try: b = gzip.decompress(b)
    except OSError: pass
    return parse(b)


def plain(v, t=COMP):
    if t == COMP: return {k: plain(vv, tt) for k, (tt, vv) in v.items()}
    if t == LIST: return [plain(x, v[1]) for x in v[2]]
    if t in (BARR, IARR, LARR): return v[1]
    return v


def region_chunks(path):
    data = open(path, 'rb').read()
    for i in range(1024):
        off = int.from_bytes(data[i * 4:i * 4 + 3], 'big'); cnt = data[i * 4 + 3]
        if off == 0: continue
        o = off * 4096; ln = struct.unpack('>i', data[o:o + 4])[0]; comp = data[o + 4]
        raw = data[o + 5:o + 4 + ln]
        if comp == 2: raw = zlib.decompress(raw)
        elif comp == 1: raw = gzip.decompress(raw)
        elif comp == 3: pass
        else: continue
        yield i % 32, i // 32, parse(raw)


if __name__ == '__main__':
    if sys.argv[1] == 'dump':
        d = plain(load(sys.argv[2]))
        s = json.dumps(d, indent=1, default=str)
        print(s[: int(sys.argv[3]) if len(sys.argv) > 3 else 20000])


def _w_payload(out, t, v):
    if t == BYTE: out += struct.pack('>b', v)
    elif t == SHORT: out += struct.pack('>h', v)
    elif t == INT: out += struct.pack('>i', v)
    elif t == LONG: out += struct.pack('>q', v)
    elif t == FLOAT: out += struct.pack('>f', v)
    elif t == DOUBLE: out += struct.pack('>d', v)
    elif t == BARR: out += struct.pack('>i', len(v[1])); out += bytes(x & 0xFF for x in v[1])
    elif t == STR: b = v.encode('utf-8'); out += struct.pack('>H', len(b)); out += b
    elif t == LIST:
        et, items = v[1], v[2]
        if not items: et = END if et is None else et
        out += struct.pack('>bi', et, len(items))
        for it in items: _w_payload(out, et, it)
    elif t == COMP:
        for k, (tt, vv) in v.items():
            kb = k.encode('utf-8'); out += struct.pack('>bH', tt, len(kb)); out += kb; _w_payload(out, tt, vv)
        out += b'\x00'
    elif t == IARR: out += struct.pack('>i', len(v[1])); out += struct.pack('>%di' % len(v[1]), *v[1])
    elif t == LARR: out += struct.pack('>i', len(v[1])); out += struct.pack('>%dq' % len(v[1]), *v[1])
    else: raise ValueError(t)


def dump_bytes(root):
    out = bytearray(); out += b'\x0a\x00\x00'; _w_payload(out, COMP, root); return bytes(out)


def save_gz(path, root):
    with open(path, 'wb') as f: f.write(gzip.compress(dump_bytes(root), 6))


def C(**kw): return dict(kw)
def tI(v): return (INT, v)
def tS(v): return (STR, v)
def tD(v): return (DOUBLE, v)
def tL(et, items): return (LIST, ('L', et, items))
def tC(d): return (COMP, d)
