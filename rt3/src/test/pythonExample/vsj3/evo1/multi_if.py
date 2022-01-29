# multi_if.py

a, b = False, False

if a and b:
    r = 2
elif a or b:
    r = 1
else:
    r = 0

a, b = False, True
if a and b:
    r1 = 2
elif a or b:
    r1 = 1
else:
    r1 = 0

a, b = True, False
if a and b:
    r2 = 2
elif a or b:
    r2 = 1
else:
    r2 = 0

a, b = True, True
if a and b:
    r3 = 2
elif a or b:
    r3 = 1
else:
    r3 = 0
