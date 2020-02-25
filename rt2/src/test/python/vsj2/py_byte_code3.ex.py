# Examples for PyByteCode3.java

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

# boolean_arithmetic:
u, t, f = 42, True, False
u, t, f = 42., True, False
# ? a, b, c
a = u + t
b = u * t
c = u * f

# simple_if:
b = True
b = False
b = 0
b = 1
b = ""
b = "something"
b = None
# ? r
if b:
    r = 1
else:
    r = 0

# multi_if:
a, b = False, False
a, b = False, True
a, b = True, False
a, b = True, True
# ? r
if a and b:
    r = 2
elif a or b:
    r = 1
else:
    r = 0

# comparison:
a, b= 2, 4
a, b= 4, 2
a, b= 2, 2
# ? lt, le, eq, ne, ge, gt
lt = a < b
le = a <= b
eq = a == b
ne = a != b
ge = a >= b
gt = a > b

# simple_loop:
n = 6
# ? n, sum
sum = 0
while n > 0:
    sum = sum + n
    n = n - 1


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

