# Examples for PyByteCode6.java

# This looks like a Python module but it isn't. These are code fragments that
# the module vsj2.exparser will break apart to generate test material.

# empty_class:

# ? name, n
class C:
    pass

name = C.__name__
c = C()
c.a = 10
n = c.a


# simple_method:
x, y, z = 1, 2, 3.0
# ? fx, fy, fz
class C:
    def f(self, x):
        return ((x-6)*x + 11)*x + 36

c = C()
fx = c.f(x)
fy = c.f(y)
fz = c.f(z)

# attributes:
x, y = 4, 5.0
# ? fx, fy, ca
class C:
    def __init__(self, a, b):
        self.a = a
        self.b = b

    def f(self, x):
        return (x + self.a) * x + self.b

c = C(-9, 62)
ca = c.a
fx = c.f(x)
fy = c.f(y)

