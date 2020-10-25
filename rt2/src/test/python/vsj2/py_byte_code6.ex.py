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

# ? t
# Create a class using the type constructor
C = type('C', (), {})
c = C()
t = repr(type(c))


# type_get_attribute:

# ? a, b
# A created class has attributes we can get
C = type('C', (), {'a': 'hello', 'b': 'world'})
a = C.a
c = C()
b = c.b

# type_set_attribute:

# ? a, b, ca, cb
# A created class has attributes we can set
C = type('C', (), {'b': 'world'})
c = C()
C.a = 'hello'
C.b = 42
a = C.a
b = C.b
ca = c.a
cb = c.b


# type_instance_dict:

# ? a, b
# An instance of a created class has a read/write dictionary
C = type('C', (), {'b': 'world'})
c = C()
c.a = 5
c.b = 42
a = c.a
b = c.b



# class_definition:

# ? t
# Create a class using class definition
class C:
    pass

c = C()
t = repr(type(c))


# class_get_attribute:

# ? a, b
# A created class has attributes we can get
class C:
    a = 'hello'
    b = 'world'

a = C.a
c = C()
b = c.b

# class_set_attribute:

# ? a, b, ca, cb
# A created class has attributes we can set
class C:
    b = 'world'

c = C()
C.a = 'hello'
C.b = 42
a = C.a
b = C.b
ca = c.a
cb = c.b


# class_instance_dict:

# ? a, b
# An instance of a created class has a read/write dictionary
class C:
    b = 'world'

c = C()
c.a = 5
c.b = 42
a = c.a
b = c.b


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

