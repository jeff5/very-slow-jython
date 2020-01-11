# Examples for PyByteCode1.java

# This looks like a Python module but it isn't. These are code fragments that
# the module vsj2.exparser will break apart to generate test material.

# load_store_name:
a, b = 1, 2
# ? a, b, c
a = b
b = 4
c = 6

# load_store_name_ex:
a, b = "Hello", "World"
# ? a, b, c
a = b
b = 2.0
c = 'begins!'
