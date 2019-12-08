# Utilities for exploring code objects

import sys, ast, types
from dis import dis, show_code, disassemble
from pprint import pprint
import astutil

def as_code(fc):
    """Convert object to code it contains."""
    if isinstance(fc, types.FunctionType):
        fc = fc.__code__
    if isinstance(fc, types.FrameType):
        fc = fc.f_code
    return fc

def list_code_objects(co):
    """ Given a starting code object, make a list containing the
        starting object and all the contained code objects by a
        depth-first exploration of its constants.
    """
    code_list = []
    def append_code(c):
        #print(c)
        code_list.append(c)
        for k in c.co_consts:
            if isinstance(k, types.CodeType):
                append_code(k)
    append_code(co)
    return code_list

_CODEATTR = (
    'co_argcount',
    'co_kwonlyargcount',
    'co_nlocals',
    'co_name',
    #'co_consts',
    'co_names', 'co_varnames', 'co_cellvars', 'co_freevars',
    )

def show_block(c):
    "Recursively dump code properties"
    c = as_code(c)
    print('Code block: {}'.format(c.co_name))
    for key in _CODEATTR:
        val = getattr(c, key, None)
        print('    {:12s} : {!r}'.format(key, val))
    #dis(c)
    for sub in c.co_consts:
        if isinstance(sub, types.CodeType):
            show_block(sub)

def show_code(prog):
    ##symbolutil.show_module(prog)
    m = compile(prog, '<module>', 'exec')
    show_block(m)


def exec_code(prog, globals=None, mode=1):
    newglobals = globals is None
    if newglobals:
        globals = dict()
    if mode == 2:
        locals = dict()       
    else:
        locals = globals
    if not isinstance(prog, types.CodeType):
        prog = compile(prog, '<module>', 'exec')
    exec(prog, globals, locals)
    if newglobals:
        del globals['__builtins__']
    return locals

# Pretty-print properties of the code from a code, function or frame
def ppcode(fc):
    fc = as_code(fc)
    fcd = dict(((n, getattr(fc,n)) for n in dir(fc) if n.startswith("co_")))
    pprint(fcd)

_FUNCNAMES = ['__class__', '__closure__', '__code__', '__defaults__',
             '__globals__', '__kwdefaults__',
             '__module__', '__name__', '__qualname__']

# Pretty-print properties of a function
def ppfunc(f):
    fd = dict(((n, getattr(f,n)) for n in _FUNCNAMES))
    pprint(fd, depth=2)

# Dump the function properties and code object of a function
def dump_func(f):
    ppfunc(f)
    ppcode(f)
    dis(f)


