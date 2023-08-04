"""
A key aspect of translating the C-based code hints to Java is proper conversion of types from C to Java.

It may seem like primitive types are quite obvious and straight forward to translate.  However, even a C-`int` might
actually be a `boolean` in Java.  Moreover, the CPython code itself might have `int` while the interface in the ASDL
is specified as using a `boolean`.

While C clearly differentiates between pointers and values, Java basically uses references in all cases, anyway
(except for primitives, of course).  As a consequence, we can safely ignore pointer types in the original C-code,
i.e. discard any trailing stars in types.  The one place where this becomes problematic is where we have pointers
used for 'out'-parameters of functions.  In Java, we have to rewrite such functions so as to return the respective
results.

There are a few heuristics when dealing with C-types:
- Some type names end in `_ty`, which is neither customary nor really necessary in Java.
- Some type names start with `asdl_`, which we can ignore, too, as we have proper namespaces in Java.
- C-code uses linked lists whose name ends in `_seq`.  We translate those to Java arrays.
- CPython uses snake case such as `some_type` whereas in Java we want to write this as `SomeType`.  We therefore
  normalise the type names.
- There are both lower- and uppercase variants of the same name used in C code (e.g. `Expr` and `expr` or `expr_ty`,
  respectively).  We thus have to careful when normalising those names to use Java camel case.

When parsing the ASDL file, we generate a mapping from C- and ASDL-types to Java-types.  This mapping is exported to
a text file and imported (when present) when this module is loaded again.  Each line in the file has the form
`c_abc->JAbc` where `c_abc` is the original C-type and `JAbc` the corresponding Java-type.
"""
import atexit as _atexit
from pathlib import Path
from typing import List, Optional, Tuple


# We put the cache into the CWD by default but tools use ./generated
_CACHE_FILE_PATH: Path = Path.cwd() / '.c_java_type_cache'

# This is a flag for debugging purposes

_CACHE_FILE_LOADED = False

# This is the mapping from C- to Java-types
# The initialisation here will most probably be overwritten once we have a cache file, of course, but we still need
# these basic conversions to start with.
type_map = {
    'char': 'char',
    'char*': 'String',          # `String` is probably more likely and a better correspondence than `Character` here
    'const char*': 'String',
    'double': 'double',
    'double*': 'Double',
    'float': 'float',
    'float*': 'Float',
    'int': 'int',
    'int*': 'Integer',
    'long': 'int',
    'long*': 'Integer',
    'long long': 'long',
    'long long*': 'Long',
    'size_t': 'int',
    'void*': 'Object',
    # Types specific to our Python translator:
    'Py_ssize_t': 'int',
    # The following come from the ASDL language (see `asdl.py`, although the mapping is our own)
    'constant': 'Object',
    'identifier': 'String',
    'string': 'String',
}

# A dictionary with function declarations and how to translate the function arguments.  The entries have the form
#   `(str)c_function_name: ((str)java_function_expr, (tuple)c_param_list, (tuple)java_param_list)`
functions = {}

# This is the basic type that is used if no other type fits
default_Java_object = 'Object'

# We collect all types that could not be translated according to the map provided above so that we can inspect any
# missing translation schemes.
unknown_types = set()


def add_type(c_type: str, j_type: str):
    """
    Adds a new translation to the type map.
    """
    if c_type is None or j_type is None:
        raise ValueError("arguments for type translation cannot be empty")
    if not isinstance(c_type, str):
        c_type = str(c_type)
    if not isinstance(j_type, str):
        j_type = str(j_type)
    type_map[c_type] = j_type


def add_function(c_func: str, c_args: str|List[str]|Tuple[str]|None,
                 j_func: str|None, j_args: str|List[str]|Tuple[str]|None):
    """
    Adds a new function to the mapping of functions.

    The Java-function is actually an arbitrary expression and does not have to be a name.  This way you can translate
    a C-function call `_make_foo()`, say, to something like `new Foo()`.  You can even leave the Java-function empty,
    which will cause the original C-call to disappear.  If there is exactly one Java-argument defined, that one
    argument will survive, otherwise the entire expression will simply be removed.

    In our use case, we have some function calls of the form `CHECK(p, some_result)`, which will check if the result
    is non-null and throw an exception otherwise.  In Java we do not need to do this manually, so we can simply get
    rid of the `CHECK(...)` and take the `some_result` only.  Here is how `add_function` would be used to achieve this:
        `add_function('CHECK', ['Parser *p', 'void *result'], None, ['Object result'])`
    """
    if c_func in (None, ''):
        raise ValueError("C-function name for type translation cannot be empty")
    if c_args is None:
        c_args = ()
    elif isinstance(c_args, str):
        c_args = c_args.split(',')
    if j_args is None:
        j_args = ()
    elif isinstance(j_args, str):
        j_args = j_args.split(',')
    functions[c_func] = [j_func, tuple(c_args), tuple(j_args)]


