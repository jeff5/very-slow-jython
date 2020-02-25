# Examples for PyByteCode2.java

# This looks like a Python module but it isn't. These are code fragments that
# the module vsj2.exparser will break apart to generate test material.

# load_store_name:
a, b = 1, 2
# ? a, b, c
a = b
b = 4
c = 6

# negate:
a, b = 6, -7
a, b = 6., -7.
# ? a, b
a, b = -a, -b

# binary:
a, b = 7, 6
a, b = 7., 6.
a, b = 7., 6
a, b = 7, 6.
# ? sum, diff, prod
sum = a + b
diff = a - b
prod = a * b

