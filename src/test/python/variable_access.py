# What do we get when we compile a module?

import ast, types
from dis import dis, show_code, disassemble
from pprint import pprint
import astutil

# Pretty-print properties of the code from a code, function or frame
def ppcode(fc):
    if isinstance(fc, types.FunctionType):
        fc = fc.__code__
    if isinstance(fc, types.FrameType):
        fc = fc.f_code
    fcd = dict(((n, getattr(fc,n)) for n in dir(fc) if n.startswith("co_")))
    pprint(fcd)

# Pretty-print properties of a function
def ppfunc(f):
    fd = dict(((n, getattr(f,n)) for n in FUNCNAMES))
    pprint(fd, depth=2)

FUNCNAMES = ['__class__', '__closure__', '__code__', '__defaults__',
             '__globals__', '__kwdefaults__',
             '__module__', '__name__', '__qualname__']

# Dump the function properties and code object of a function
def dump_func(f):
    ppfunc(f)
    ppcode(f)
    dis(f)

prog_to_examine = """\
global a
qux = 9
a = 1
def f():
    global g
    b = 10 + a
    c = 20 + b
    def gg():
        global a
        nonlocal b, c
        d = 100 + b + a
        c = 20
        #a = 2
    e = 30 + c
    g = gg
f()
g()
"""

# Examine program with every type of load and store
def examine_prog(verbose):

    tree = ast.parse(prog_to_examine)
    code = compile(tree, '<tree>', 'exec')

    print("\n*** top-level code object ***")
    ppcode(code)
    dis(code)

    glob, loc = dict(foo=7), dict(bar=8)
    exec(code, glob, loc)
    # __builtins__ forced in but not interesting
    del glob['__builtins__']

    print("\n*** globals ***")
    pprint(glob)

    print("\n*** locals ***")
    pprint(loc)

    if verbose:
        print("\n*** function f ***")
        dis(loc['f']) # short form
        dump_func(loc['f'])

        print("\n*** function g ***")
        try:
            dump_func(glob['g'])
        except KeyError:
            print("    not defined")

    print("\n*** tree ***")
    astutil.pretty_java(tree)


if __name__ == "__main__" :

    # Complex example
    examine_prog(True)    