def is_array(tp: str) -> bool:
    """
    Returns true if the given type (either C- or Java-type) is an array (to our best guess).  We do not count Strings
    as arrays, though.
    """
    if not tp:
        return False
    if not isinstance(tp, str):
        tp = str(tp)
    return tp.endswith('[]') or tp.endswith('_seq*')


def translate(c_type: str|List[str]|Tuple[str], is_seq: bool = False) -> str|List[str]|Tuple[str]:
    """
    This is the core function of this module: it takes a C-type and returns the corresponding Java-type.
    """
    if is_seq:
        return translate(c_type) + '[]'
    elif c_type is None:
        return default_Java_object
    elif isinstance(c_type, (list, tuple)):
        j_type = [translate(t) for t in c_type]
        return type(c_type)(j_type)
    elif not isinstance(c_type, str):
        c_type = str(c_type)
    j_type = type_map.get(c_type, None)
    if j_type:
        return j_type
    elif c_type == '':
        return default_Java_object
    elif c_type.endswith('_seq*'):
        return translate(c_type[:-5]) + '[]'
    elif c_type.endswith('[]'):
        # This is not really a c-type, but it allows us to 'hack' it by appending empty brackets for making
        # any given type to an array.
        return translate(c_type[:-2]) + '[]'
    elif c_type.startswith('asdl_'):
        return translate(c_type[5:])
    elif c_type.endswith('_ty'):
        return translate(c_type[:-3])
    elif c_type.endswith('*'):
        return translate(c_type[:-1])
    elif c_type.startswith('signed ') or c_type.startswith('unsigned '):
        # signed and unsigned integers: for Java we simply drop the signed/unsigned bit
        result = translate(c_type[c_type.index(' ')+1:])
        if result.lower() not in ('int', 'long', 'integer', 'short', 'byte'):
            unknown_types.add(c_type)
        return result
    elif '(' in c_type and ')' in c_type:
        # function pointers and types in parentheses
        idx_1, idx_2 = c_type.index('('), c_type.index(')')
        if idx_1 == 0 and idx_2 == len(c_type) - 1:
            # (t)
            return translate(c_type[1:-1])
        elif idx_1 > 0 and idx_2 == len(c_type) - 1:
            # t(u,v,w)
            func = translate(c_type[0:idx_1])
            args = translate([t.strip() for t in c_type[idx_1 + 1:-1].split(',')])
            return f"{func}({', '.join(args)})"
        elif idx_1 == 0 and c_type[idx_2 + 1] == '(' and c_type.index(')', idx_2 + 1) == len(c_type) - 1:
            # (t)(u,v,w)
            func = translate(c_type[1:idx_2])
            args = translate([t.strip() for t in c_type[idx_2 + 2:-1].split(',')])
            return f"{func}({', '.join(args)})"
        else:
            unknown_types.add(c_type)
            return default_Java_object
    else:
        unknown_types.add(c_type)
        return c_type.title().replace('_', '')


def translate_call(c_func: str, args: List[str]) -> Optional[str]:
    """
    Translates a C function call to the corresponding Java function call.  The arguments are expected to already be
    Java expressions (as strings).  If the function has not been declared (see variable `functions` and function
    `add_function()` above), this returns `None`.  Mind that the result could also be the empty string `''`, which
    means that the function in C is completely ignored/removed in its Java counterpart.
    """

    def separate_names_and_types(args: Tuple[str]) -> dict:
        """
        Separates the names and types of type declarations and returns a dictionary name->type.
        """
        splt = lambda s: ((i := max(s.rfind(c) for c in ' *')), s[i+1:], s[:i+1].strip())[1:]
        return { k: v for (k, v) in map(splt, args) }

    def make_int_to_bool(value: str, name: str) -> str:
        """
        This is needed to convert cases where the C-code uses int but the Java code uses boolean.
        """
        if name in int_to_bools:
            if value == '0':
                return 'false'
            elif value == '1':
                return 'true'
            else:
                return f"{value} != 0"
        else:
            return value

    # First, let us get the function and make sure we have the names of the C- and Java-arguments, so we can map them:
    m = functions.get(c_func, None)
    if m is None:
        return None
    elif len(m) == 3:
        [j_func, c_args, j_args] = m
        c_arg_dict = separate_names_and_types(c_args)
        j_arg_dict = separate_names_and_types(j_args)
        c_arg_names = list(c_arg_dict.keys())           # <- This works only because we are guaranteed that...
        j_arg_names = tuple(j_arg_dict.keys())          # <- ... the order in dicts is preserved.

        # Sometimes the names do not correspond exactly (there are different conventions in C and Java).  We try to
        # establish some form of mapping nonetheless and raise an exception if it does not work.
        # We do this in two tiers (hence the nesting): we first try to do a simple mapping where we remove underscores
        # in C-names and ignore case.  If that did not help, we are looking for a match where the C-name occurs in
        # the Java-name, but makes up at least half of it.  This is intended for cases where the Java-name might be
        # something like `isSimple` instead of just `simple`.
        for j_name in j_arg_names:
            if j_name not in c_arg_names:
                for i, c_name in enumerate(c_arg_names):
                    if c_name.lower().replace('_', '') == j_name.lower():
                        c_arg_dict[j_name] = c_arg_dict[c_name]
                        c_arg_names[i] = j_name
                        break
                else:
                    for i, c_name in enumerate(c_arg_names):
                        if len(c_name) * 2 >= len(j_name) and (
                                c_name.lower() in j_name.lower() or
                                c_name.lower().replace('_', '') in j_name.lower()
                        ):
                            c_arg_dict[j_name] = c_arg_dict[c_name]
                            c_arg_names[i] = j_name
                            break
                    else:
                        c = f"{c_func}({', '.join(c_arg_names)})"
                        j = f"{c_func}({', '.join(j_arg_names)})"
                        raise TypeError(
                            f"cannot translate {c_func}->{j_func} because of missing argument ('{j_name}' in '{c}->{j}')"
                        )
        c_arg_names = tuple(c_arg_names)
        # Sometimes we need to translate a C-int to a Java-boolean, which we try to deduce from the arg types.
        # Otherwise, we do not really care about the actual types of the arguments.
        int_to_bools = set()
        for (name, jt) in j_arg_dict.items():
            if jt.lower() == 'boolean' and c_arg_dict[name] == 'int':
                int_to_bools.add(name)
        functions[c_func] = [j_func, c_args, j_args, c_arg_names, j_arg_names, int_to_bools]
    else:
        [j_func, _, _, c_arg_names, j_arg_names, int_to_bools] = m

    # Second, let us actually map the C arguments to the Java arguments
    arg_dict = { name: arg for name, arg in zip(c_arg_names, args) }
    new_args = [ make_int_to_bool(arg_dict[name], name) for name in j_arg_names ]
    if j_func:
        return f"{j_func}({', '.join(new_args)})"
    elif len(new_args) == 0:
        return ''
    elif len(new_args) == 1:
        return new_args[0]
    else:
        return None


