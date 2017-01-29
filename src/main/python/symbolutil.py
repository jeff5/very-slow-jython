# Generate test cases of nested functions for exploring the scope of names

import sys, ast, types, symtable


_NAME_PROPS = ("assigned", "declared_global", "free", "global", "imported",
         "local", "namespace", "parameter", "referenced")
_NAME_LISTS = ("parameters", "locals", "frees", "globals")
_SCOPE_NAMES = { symtable.FREE:'FREE', symtable.LOCAL:'LOCAL',
                 symtable.GLOBAL_IMPLICIT:'GLOBAL_IMPLICIT',
                 symtable.GLOBAL_EXPLICIT:'GLOBAL_EXPLICIT',
                 symtable.CELL:'CELL' }

def to_scope_name(scope):
    return _SCOPE_NAMES.get(scope)

# Dump one symbol table
def show_symbol_table(st):
    """ Dump one symbol table showing, for each symbol, the result of
        the informational methods is_global() etc., and when the symbol
        table is for a function scope, the (non-empty) tuples of parameters,
        locals, frees, and globals.
    """
    print(st)
    # Dump the name lists get_*()
    if isinstance(st, symtable.Function):
        for nlist in _NAME_LISTS:
            names = getattr(st, "get_"+nlist)()
            if names:
                print('  {} : {!r}'.format(nlist, names))
    # Dump the properties as short names is_global -> global, etc..
    for s in st.get_symbols():
        scope = to_scope_name(s._Symbol__scope)
        props = [scope]
        for p in _NAME_PROPS:
            if getattr(s, "is_"+p)():
                props.append(p)
        print('  "{}" : {}'.format(s.get_name(), ', '.join(props)))


def list_symbol_tables(mst):
    """ Gather all the symbol tables of a module by a depth-first
        exploration of its symbol table tree.
    """
    stlist = []
    def append_st(st):
        #print(st)
        stlist.append(st)
        for s in st.get_symbols():
            for ns in s.get_namespaces():
                append_st(ns)
    if not isinstance(mst, symtable.SymbolTable):
        # Assume it is text of a program to compile
        mst = symtable.symtable(mst, '<string>', 'exec')
    append_st(mst)
    return stlist

# Compile a module and dump the symbol tables
def show_module(prog, name="<module>"):
    """ Compile a module and dump the symbol tables
    """
    if isinstance(prog, symtable.SymbolTable):
        # Already compiled
        mst = prog
    else:
        mst = symtable.symtable(prog, name, 'exec')
    stlist = list_symbol_tables(mst)
    for st in stlist:
        show_symbol_table(st)



