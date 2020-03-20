# Examples for PyByteCode4.java

# This looks like a Python module but it isn't. These are code fragments that
# the module vsj2.exparser will break apart to generate test material.

# boolean_and:
u = False
u = True
u = 15
# ? a, b, c, d
a = u & False
b = u & True
c = 42 & u
d = 43 & u

# boolean_or:
u = False
u = True
u = 15
# ? a, b, c, d
a = u | False
b = u | True
c = 42 | u
d = 43 | u

# boolean_xor:
u = False
u = True
u = 15
# ? a, b, c, d
a = u ^ False
b = u ^ True
c = 42 ^ u
d = 43 ^ u