def _try_load_cache():
    """
    Tries to load an existing type map from a cached file.  If there is an issue parsing the file, we abort parsing
    the file, consider it corrupt and ignore the rest.  Since the file is generated automatically in the first place,
    it will be created anew each time we restart the module.
    """
    global _CACHE_FILE_LOADED
    try:
        with open(_CACHE_FILE_PATH, 'r') as f:
            for line in f:
                c_t, j_t = line.strip().split('->')
                if (c_idx := c_t.find('(')) >= 0 and (j_idx := j_t.find('(')) >= 0:
                    c_func, j_func = c_t[:c_idx], j_t[:j_idx]
                    c_args = tuple(t.strip() for t in c_t[c_idx + 1:-1].split(','))
                    j_args = tuple(t.strip() for t in j_t[j_idx + 1:-1].split(','))
                    functions[c_func] = [j_func, c_args, j_args]
                else:
                    type_map[c_t] = j_t
        _CACHE_FILE_LOADED = True
    except FileNotFoundError:
        # Use the global tables as initialised. Save at exit.
        _CACHE_FILE_LOADED = 'to be created'
    except ValueError as t:
        if not t.args[0].startswith('not enough values to unpack'):
            raise t
        _CACHE_FILE_LOADED = 'parse error'


@_atexit.register
def _save_to_cache():
    """
    Saves the type map to the cache file.  This is called automatically when the interpreter is shut down.
    """
    if _CACHE_FILE_LOADED:
        with open(_CACHE_FILE_PATH, 'w') as f:
            for (c_type, j_type) in type_map.items():
                f.write(f"{c_type}->{j_type}\n")
            for (c_func, [j_func, c_args, j_args, *_]) in functions.items():
                f.write(f"{c_func if c_func else ''}({','.join(c_args)})->{j_func}({','.join(j_args)})\n")


def set_cache_file_path(filepath: Path):
    """
    Allows you to manually set the cache file's path.  Only do this if you know what you are doing.
    """
    global _CACHE_FILE_PATH
    filepath.parent.mkdir(parents=True, exist_ok=True)
    _CACHE_FILE_PATH = filepath
    _try_load_cache()


if __name__ == '__main__':
    # Enable next line to read and write on disk.
    # set_cache_file_path(_CACHE_FILE_PATH)
    add_function('_PyAST_AnnAssign',
                 ['expr_ty target', 'expr_ty annotation', 'expr_ty value', 'int simple', 'int lineno', 'int col_offset',
                  'int end_lineno', 'int end_col_offset', 'PyArena *arena'],
                 'new AnnAssign',
                 ['ASTExpr target', 'ASTExpr annotation', 'ASTExpr value', 'boolean is_simple', 'int lineno',
                  'int col_offset', 'int end_lineno', 'int end_col_offset'])
    print(translate(['int_seq*', 'char*', 'asdl_some_expr_seq*', '(void*)', '(long long*)(long, long)']))
    print(translate_call('_PyAST_AnnAssign', ['Name(\"x\")', 'null', 'Constant(123)', '1',  '1', '0', '1', '10', None]))
    print(unknown_types)
    functions.clear()           # Remove the example function here from the cache
