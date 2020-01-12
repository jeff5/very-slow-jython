# Generate examples for the bytecode interpreter

import os
import dis
from contextlib import closing
from vsj2.exparser import LineToken, Lines
from vsj2.srcgen import PyObjectEmitter


class PyObjectTestEmitter(PyObjectEmitter):
    """Class to emit a test PyCode and a JUnit test method for each case.

    The generated code assumes a particular representation for Python in Java,
    which is that first exhibited in PyByteCode1.java.
    """

    def __init__(self, test, stream=None, width=None, indent=None):
        super().__init__(stream, width, indent)
        self.test = test
        # Compile the lines to byte code
        prog = '\n'.join(test.body)
        code = compile(prog, self.test.name, 'exec')
        self.bytecode = dis.Bytecode(code)

    def emit_test_material(self):
        """Emit the PyCode comments, declaration and initialiser."""
        self.emit_comments()
        self.emit_declaration()
        return self

    def emit_test_cases(self):
        """Emit the test methods."""
        num = 0
        for c in self.test.cases:
            num += 1
            name = "test_{}{:d}".format(self.test.name, num)
            self.emit_test_method(name, c)
        return self

    def emit_comments(self):
        """Emit the comments that precede the PyCode declaration"""
        self.emit_line("/**")
        self.emit_line(f" * Example '{self.test.name}': <pre>")
        for line in self.test.body:
            self.emit_line(f" * {line}")
        self.emit_line(" * </pre>")
        return self.emit_line(" */")

    def emit_declaration(self):
        """Emit the PyCode declaration and initialiser"""
        self.emit_line("//@formatter:off")
        self.emit_line("static final PyCode ")
        self.emit(self.test.name.upper(), " = ")
        self.python_code(self.bytecode.codeobj, ";")
        self.emit_line("//@formatter:on")
        return self.emit_line()

    def emit_test_method(self, name, c):
        """Emit one JUnit test method with the given name"""
        # Prepare the "before" name space by executing the "case" code
        before = dict()
        exec(c, {}, before)
        # Execute the example code against a copy of that name space
        globals = dict(before)
        exec(self.bytecode.codeobj, globals)
        # Extract those variables names as results to test
        after = {k: globals[k] for k in self.test.test}
        # Check
        #print("before = {!r}".format(before))
        #print("after = {!r}".format(after))
        # Emit the code for the test method
        self.emit_line("@Test")
        self.emit_line("void " + name + "() {")
        with self.indentation():
            # Load the global name space with the test case values
            self.emit_line("PyDictionary globals = new PyDictionary();")
            for k, v in before.items():
                self.emit_line("globals.put(")
                self.python(k, ", ")
                self.python(v, ");")
            # Execute the code in the Java implementation
            self.emit_line("PyCode code = " + self.test.name.upper() + ";")
            self.emit_line("ThreadState tstate = new ThreadState();")
            self.emit_line(
                "PyFrame frame = code.createFrame(tstate, globals, globals);")
            self.emit_line("frame.eval();")
            # Compare named results against the values this Python got
            for k, v in after.items():
                self.emit_line("// {} == {}".format(k, repr(v)))
                self.emit_line("assertEquals(")
                with self.indentation():
                    self.java_string(k, ", ")
                    self.python(v, ", ")
                    self.emit("globals.get(")
                    self.python(k, "));")
        self.emit_line("}")
        return self.emit_line()

    def python_code(self, code, suffix=""):
        """Emit Java to construct a Python code object."""
        # Emit disassembly as comment
        self.emit_line("/*")
        lines = dis.Bytecode(code).dis().splitlines()
        for line in lines:
            self.emit_line(" * " + line)
        self.emit_line(" */")
        self.emit_line("")

        # Then the object itself via super-class
        super().python_code(code, suffix)
        return self

# ------------------------- Main Program -------------------------


def generate(test, stream=None, width=None, indent=None):
    """Generate Java code to test one program example"""
    # Strip blank lines from end of example
    body = test.body
    while len(body[-1]) == 0:
        body.pop(-1)
    # Generate the text of a test based on this example
    with closing(PyObjectTestEmitter(
            test, stream=stream, width=width, indent=indent)) as e:
        e.emit_test_material()
        e.emit_test_cases()
        e.emit_line("")


def main(examples):
    # Open the input and wrap in a parser
    with closing(Lines(open(examples))) as lines:
        # Anything before the first heading is not part of any test
        lines.parse_preamble()
        # Each test in the file produces a code object and one or more tests
        while lines.kind() != LineToken.EOF:
            test = lines.parse_test()
            generate(test)


if __name__ == "__main__":
    # Derive the examples file name from this one
    print("    // Code generated by {}\n".format(os.path.basename(__file__)))
    main(".ex".join(os.path.splitext(__file__)))
