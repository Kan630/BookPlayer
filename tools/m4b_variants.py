#!/usr/bin/env python3
"""Builds modified copies of real M4B files to test M4bSplitter's fallback and refusals.

  broken_vide   : the cover 'vide' track loses its stco (renamed 'free', sizes kept) -> mp4parser NPE
  chpl_only     : tref/chap -> 'free', text track handler -> 'meta', a Nero chpl added in moov/udta
                  (chunk offsets shifted when moov precedes mdat)
  bad_chapters  : chapter track durations x10 (timeline far past the audio end)
  uint64        : trailing top-level box with a 64-bit size > Long.MAX_VALUE

Inputs of M4bSplitterDeviceTest / M4bSplitWorkerDeviceTest (androidTest services/m4b), from the fixtures on the test
phones' SD card (/storage/<sd>/fixtures/m4b/):
  frost.m4b  = FrostTonight_librivox.m4b          anthem.m4b = Anthem by Ayn Rand .m4b
  elements.m4b = Elements of Style by William Strunk Jr 64kbps.m4b
  piper.m4b  = John Piper - When the Darkness Will Not Lift.m4b
  python3 tools/m4b_variants.py elements.m4b broken_vide elements_broken_vide.m4b
  python3 tools/m4b_variants.py frost.m4b chpl_only frost_chpl_only.m4b
  python3 tools/m4b_variants.py frost.m4b bad_chapters frost_bad_chapters.m4b
  python3 tools/m4b_variants.py anthem.m4b uint64 anthem_uint64.m4b
  adb push <all 8 files> /data/local/tmp/bp_m4b/
"""
import struct, sys

CONT = {b'moov', b'trak', b'mdia', b'minf', b'stbl', b'udta', b'edts', b'dinf', b'tref'}


def boxes(d, start, end):
    out, p = [], start
    while p + 8 <= end:
        size, typ = struct.unpack('>I4s', d[p:p + 8])
        hdr = 8
        if size == 1:
            size = struct.unpack('>Q', d[p + 8:p + 16])[0]
            hdr = 16
        elif size == 0:
            size = end - p
        out.append((typ, p, p + hdr, p + size))
        p += size
    return out


def find(lst, t):
    return [b for b in lst if b[0] == t]


def kids(d, b):
    return boxes(d, b[2], b[3])


def traks(d):
    moov = find(boxes(d, 0, len(d)), b'moov')[0]
    res = []
    for tr in find(kids(d, moov), b'trak'):
        k = kids(d, tr)
        mdia = find(k, b'mdia')[0]
        mk = kids(d, mdia)
        hdlr = find(mk, b'hdlr')[0]
        handler = d[hdlr[2] + 8:hdlr[2] + 12]
        minf = find(mk, b'minf')[0]
        stbl = find(kids(d, minf), b'stbl')[0]
        res.append(dict(trak=tr, kids=k, mdia=mdia, mk=mk, hdlr=hdlr, handler=handler, stbl=stbl,
                        st=kids(d, stbl)))
    return moov, res


def broken_vide(d):
    d = bytearray(d)
    _, ts = traks(d)
    v = [t for t in ts if t['handler'] == b'vide']
    assert v, 'no vide track'
    for t in v:
        for b in t['st']:
            if b[0] in (b'stco', b'co64'):
                d[b[1] + 4:b[1] + 8] = b'free'
    return bytes(d)


