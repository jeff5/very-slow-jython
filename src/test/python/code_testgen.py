# Generate test cases for exploring names in code object generation

import symbolutil, astutil, types, ast
from dis import dis
from codeutil import (as_code, list_code_objects,
                      show_code, show_block,
                      exec_code)

def emit_java_ast(prog, tree):
    """ Emit the AST in Java functional form. """
    for line in prog.splitlines():
        print("        // {}".format(line))
    print("        mod module", end=" = ")
    astutil.pretty_java(tree, width=120)
    print("        ;")


def emit_java_codetest(prog, name):
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
    print()
    print("checkCode(module, new RefCode[]{ // " + name)
    for co in code_list:
        emit_java_refcode(co)
    print("});")
    print("}\n")


_REF_INT_ATTRS = (
    'co_argcount',
    'co_kwonlyargcount',
    #'co_nlocals',
    #'co_flags',
    )

_REF_NAME_LISTS = (
    'co_names',
    'co_varnames',
    'co_cellvars',
    'co_freevars',
    )

def emit_java_refcode(c):
    print("    new RefCode( //")
    # Emit the integer attributes chosen
    int_attrs = [(n, getattr(c, n)) for n in _REF_INT_ATTRS]
    print(',\n'.join(map(java_int_attr, int_attrs)))
    # Emit the list-of-names attributes chosen
    name_lists = [(n, getattr(c, n)) for n in _REF_NAME_LISTS]
    print(',\n'.join(map(java_name_list, name_lists)))
    # Round off with the name
    print("        \"{}\" ),".format(c.co_name))

def java_int_attr(attr_pair):
    # Emit an integer attribute as one line 
    name, value = attr_pair
    return "        {}, // {}".format(value, name)

def java_name_list(attr_pair):
    # Emit a name list as one line 
    name, names = attr_pair
    quoted_names = ['"'+n+'"' for n in names]
    strings = ','.join(quoted_names)
    return "        new String[]{{{}}}, // {}".format(strings, name)


funcprog = """\
def f():
    #global g, x
    def g():
        y = len(x+1)
    x = 42
f()
x = len("hello")
"""

classprog = """\
class A():
    def g(self):
        y = len(x+1)

a = A()
x = len("hello")
"""

faqprog = """\
# The FAQ on global and local is insufficient
a = 6
b = 7

def p():
    def q():
        global c
        c = a * b
    q()

p()
print(a, b, c)
"""

globprog1 = """\
# Test allocation of "names" (globals)
# global d is *not* ref'd at module level
b = 1
a = 6
result = 0

def p():
    global result
    def q():
        global d # not ref'd at module level
        d = a + b
    q()
    result = a * d

p()
"""

globprog2 = """\
# Test allocation of "names" (globals)
# global d is *assigned* at module level
b = 1
a = 6
result = 0

def p():
    global result
    def q():
        global d
        d = a + b
    q()
    result = a * d

d = 41
p()
"""

globprog3 = """\
# Test allocation of "names" (globals)
# global d *decalred* but not used at module level
global a, b, d
b = 1
a = 6
result = 0

def p():
    global result
    def q():
        global d
        d = a + b
    q()
    result = a * d

p()
"""


builtin = """\
# Test allocation of "names" (globals and a built-in)
a = -6
b = 7
result = min(0, b)

def p():
    global result
    def q():
        global a
        a = abs(a)
    q()
    result = a * b

p()
"""

argprog1 = """\
# Test allocation of argument lists and locals
def p(eins, zwei):
    def sum(un, deux, trois):
        return un + deux + trois
    def diff(tolv, fem):
        return tolv - fem
    def prod(sex, sju):
        return sex * sju
    drei = 3
    six = sum(eins, zwei, drei)
    seven = diff(2*six, drei+zwei)
    return prod(six, seven)

result = p(1, 2)
"""


closprog1 = """\
# Program requiring closures made of local variables
def p(a, b):
    x = a + 1 # =2
    def q(c):
        y = x + c # =4
        def r(d):
            z = y + d # =6
            def s(e):
                return (e + x + y - 1) * z # =42
            return s(d)               
        return r(c)
    return q(b)

result = p(1, 2)
"""

closprog2 = """\
# Program requiring closures from arguments
def p(r, i):
    def sum():
        return r + i
    def diff():
        def q():
            return r - i
        return q()
    def prod():
        return r * i
    return prod() + sum() + diff()

result = p(7, 4)
"""

closprog3 = """\
# Program requiring closures (mixed)
def p(ua, b): #(1,2)
    z = ua + b # 3
    def q(uc, d): #(1,3)
        y = ua + uc + z # 5
        def r(ue, f): #(1,5)
            x = (ua + uc) + (ue + f) + (y + z) # 16
            def s(uf, g): # (1,16)
                return (ua + uc - ue) + (uf + g) + (x + y + z)
            return s(ue, x)               
        return r(uc, y)
    return q(ua, z)
result = p(1, 2)
"""


kwargprog = """\
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
    s = sum(r, 1, 2, 3, i=i, j=0, k="hello", l="world")
    return prod() + s + diff()

result = p(7, 4)
"""

kwargcell = """\
# Program where a default value is a non-local (cell)
def p(i):
    def q():
        def r(m=i):
            i = 43
            return m
        return r()
    return q()

result = p(42)
"""

worklist = (
    ("globprog1", 1),
    ("globprog2", 1),
    ("globprog3", 1),
    ("builtin", 1),
    ("argprog1", 1),
    ##("closprog1", 1),
    ##("closprog2", 1),
    ##("closprog3", 1),
    ##("kwargprog", 1),
    ##("kwargcell", 1),
)


def show_worklist():
    for name, mode in worklist:
        print(name)
        prog = globals()[name]
        m = compile(prog, '<module>', 'exec')
        #print("\n{}\n".format(name))
        #show_block(m)
        gbl = dict()
        loc = exec_code(m, gbl, mode)
        del gbl['__builtins__']
        print(gbl)

def main():
    for name, mode in worklist:
        prog = globals()[name]
        print("\n// {}\n".format(name))
        emit_java_codetest(prog, name)

        # Compare symbols
        ##symbolutil.show_module(prog)

##show_worklist()

main()
