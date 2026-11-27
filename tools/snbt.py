import re

NUMBER = re.compile(r'[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?[bBsSlLfFdD]?')
BARE = re.compile(r'[A-Za-z0-9_\-.+]+')


class Parser:
    def __init__(self, text):
        self.s = text
        self.i = 0

    def ws(self):
        while self.i < len(self.s) and self.s[self.i] in ' \t\r\n,':
            self.i += 1

    def peek(self):
        self.ws()
        return self.s[self.i] if self.i < len(self.s) else ''

    def value(self):
        c = self.peek()
        if c == '{':
            return self.compound()
        if c == '[':
            return self.list()
        if c in '"\'':
            return self.string()
        m = NUMBER.match(self.s, self.i)
        if m and (m.end() >= len(self.s) or not (self.s[m.end()].isalnum() or self.s[m.end()] == '_')):
            self.i = m.end()
            raw = m.group(0)
            if raw[-1] in 'bBsSlLfFdD':
                kind = raw[-1].lower()
                raw = raw[:-1]
                return float(raw) if kind in 'fd' else int(raw)
            return float(raw) if any(ch in raw for ch in '.eE') else int(raw)
        m = BARE.match(self.s, self.i)
        if not m:
            raise ValueError('bad value at %d: %r' % (self.i, self.s[self.i:self.i + 40]))
        self.i = m.end()
        word = m.group(0)
        return {'true': True, 'false': False}.get(word, word)

    def string(self):
        quote = self.s[self.i]
        self.i += 1
        out = []
        while self.s[self.i] != quote:
            ch = self.s[self.i]
            if ch == chr(92):
                nxt = self.s[self.i + 1]
                out.append({'n': '\n', 't': '\t', 'u': None}.get(nxt, nxt))
                if nxt == 'u':
                    out[-1] = chr(int(self.s[self.i + 2:self.i + 6], 16))
                    self.i += 4
                self.i += 2
                continue
            out.append(ch)
            self.i += 1
        self.i += 1
        return ''.join(out)

    def key(self):
        if self.peek() in '"\'':
            return self.string()
        m = BARE.match(self.s, self.i)
        self.i = m.end()
        return m.group(0)

    def compound(self):
        self.i += 1
        out = {}
        while self.peek() != '}':
            k = self.key()
            self.ws()
            assert self.s[self.i] == ':', (self.i, self.s[self.i - 30:self.i + 30])
            self.i += 1
            out[k] = self.value()
        self.i += 1
        return out

    def list(self):
        self.i += 1
        if re.match(r'[BIL];', self.s[self.i:self.i + 2]):
            self.i += 2
        out = []
        while self.peek() != ']':
            out.append(self.value())
        self.i += 1
        return out


def parse(text):
    return Parser(text.lstrip('﻿')).value()


def load(path):
    with open(path, encoding='utf-8-sig') as fh:
        return parse(fh.read())
