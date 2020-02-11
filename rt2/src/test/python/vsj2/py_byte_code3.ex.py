# Examples for PyByteCode1.java

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

# tuple_index:
c = 22.0
# ? b, c
d = (20, "hello", c)
b = d[1]
c = d[2] + d[0]

# tuple_dot_product:
a, b, n = (2, 3, 4), (3, 4, 6), 3
a, b, n = (1., 2., 3., 4.), (4., 3., 4., 5.), 4
# ? sum
sum = a[0] * b[0]
i = 1
while i < n:
    sum = sum + a[i] * b[i]
    i = i + 1

# list_index:
c = 22.0
# ? a, b, c
d = [20, "hello", c]
a = d[0]
b = d[1]
d[2] = a + c
c = d[2]

# list_dot_product:
n = 2
# ? n, sum
a = [1.2, 3.4, 5.6, 7.8] * (3 * n)
b = (4 * n) * [1.2, 4.5, 7.8]
n = 12 * n  # lists are this long
i = 0
sum = 0.0
while i < n:
    sum = sum + a[i] * b[i]
    i = i + 1

