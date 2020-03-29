# Examples for PyByteCode5.java

# This looks like a Python module but it isn't. These are code fragments that
# the module vsj2.exparser will break apart to generate test material.

# builtin_call:
x = "hello"
x = [1,2,3]
x = ("horse", 42, None)
# ? n
n = len(x)

# def_func:
x = 14
x = "ha"
# ? y
def f():
    return x * 3
y = f()

# def_func_args:
u, v = 6, 7
u, v = 3, "ha"
# ? y
def f(x, y):
    return x * y
y = f(u, v)


# faqprog:

# ? c
# The FAQ on global and local is insufficient
a = 6
b = 7
def p():
    def q():
        global c
        c = a * b
    q()
p()


# globprog_a:

# ? d, result
# Test allocation of "names" (globals)
# global d is *not* ref'd at module level
b = 1
a = 6

def p():
    global result
    def q():
        global d    # not ref'd at module level
        d = a + b
    q()
    result = a * d

p()


# globprog_b:

# ? d, result
# Test allocation of "names" (globals)
# global d is *assigned* at module level
b = 1
a = 6
d = 41

def p():
    global result
    def q():
        global d
        d = a + b
    q()
    result = a * d

p()


# globprog_c:

# ? d, result
# Test allocation of "names" (globals)
# global d *declared* but not used at module level
global a, b, d
b = 1
a = 6

def p():
    global result
    def q():
        global d
        d = a + b
    q()
    result = a * d

p()


# alloc_builtin:

# ? result
# Test allocation of "names" (globals and a built-in)
a = -6
b = 7
result = min(0, b)

def p():
    global result
    def q():
        global a
        a = abs(a)
    q()
    result = a * b

p()


# argprog:

# ? result
# Test allocation of argument lists and locals
def p(eins, zwei):
    def sum(un, deux, trois):
        return un + deux + trois
    def diff(tolv, fem):
        return tolv - fem
    def prod(sex, sju):
        return sex * sju
    drei = 3
    six = sum(eins, zwei, drei)
    seven = diff(2 * six, drei + zwei)
    return prod(six, seven)

result = p(1, 2)



# closprog_local:

# ? result
# Program requiring closures made of local variables
def p(a, b):
    x = a + 1   # =2
    def q(c):
        y = x + c   # =4
        def r(d):
            z = y + d   # =6
            def s(e):
                return (e + x + y - 1) * z  # =42
            return s(d)               
        return r(c)
    return q(b)

result = p(1, 2)


# closprog_arg:

# ? result
# Program requiring closures from arguments
def p(r, i):
    def sum():
        return r + i
    def diff():
        def q():
            return r - i
        return q()
    def prod():
        return r * i
    return prod() + sum() + diff()

result = p(7, 4)


# closprog_mix:

# ? result
# Program requiring closures (mixed)
def p(ua, b):   #(1,2)
    z = ua + b # 3
    def q(uc, d):   #(1,3)
        y = ua + uc + z # 5
        def r(ue, f):   #(1,5)
            x = (ua + uc) + (ue + f) + (y + z) # 16
            def s(uf, g):   # (1,16)
                return (ua + uc - ue) + (uf + g) + (x + y + z)
            return s(ue, x)               
        return r(uc, y)
    return q(ua, z)

result = p(1, 2)



# kwargprog_a:

# ? result
# Program where functions have keyword arguments
def p(r, i=4):
    def sum(r, q, *args, j, i, **kwargs):
        mysum = r + i
        return mysum
    def diff():
        def q():
            return r - i
        return q()
    def prod():
        return r * i
    s = sum(r, 1, 2, 3, i=i, j=0, k="hello", l="world")
    return prod() + s + diff()

result = p(7, 4)


# kwargcell:

# ? result
# Program where a default value is a non-local (cell)
def p(i):
    def q():
        def r(m=i):
            i = 43
            return m
        return r()
    return q()

result = p(42)


# kwargprog_b:

# ? result
# Program where functions have keyword arguments
def p(r, i=4):
    def sum(r, q, *args, j, i, **kwargs):
        mysum = r + i
        return mysum
    def diff():
        def q():
            return r - i
        return q()
    def prod():
        return r * i
    ar = (1, 2, 3)
    s = sum(r, *ar, i=i, j=0, k="hello", l="world")
    return prod() + s + diff()

result = p(7, 4)


# kwargprog_c:

# ? result
# Program where functions have keyword arguments
def p(r, i=4):
    def sum(r, q, *args, j, i, **kwargs):
        mysum = r + i
        return mysum
    def diff():
        def q():
            return r - i
        return q()
    def prod():
        return r * i
    kw = { 'q':1, 'i':i, 'k':"hello" }
    s = sum(r, j=0, l="world", **kw)
    return prod() + s + diff()

result = p(7, 4)

