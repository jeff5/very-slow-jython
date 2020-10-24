# Examples for PyByteCode6.java

# This looks like a Python module but it isn't. These are code fragments that
# the module vsj2.exparser will break apart to generate test material.

# numeric_constructor:
u = 7.5
u = "42"
u = 7**21  # < 2**63
u = -7**21
u = 2**63-1
u = -2**63
u = 9**21  # > 2**63
u = -9**21
# ? i, j, x, y
# Exercise the constructors for int and float
i = int(u)
x = float(i)
y = float(u)
j = int(y)

# type_enquiry:
x = 6
x = 6.9
x = "42"
# ? t
# Distinguish type enquiry from type construction (both call type.__new__)
t = type(x)


# type_constructor:

# ? t, name
# Create a class using the type constructor
C = type('C', (), {})
t = type(C)
name = t.__name__


# instance_attributes:

# ? r
# An instance has a dictionary we may access from Python.
class C:
    pass

c = C()
c.a = 10
r = c.a


# class_get_attribute:

# ? name, x
# A class defined in Python has a dictionary we may read from Python.
class C:
    X = 42
    pass

name = C.__name__
x = C.X

# class_set_attribute:

# ? x, y
# A class defined in Python has a dictionary we may write from Python.
class C:
    X = "penguin"
    pass

C.X = "telly"
C.Y = "explode"
x = C.X
y = C.Y


# instance_init:
x, y = 4, 5.0
# ? fx, fy, ca
# The instance dictionary may be set from __init__.
class C:
    def __init__(self, a, b):
        self.a = a
        self.b = b

def f(cc, x):
    return (x + cc.a) * x + cc.b

c = C(-9, 62)
ca = c.a
fx = f(c, x)
fy = f(c, y)


# simple_method:
a, x = 6, 1
a, x = 6.0, 2
a, x = 6, 3.0
# ? cfx, fx
# We may define methods and in the darkness bind them.
class C:
    def __init__(self, a, b, c):
        self.a = a
        self.b = b
        self.c = c

    def f(self, x):
        return ((x-self.a)*x + self.b)*x + self.c


c = C(a, 11, 36)
cf = c.f
cfx = cf(x)
fx = c.f(x)

