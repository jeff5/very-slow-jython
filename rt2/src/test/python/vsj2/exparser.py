# A parser for a file of Python code fragments intended as tests

import re


class LineToken:
    """A token representing one line from the examples file"""
    def __init__(self, text):
        self.text = text
        self.arg = None
        self._classify()

    EOF = "EOF"
    CODE = "CODE"
    HEAD = "HEAD"
    TEST = "TEST"

    def __repr__(self):
        return f"[{self.kind}, {self.arg}, {self.text!r}]"

    HEAD_PATTERN = re.compile(r"#\s*(\w+):")
    TEST_PATTERN = re.compile(r"#\s*\?\s*((\w+,?\s*)*)")

    def _classify(self):
        def as_tuple(names):
            # Comma-separated names: split into a tuple discarding ''.
            return tuple(n for n in map(str.strip, names.split(',')) if n)
        if self.text is None:
            self.kind = LineToken.EOF
        elif m := self.HEAD_PATTERN.match(self.text):
            self.kind = LineToken.HEAD
            self.arg = m.group(1)
        elif m := self.TEST_PATTERN.match(self.text):
            self.kind = LineToken.TEST
            self.arg = as_tuple(m.group(1))
        else:
            self.kind = LineToken.CODE

class Test:
    """A test derived by parsing the examples file."""
    def __init__(self, name):
        self.name = name
        self.cases = []
        self.test = ()
        self.body = []

    def __repr__(self):
        name = "# " + self.name + ":"
        cases = "\n".join(self.cases)
        test = "# ? " + ", ".join(self.test)
        body = "\n".join(self.body)
        return "\n".join((name, cases, test, body))


class Lines:
    """A structure to hold and classify a line read from a stream."""
    def __init__(self, f):
        self.f = f
        self.line = LineToken(None)
        self.consume()

    def __repr__(self):
        return f"{self.kind():2d} {self.line!r}"

    def consume(self):
        """Return the current line, and advance to and classify the next."""
        #print("    consume() =", repr(self.line))
        prev = self.line
        if line := self.f.readline():
            self.line = LineToken(line.rstrip())
        else:
            self.line = LineToken(None)
        return prev

    def kind(self):
        return self.line.kind

    def close(self):
        self.f.close()

    def expect(self, kind):
        """Check that the current line is of the expected kind."""
        if self.kind() != kind:
            raise SyntaxError("Expected: {} got {}".format(
                kind, self.line))

    def parse_preamble(self):
        """Ignore lines until positioned at the head of the first test."""
        while self.kind() == LineToken.CODE:
            self.consume()

    def parse_test(self):
        """Return the next Test object from the examples file."""
        name = self.parse_head()
        t = Test(name)
        while self.kind() == LineToken.CODE:
            t.cases.append(self.consume().text)
        t.test = self.parse_query()
        t.body = self.parse_body()
        return t

    def parse_head(self):
        """Parse a HEAD line returning the test name."""
        self.expect(LineToken.HEAD)
        line = self.consume()
        return line.arg

    def parse_case(self):
        """Return a sequence of cases (single line of code each)."""
        # A case is a code fragment that assigns initial values
        self.expect(LineToken.CODE)
        cases = []
        cases.append(self.consume())
        return cases

    def parse_query(self):
        """Parse a TEST line defining what outputs shall be tested."""
        self.expect(LineToken.TEST)
        query = self.consume()
        return query.arg

    def parse_body(self):
        """Return a sequence of lines assumed to be a Python code fragment."""
        body = []
        while self.kind() == LineToken.CODE:
            body.append(self.consume().text)
        return body
