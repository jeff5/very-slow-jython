# Generate examples for the bytecode interpreter - evo3

import os
import re
from contextlib import closing
from vsj2.exparser import LineToken, Lines
from vsj2.srcgen import PyObjectTestEmitter, PyObjectEmitter, \
    PyObjectEmitterEvo3, PyObjectTestEmitterEvo3, PyObjectTestEmitterEvo4, \
    PyObjectEmitterEvo4


def generate(test, testType=None, writer=None):
    """Generate Java code to test one program example"""
    if testType is None:
        testType = PyObjectTestEmitter
    # Generate the text of a test based on this example
    with closing(testType(test, writer)) as e:
        e.emit_test_material()
        e.emit_test_cases()
        e.emit_line("")


def main(filename):
    # Derive the examples file name from one passed in
    dirname, basename = os.path.split(filename)
    m = re.search(r"(py_byte_code\d+)_evo(\d+)\.py", basename)
    name = "{}.ex.py".format(m.group(1))
    evo = int(m.group(2))
    print("    // Code generated by {}\n    // from {}\n"
          .format(basename, name))

    # Choose the writer evo
    if evo <= 2:
        writer = PyObjectEmitter(code_comment=True)
        testType = PyObjectTestEmitter
    elif evo == 3:
        writer = PyObjectEmitterEvo3(code_comment=True)
        testType = PyObjectTestEmitterEvo3
    elif evo == 4:
        writer = PyObjectEmitterEvo4(code_comment=True)
        testType = PyObjectTestEmitterEvo4
    else:
        raise ValueError("evo = {} out of range".format(evo))

    # Open the input and wrap in a parser
    examples = os.path.join(dirname, name)
    with closing(Lines(open(examples))) as lines:
        # Anything before the first heading is not part of any test
        lines.parse_preamble()
        # Each test in the file produces a code object and one or more tests
        while lines.kind() != LineToken.EOF:
            test = lines.parse_test()
            # Strip blank lines from end of example
            body = test.body
            while len(body[-1]) == 0:
                body.pop(-1)
            # Emit the text of the test
            generate(test, testType, writer)
