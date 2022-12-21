# builtins_module.py
#
# The focus of this test is the way the interpreter resolves names
# in the builtins dictionary (after local and global namespaces).
# This happens in opcodes LOAD_NAME and LOAD_GLOBAL.

# Access sample objects from the builtins module implicitly
# globals['__builtins__'] not defined/used automatically
#bi_true = __builtins__['True']
#bi_int_name = __builtins__['int'].__name__

# Access sample objects from the builtins module implicitly
# Opcode is LOAD_NAME

int_name = int.__name__
max_name = max.__name__

# Call functions to prove we can
# Opcode is LOAD_NAME
ai = abs(-42)
af = abs(-41.9)

# Access from within a nested scope

def f(x):
    # Opcode is LOAD_GLOBAL
    return abs(x), min.__name__

aj, min_name = f(-123_000_000_000)

# Not marshallable
del f
