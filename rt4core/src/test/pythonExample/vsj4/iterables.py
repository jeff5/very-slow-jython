# iterables.py

a, b, c = 1, 2, 3
a1, b1, c1 = ('a', 'b', 'c')

u = (0, 1, 2, 3, 4, 5, 6, 7)
a2, b2, *list, x2, y2, z2 = u
# list = [2, 3, 4]

t = (*list,)
s = (a2, b2, *list, x2, y2, *t, z2)