def chapters(d):
    _, ts = traks(d)
    t = [t for t in ts if t['handler'] in (b'text', b'sbtl')][0]
    mdhd = find(t['mk'], b'mdhd')[0]
    v = d[mdhd[2]]
    timescale = struct.unpack('>I', d[mdhd[2] + (20 if v == 1 else 12):][:4])[0]
    st = {b[0]: b for b in t['st']}
    stts = st[b'stts']
    n = struct.unpack('>I', d[stts[2] + 4:stts[2] + 8])[0]
    durs = []
    for i in range(n):
        c, dl = struct.unpack('>II', d[stts[2] + 8 + i * 8:stts[2] + 16 + i * 8])
        durs += [dl] * c
    stsz = st[b'stsz']
    const, count = struct.unpack('>II', d[stsz[2] + 4:stsz[2] + 12])
    sizes = [const] * count if const else list(struct.unpack('>%dI' % count, d[stsz[2] + 12:stsz[2] + 12 + 4 * count]))
    stco = st[b'stco']
    cc = struct.unpack('>I', d[stco[2] + 4:stco[2] + 8])[0]
    chunks = struct.unpack('>%dI' % cc, d[stco[2] + 8:stco[2] + 8 + 4 * cc])
    stsc = st[b'stsc']
    ec = struct.unpack('>I', d[stsc[2] + 4:stsc[2] + 8])[0]
    ent = [struct.unpack('>III', d[stsc[2] + 8 + i * 12:stsc[2] + 20 + i * 12]) for i in range(ec)]
    offs, s = [], 0
    for ci, co in enumerate(chunks, 1):
        per = [e[1] for e in ent if e[0] <= ci][-1]
        for _ in range(per):
            if s < count:
                offs.append(co)
                co += sizes[s]
                s += 1
    titles = []
    for o, sz in zip(offs, sizes):
        ln = struct.unpack('>H', d[o:o + 2])[0]
        titles.append(d[o + 2:o + 2 + ln].decode('utf-8', 'replace'))
    starts, t0 = [], 0
    for dl in durs[:count]:
        starts.append(t0 * 1000 // timescale)
        t0 += dl
    return titles, starts


def chpl_only(d):
    titles, starts = chapters(d)
    d = bytearray(d)
    moov, ts = traks(d)
    for t in ts:
        for b in t['kids']:
            if b[0] == b'tref':
                for c in boxes(d, b[2], b[3]):
                    if c[0] == b'chap':
                        d[c[1] + 4:c[1] + 8] = b'free'
        if t['handler'] in (b'text', b'sbtl'):
            d[t['hdlr'][2] + 8:t['hdlr'][2] + 12] = b'meta'
    body = bytes([1, 0, 0, 0, 0, 0, 0, 0, len(titles)])
    for ti, st in zip(titles, starts):
        tb = ti.encode('utf-8')[:255]
        body += struct.pack('>Q', st * 10000) + bytes([len(tb)]) + tb
    chpl = struct.pack('>I', 8 + len(body)) + b'chpl' + body
    delta = len(chpl)
    moov = find(boxes(d, 0, len(d)), b'moov')[0]
    udta = find(kids(d, moov), b'udta')
    mdat = find(boxes(d, 0, len(d)), b'mdat')[0]
    shift = moov[1] < mdat[1]
    if shift:  # every chunk offset moves by delta
        _, ts = traks(d)
        for t in ts:
            for b in t['st']:
                if b[0] == b'stco':
                    n = struct.unpack('>I', d[b[2] + 4:b[2] + 8])[0]
                    for i in range(n):
                        p = b[2] + 8 + i * 4
                        d[p:p + 4] = struct.pack('>I', struct.unpack('>I', d[p:p + 4])[0] + delta)
                elif b[0] == b'co64':
                    n = struct.unpack('>I', d[b[2] + 4:b[2] + 8])[0]
                    for i in range(n):
                        p = b[2] + 8 + i * 8
                        d[p:p + 8] = struct.pack('>Q', struct.unpack('>Q', d[p:p + 8])[0] + delta)
    if udta:
        u = udta[0]
        d[u[1]:u[1] + 4] = struct.pack('>I', struct.unpack('>I', d[u[1]:u[1] + 4])[0] + delta)
        insert_at = u[2]
    else:
        chpl = struct.pack('>I', 8 + len(chpl)) + b'udta' + chpl
        delta = len(chpl)
        insert_at = moov[3]
        assert not shift, 'no udta and moov before mdat: not handled'
    d[moov[1]:moov[1] + 4] = struct.pack('>I', struct.unpack('>I', d[moov[1]:moov[1] + 4])[0] + delta)
    d[insert_at:insert_at] = chpl
    return bytes(d), titles, starts


def bad_chapters(d):
    d = bytearray(d)
    _, ts = traks(d)
    t = [t for t in ts if t['handler'] in (b'text', b'sbtl')][0]
    stts = [b for b in t['st'] if b[0] == b'stts'][0]
    n = struct.unpack('>I', d[stts[2] + 4:stts[2] + 8])[0]
    for i in range(n):
        p = stts[2] + 8 + i * 8 + 4
        d[p:p + 4] = struct.pack('>I', struct.unpack('>I', d[p:p + 4])[0] * 10)
    return bytes(d)


def uint64(d):
    return d + struct.pack('>I4sQ', 1, b'free', 0xFFFFFFFFFFFFFFF0)


if __name__ == '__main__':
    src, kind, dst = sys.argv[1], sys.argv[2], sys.argv[3]
    d = open(src, 'rb').read()
    if kind == 'chpl_only':
        out, titles, starts = chpl_only(d)
        print(len(titles), 'chapters ->', list(zip(starts, titles))[:3])
    else:
        out = {'broken_vide': broken_vide, 'bad_chapters': bad_chapters, 'uint64': uint64}[kind](d)
    open(dst, 'wb').write(out)
    print(kind, 'written', dst, len(out))
