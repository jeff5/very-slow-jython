# attr_access_builtin.py

true_and = True.__and__.__name__

#Fails in Descriptor.descr_get_qualname() (incomplete port?)
#one_sub = (1).__sub__.__qualname__

#Requires __builtins__ to define bool
#bool_add = bool.__add__.__name__

