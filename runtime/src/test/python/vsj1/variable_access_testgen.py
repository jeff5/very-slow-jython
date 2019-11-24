# Generate test cases for execution modules and functions with assignment

import symbolutil, astutil, types, ast
from dis import dis
from codeutil import (as_code, list_code_objects,
                      show_code, show_block,
                      exec_code)

# We use the same examples as in code_testgen.py
from code_testgen import ( emit_java_ast,
                           globprog1, globprog2, globprog3,
                           builtin, argprog1,
                           closprog1, closprog2, closprog3,
                           kwargprog, kwargcell,
)

def emit_java_global_puts(prog):
    state = exec_code(prog)
    for name, value in state.items():
        if isinstance(value, (int, float)):
            print('        state.put("{}", {!r});'.format(name, value))
        if isinstance(value, str):
            print('        state.put("{}", "{}");'.format(name, value))

def emit_java_assigntest(prog, name):
    #print("\n# {}\n{}".format(name, prog))
    tree = ast.parse(prog, "m")

    # Get the code objects
    co = compile(prog, name, 'exec')
    code_list = list_code_objects(co)

    # Generate test for Java
    print("    @Test public void {}() {{".format(name))
    print("        // @formatter:off")
    emit_java_ast(prog, tree)
    print("        // @formatter:on")
    print("        Map<String, Object> state = new HashMap<>();")
    emit_java_global_puts(prog)
    print("        executeTest(module, state); // " + name)
    print("}\n")


kwargprog2 = """\
# Program where functions have keyword arguments
def p(r, i=4):
    def sum(r, q, *args, j, i, **kwargs):
        mysum = r + i
        return mysum
    def diff():
        def q():
            return r - i
        return q()
    def prod():
        return r * i
    ar = (1, 2, 3) 
    s = sum(r, *ar, i=i, j=0, k="hello", l="world")
    return prod() + s + diff()

result = p(7, 4)
"""

kwargprog3 = """\
# Program where functions have keyword arguments
def p(r, i=4):
    def sum(r, q, *args, j, i, **kwargs):
        mysum = r + i
        return mysum
    def diff():
        def q():
            return r - i
        return q()
    def prod():
        return r * i
    kw = { 'q':1, 'i':i, 'k':"hello" } 
    s = sum(r, j=0, l="world", **kw)
    return prod() + s + diff()

result = p(7, 4)
"""

worklist = (
    "globprog1",
    "globprog2",
    "globprog3",
    #"builtin",
    "argprog1",
    "closprog1",
    "closprog2",
    "closprog3",
    #"kwargprog",
    #"kwargprog2",
    #"kwargprog3",
    #"kwargcell",
)


def show_worklist():
    for name, mode in worklist:
        print(name)
        prog = globals()[name]
        m = compile(prog, '<module ' + name + '>', 'exec')
        #print("\n{}\n".format(name))
        #show_block(m)
        gbl = dict()
        loc = exec_code(m, gbl, mode)
        del gbl['__builtins__']
        print(gbl)

def main():
    for name in worklist:
        prog = globals()[name]
        print("\n// {}\n".format(name))
        emit_java_assigntest(prog, name)


##show_worklist()

if __name__ == '__main__':
    main()
