# attr_access_builtin.py

true_and = True.__and__.__name__

# PyJavaMethod.self exposed as __self__ is target
hello = "hello".replace.__self__

#Fails in Descriptor.descr_get_qualname() (incomplete port?)
#one_sub = (1).__sub__.__qualname__

#Requires builtins to be properly integrated to frame
#bool_add = bool.__add__.__name__

