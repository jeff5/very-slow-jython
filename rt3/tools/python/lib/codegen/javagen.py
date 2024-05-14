"""
This module provides classes that can be exported as Java-classes.  However, we only implement a small subset of
what is possible.  For instance, we do not support 'protected' class members.

You can find an example usage at the end of this file.  The primary use case is probably to have one or several
instances of `JavaClass`, where you may add `JavaVariable`s as fields and `JavaMethod`s.  `JavaVariable`s are also
used for parameters of methods.

Additionally, there is a `JavaInterface`-class and some more specialised variants such as `JavaPseudoEnum` to create
a JavaClass that behaves almost like a Java-enum.  We also support memoized methods in two variants: a more general
version and a specialised version used in our generated parser.

Although this module is intended to be used for the Java-pegen project, it is fairly general and might be used in other
projects without much adaption, too.

@author: Tobias Kohn
"""
from pathlib import Path
from typing import Callable, Dict, List, Optional, Set, Tuple, Iterable
from datetime import datetime


# We need a set of all reserved names in order to avoid Java keywords
# and identifiers commonly appearing unqualified in the code.
_JAVA_KEYWORDS = {
    # Keywords
    'abstract', 'continue', 'for', 'new', 'switch',
    'assert', 'default', 'goto', 'package', 'synchronized',
    'boolean', 'do', 'if', 'private', 'this',
    'break', 'double', 'implements', 'protected', 'throw',
    'byte', 'else', 'import', 'public', 'throws',
    'case', 'enum', 'instanceof', 'return', 'transient',
    'catch', 'extends', 'int', 'short', 'try',
    'char', 'final', 'interface', 'static', 'void',
    'class', 'finally', 'long', 'strictfp', 'volatile',
    'const', 'float', 'native', 'super', 'while',
    # Commonly used names
    'List', 'Set', 'Map',
}

# Used by `default_value_for_j_type`
_JAVA_DEFAULT_VALUES = {
    'boolean': 'false',
    'byte': '0',
    'char': '\'\\u0000\'',
    'double': '0.0d',
    'float': '0.0f',
    'int': '0',
    'long': '0L',
    'short': '0',
    'String': 'null',
}

_JAVA_PRIMITIVES = {
    'boolean': 'Boolean',
    'byte': 'Byte',
    'char': 'Character',
    'double': 'Double',
    'int': 'Integer',
    'long': 'Long',
    'short': 'Short',
}

# The list of default methods is used to determine whether a method needs an `@Override` annotation.
_JAVA_DEFAULT_METHODS = {
    'equals',
    'hashCode',
    'toString',
}

_JAVA_MODIFIERS = ['public', 'protected', 'private', 'abstract', 'static', 'final', 'strictfp']


def default_value_for_j_type(j_type: str) -> str:
    """
    Returns a default initialisation value for the given type.  Unless it is a primitive type, this will simply
    be `null`.  The value is always returned as a string (Java code).
    """
    return _JAVA_DEFAULT_VALUES.get(j_type, 'null')


def to_java_name(name: str, *, prefix=None,
                 start_with_upper: Optional[bool] = None,
                 suffix=None) -> str:
    """
    Translate a Python identifier in 'snake case' to 'camel case'.

    Options allow the caller to specify a prefix and a suffix on the name.
    `start_with_upper` optionally determines whether the first letter of
    the result (after the optional prefix) will be uppercase or lowercase.
    If the final result would be a reserved word (keywords ad some others),
    an underscore `_` will be appended.
    """
    segments = []
    first = True
    if prefix is not None:
        segments.append(prefix)
    for s in name.split('_'):
        if s:
            c = s[0]
            if first and start_with_upper is not None:
                # start_with_upper decides case
                if start_with_upper:
                    if c.islower():
                        s = c.upper() + s[1:]
                else:
                    if c.isupper():
                        s = c.lower() + s[1:]
            elif c.islower():
                s = c.upper() + s[1:]
        segments.append(s)
        first = False
    if suffix is not None:
        segments.append(suffix)
    name = ''.join(segments)
    if name in _JAVA_KEYWORDS:
        name += '_'
    return name


class JavaVariable:
    """
    A Java variable is an entity with a name, a (Java) type and optionally an initialisation.  It appears as field
    or parameter.
    """

    def __init__(
            self,
            name: str,
            j_type: str,
            init_value: Optional[str] = None,
            *,
            comment: Optional[str] = None,
            is_optional: Optional[bool] = None,
            is_final: Optional[bool] = False,
            is_static: Optional[bool] = False,
            is_private: Optional[bool] = False,
            is_exposed: Optional[bool] = False
    ):
        self.name = name
        self.j_type = j_type if j_type is not None else 'Object'
        self.init_value = init_value
        self.comment = comment
        self.is_final = is_final
        self.is_static = is_static
        self.is_private = is_private
        self.is_exposed = is_exposed
        if is_optional is True and init_value is None:
            self.init_value = default_value_for_j_type(j_type)

    def __repr__(self):
        return f"{self.__class__.__name__}({self.name!r}, {self.j_type!r})"

    def __str__(self):
        if self.init_value:
            return f"{self.j_type} {self.j_name} = {self.init_value}"
        else:
            return f"{self.j_type} {self.j_name}"

    def create_optional_clone(self) -> 'JavaVariable':
        """
        Creates a clone of this variable that is optional.
        """
        var = JavaVariable(self.name, self.j_type, self.init_value, is_optional=True)
        var.is_final = self.is_final
        var.is_static = self.is_static
        var.is_private = self.is_private
        var.is_exposed = self.is_exposed
        return var

    def get_comment(self) -> str:
        """
        Returns the comment associated with this object or the empty string.
        """
        if comment := self.comment:
            comment = comment.strip()
            if comment[0] == '/':
                return f"  {comment}\n"
            elif '\n' in comment:
                return '  /* ' + comment.replace('\n', '\n   * ') + '\n   */\n'
            else:
                return '  // ' + comment + '\n'
        else:
            return ''

    @property
    def declaration(self) -> str:
        """
        Returns a declaration for this field.
        """
        s = ' static' if self.is_static else ''
        f = ' final' if self.is_final else ''
        p = 'public' if self.is_exposed else 'private'
        return f"{self.get_comment()}  {p}{s}{f} {str(self)};"

    @property
    def forced_init_value(self):
        if not self.init_value:
            return default_value_for_j_type(self.j_type)
        else:
            return self.init_value

    def interface_declarations(self) -> Optional[str]:
        """
        Returns the getter and/or setter for this field if it is public and non-static.  Otherwise, returns `None`.
        """
        if not self.is_static and not self.is_private:
            decls = f"  {self.j_type} get{self.title_name}();\n"
            if not self.is_readonly:
                decls += f"\n  void set{self.title_name}({self.j_type} {self.j_name});\n"
            return decls
        else:
            return None

    @property
    def getter(self) -> str:
        """
        Returns a getter method for this field.
        """
        s = ' static' if self.is_static else ''
        return f"  public{s} {self.j_type} get{self.title_name}() {{\n    return this.{self.j_name};\n  }}"

    @property
    def is_optional(self) -> bool:
        """
        A variable with an initialisation value is considered 'optional' in the sense that we do not necessarily
        need to provide a value for it as an argument, say.
        """
        return self.init_value is not None

    @is_optional.setter
    def is_optional(self, value: bool):
        """
        If you set a variable to be optional, it receives the default initialisation value unless there already is
        an initialisation value present (in that case, it would not be overwritten).
        """
        if value:
            if self.init_value is None:
                self.init_value = default_value_for_j_type(self.j_type)
        else:
            self.init_value = None

    @property
    def is_readonly(self) -> bool:
        return self.is_final

    @is_readonly.setter
    def is_readonly(self, value: bool):
        self.is_final = value

    @property
    def j_name(self) -> str:
        """
        Some names cannot be used because they are keywords in Java. In this case, the `j_name` returns a variant
        with a trailing underscore.
        """
        n = self.name
        if n in _JAVA_KEYWORDS:
            return n + '_'
        else:
            return n

    @property
    def param_decl(self) -> str:
        """
        Returns a parameter declaration, i.e. without the initialisation (if there is any).
        """
        return f"{self.j_type} {self.j_name}"

    @property
    def setter(self) -> str:
        """
        Returns a setter method for this field.
        """
        s = ' static' if self.is_static else ''
        n = self.j_name
        return f"  public{s} void set{self.title_name}({self.j_type} {n}) {{\n    this.{n} = {n};\n  }}"

    @property
    def title_name(self) -> str:
        """
        Returns the name with a capital first letter.  Used for creating getter and setter methods.
        """
        return self.name.title().replace('_', '')



class JavaMethod:
    """
    Represents a Java method.

    You can make some of the arguments optional (it should evidently be the trailing ones), in which case this
    class can create a number of function declarations for you.  However, each argument must still have a unique
    type, i.e. it cannot create method overloading other than for optional arguments.  You might want to use several
    instances of this class to express full method overloading (hence beware of using a name->method dictionary).

    Note that we also use this class for constructors.
    """

    def __init__(
            self,
            cls: 'JavaClass|JavaInterface',
            name: str,
            return_j_type: str,
            args: Optional[JavaVariable|List[JavaVariable]] = None,
            body: Optional[str|List[str]|Callable] = None,
            *,
            comment: Optional[str] = None,
            generics: Optional[List[str]|str] = None,
            is_static: Optional[bool] = False,
            is_public: Optional[bool] = True,
            is_override: Optional[bool] = None
    ):
        self.cls = cls
        self.name = name
        self.comment = comment
        if isinstance(generics, str):
            generics = generics.split(',')
        self.generics = generics
        if self.is_constructor and return_j_type is None:
            self.return_j_type = name
        else:
            self.return_j_type = return_j_type if return_j_type is not None else 'void'
        if isinstance(args, JavaVariable):
            self.args = [ args ]
        else:
            self.args = [] if args is None else args
        self.body = [ body ] if isinstance(body, str) else body
        self.is_static = is_static
        self.is_public = is_public
        if is_override is None:
            is_override = name in _JAVA_DEFAULT_METHODS
        self.is_override = is_override

    def __repr__(self):
        return f"{self.__class__.__name__}({self.cls!r}, {self.name!r}, {self.return_j_type!r})"

    def __str__(self):
        return self._get_head() + ';'

    def _get_args(self, n: Optional[int] = None) -> str:
        """
        Returns the list of arguments where `n` specifies the number of maximum number of arguments to include.  This
        allows you to easily get the argument lists for overloaded variants.
        """
        if self.all_args:
            if isinstance(n, int) and 0 <= n <= len(self.all_args):
                return ', '.join(a.param_decl for a in self.all_args[:n])
            else:
                return ', '.join(a.param_decl for a in self.all_args)
        else:
            return ''

    def _get_body(self) -> str:
        if isinstance(self.body, (list, tuple)):
            body = [
                item.generate_code() if hasattr(item, 'generate_code') else item for item in self.body
            ]
            return '    ' + '\n    '.join(body)
        elif isinstance(self.body, str):
            return '    ' + self.body
        elif self.is_constructor and self.body is None:
            # Automatically create a body for the constructor
            code = []
            if self.cls.has_base_class:
                base_fields = self.cls.base_fields
                super_args = ', '.join(a.j_name for a in base_fields)
                code.append(f"super({super_args});")
            for a in self.args:
                code.append(f"this.{a.j_name} = {a.j_name};")
            return '    ' + '\n    '.join(code)
        elif callable(self.body):
            return self.body()
        else:
            return ''

    def _get_head(self) -> str:
        p = 'public ' if self.is_public else 'private '
        if self.is_constructor:
            return f"{p}{self.name}({self._get_args()})"
        else:
            s = 'static ' if self.is_static else ''
            generics = f"<{','.join(self.generics)}> " if self.generics else ''
            return f"{p}{s}{generics}{self.return_j_type} {self.name}({self._get_args()})"

    def _get_iface_head(self, n: int) -> str:
        generics = f"<{','.join(self.generics)}> " if self.generics else ''
        return f"{generics}{self.return_j_type} {self.name}({self._get_args(n)});"

    def get_comment(self) -> str:
        """
        Returns the comment associated with this object or the empty string.
        """
        if comment := self.comment:
            comment = comment.strip()
            if comment[0] == '/':
                return f"  {comment}\n"
            elif '\n' in comment:
                return '  /* ' + comment.replace('\n', '\n   * ') + '\n   */\n'
            else:
                return '  // ' + comment + '\n'
        else:
            return ''

    def add(self, item):
        """
        Adds either a variable or a line of code to the body of the method.
        """
        if isinstance(item, JavaVariable):
            if isinstance(self.args, list):
                self.args.append(item)
            else:
                self.args = [item]
        elif isinstance(item, str) or hasattr(item, 'generate_code'):
            if isinstance(self.body, list):
                self.body.append(item)
            elif isinstance(self.body, str):
                self.body = [self.body, item]
            else:
                self.body = [item]
        elif isinstance(item, (list, set, tuple)):
            for i in item:
                self.add(i)
        else:
            raise ValueError(f"cannot add item to Java method: '{item}'")

    @property
    def all_args(self) -> list[JavaVariable]:
        """
        Returns all arguments.  This is equivalent to `self.args` unless this is a constructor with a base class and
        the base class has fields on its own.
        """
        if self.is_constructor and self.cls.has_base_class:
            return self.args + [f for f in self.cls.base_fields]
        else:
            return self.args

    @property
    def declaration(self) -> str:
        """
        Returns the full declaration of the function or method.
        """
        o = '@Override\n  ' if self.is_override and not self.is_constructor else ''
        return f"{self.get_comment()}  {o}{self._get_head()} {{\n{self._get_body()}\n  }}"

    def get_all_declarations(self) -> str:
        """
        If some arguments are optional, this will create a number of different method declarations/implementations.
        Otherwise, the result will be the same as from `declaration`.

        Note: this method is not a property to make it more explicit that a number of different method declarations
        are created.
        """
        if (all_args := self.all_args) and (n_opt := len([a for a in all_args if a.is_optional])) > 0:
            p = 'public ' if self.is_public else 'private'
            s = 'static ' if self.is_static else ''
            if self.is_constructor:
                head = f"{p}{s}{self.name}"
                ret = 'this'
            else:
                o = '@Override\n  ' if self.is_override else ''
                generics = f"<{','.join(self.generics)}> " if self.generics else ''
                head = f"{o}{p}{s}{generics}{self.return_j_type} {self.name}"
                ret = ('return ' if self.return_j_type != 'void' else '') + self.name
            decls = [
                self.declaration
            ]

            for i in range(n_opt):
                params = []
                args = []
                j = i
                for a in all_args:
                    if a.is_optional:
                        j -= 1
                        if j < 0:
                            args.append(a.init_value)
                            continue
                    params.append(a.param_decl)
                    args.append(a.j_name)
                decls.append(
                    f"  {head}({', '.join(params)}) {{\n    {ret}({', '.join(args)});\n  }}"
                )

            return '\n\n'.join(decls)

        return self.declaration

    def interface_declarations(self) -> Optional[str]:
        """
        Returns all the overloaded variants of this method, but without body of private/public marker for
        interface declarations.
        """
        if self.is_public and not self.is_static and not self.is_constructor:
            decls = []
            mandatory_arg_count = len([a for a in self.args if not a.is_optional])
            n = len(self.args)
            while n >= mandatory_arg_count:
                decls.append(self._get_iface_head(n))
                n -= 1
            return '  ' + '\n  '.join(decls) + '\n'
        else:
            return None

    @property
    def is_constructor(self) -> bool:
        return isinstance(self.cls, JavaClass) and self.cls.name == self.name

    @property
    def is_private(self) -> bool:
        return not self.is_public

    @is_private.setter
    def is_private(self, value: bool):
        self.is_public = not value


class JavaMemoizedMethod(JavaMethod):
    """
    A memoized method 'wraps' the actual method and stores any results (other than 'null') in a cache to be
    retrieved later if the method is called with the same argument.

    You should specify a hash_arg that is used to write and look up the memoization.  Otherwise, the function will
    simply go for the first argument, which is most probably not what you want, but serves the purpose of showing
    how it works.
    """

    def __init__(
            self,
            cls: 'JavaClass',
            name: str,
            return_j_type: str,
            args: Optional[JavaVariable|List[JavaVariable]] = None,
            body: Optional[str|List[str]|Callable] = None,
            *,
            comment: Optional[str] = None,
            hash_arg: Optional[str] = None,
            hash_j_type: Optional[str] = None,
            is_static: Optional[bool] = False,
            is_public: Optional[bool] = True,
            is_override: Optional[bool] = None
    ):
        super().__init__(cls, name, return_j_type, args, body, comment=comment,
                         is_static=is_static, is_public=is_public, is_override=is_override)
        self.hash_arg = hash_arg
        self.hash_j_type = hash_j_type
        self.memo_class = mc = JavaClass(
            'Memo',
            modifiers='private static'.split(),
            generics=['U']
        )
        mc.add( JavaVariable('item', 'U', is_final=True, is_exposed=True) )
        if cls:
            cls.add(self.memo_class)
            cls.imports.add('java.util.HashMap')
            cls.imports.add('java.util.Map')

    def _get_hash_arg_code(self) -> str:
        if self.hash_arg and '=' in self.hash_arg:
            return f"{self.hash_j_type} {self.hash_arg};"
        else:
            return ''

    def _get_head_private(self) -> str:
        s = 'static ' if self.is_static else ''
        ret_type = self.return_j_type
        return f"private {s}{ret_type} _{self.name}({self._get_args()})"

    @property
    def declaration(self) -> str:
        """
        Returns the full declaration of the function or method.
        """
        s = 'static ' if self.is_static else ''
        o = '@Override\n  ' if self.is_override and not self.is_constructor else ''
        if self.hash_j_type is None and self.args:
            hash_j_type = self.args[0].j_type
        else:
            hash_j_type = self.hash_j_type
        ret_type = _JAVA_PRIMITIVES.get(self.return_j_type, self.return_j_type)
        return f"\n  private {s}final Map<{hash_j_type}, Memo<{ret_type}>> " \
               f"{self.name}_cache = new HashMap<>();\n" \
               f"  {o}{self._get_head()} {{\n{self.get_memoization_body()}\n  }}\n" \
               f"  {self._get_head_private()} {{\n{self._get_body()}\n  }}"

    def get_memoization_body(self) -> str:
        """
        This method is responsible for generating the code that goes into the memoization part.
        """
        args = ', '.join([a.name for a in self.args])
        if self.hash_arg:
            hash_arg = self.hash_arg
            if '=' in hash_arg:
                hash_arg = hash_arg[:hash_arg.index('=')].strip()
        elif self.args:
            hash_arg = self.args[0].name
        else:
            raise ValueError("missing argument for memoization")
        get_arg = self._get_hash_arg_code()
        if get_arg: get_arg += '\n    '
        ret_type = _JAVA_PRIMITIVES.get(self.return_j_type, self.return_j_type)
        code = f"    {get_arg}Memo<{ret_type}> info = {self.name}_cache.get({hash_arg});\n" \
               f"    if (info != null) {{\n      return info.item;\n    }}\n" \
               f"    {self.return_j_type} result = _{self.name}({args});\n"
        # If the function returns a primitive type, we do not need to check whether the call was successful
        if ret_type != self.return_j_type:
            code += f"    {self.name}_cache.put({hash_arg}, new Memo<>(result));\n" \
                    f"    return result;"
        else:
            code += \
               f"    if (result != null) {{\n      {self.name}_cache.put({hash_arg}, new Memo<>(result));\n    }}\n" \
               f"    return result;"
        return code


class JavaMemoizedParserMethod(JavaMemoizedMethod):
    """
    This is a specialised version of the memoized method used for the parser, i.e. with a very specific
    memoization body.
    """

    def __init__(
            self,
            cls: 'JavaClass',
            name: str,
            return_j_type: str,
            args: Optional[JavaVariable|List[JavaVariable]] = None,
            body: Optional[str|List[str]|Callable] = None,
            *,
            comment: Optional[str] = None,
            is_non_empty_loop: Optional[bool] = False,
            is_static: Optional[bool] = False,
            is_public: Optional[bool] = True,
            is_override: Optional[bool] = None
    ):
        super().__init__(cls, name, return_j_type, args, body, comment=comment,
                         hash_arg='m = this.mark()', hash_j_type='int',
                         is_static=is_static, is_public=is_public, is_override=is_override)
        self.is_non_empty_loop = is_non_empty_loop
        self.memo_class.add( JavaVariable('end_mark', 'int', is_exposed=True, is_final=True) )
        self.memo_class.add( JavaMethod(self.memo_class, 'toString', 'String',
                                        body='if (item != null)\n      return item.toString() + ":~" + end_mark;\n'
                                             '    else\n      return "<null>:~" + end_mark;') )

    def get_memoization_body(self) -> str:
        """
        This method is responsible for generating the code that goes into the memoization part.
        """
        args = ', '.join([a.name for a in self.args])
        if self.hash_arg:
            hash_arg = self.hash_arg
            if '=' in hash_arg:
                hash_arg = hash_arg[:hash_arg.index('=')].strip()
        elif self.args:
            hash_arg = self.args[0].name
        else:
            raise ValueError("missing argument for memoization")
        get_arg = self._get_hash_arg_code()
        if get_arg: get_arg += '\n    '
        ret_type = _JAVA_PRIMITIVES.get(self.return_j_type, self.return_j_type)
        code = f"    {get_arg}Memo<{ret_type}> info = {self.name}_cache.get({hash_arg});\n" \
               f"    if (info != null) {{\n" \
               f"      log(\"{self.name}() [cached]-> \" + info.toString());\n" \
               f"      this.reset(info.end_mark);\n" \
               f"      return info.item;\n" \
               f"    }}\n" \
               f"    logl(\"{self.name}() ...\");\n" \
               f"    this._level += 1;\n" \
               f"    {self.return_j_type} result = _{self.name}({args});\n" \
               f"    this._level -= 1;\n" \
               f"    log(\"{self.name}() [fresh]-> \", result);\n"
        # If the function returns a primitive type, we do not need to check whether the call was successful
        if ret_type != self.return_j_type:
            code += f"    {self.name}_cache.put({hash_arg}, new Memo<>(result, this.mark()));\n" \
                    f"    return result;"
        elif self.is_non_empty_loop:
            code += \
                f"    if (result.length > 0) {{\n" \
                f"      {self.name}_cache.put({hash_arg}, new Memo<>(result, this.mark()));\n" \
                f"      return result;" \
                f"    }}\n" \
                f"    return null;"
        else:
            code += \
               f"    if (result != null)\n" \
               f"      {self.name}_cache.put({hash_arg}, new Memo<>(result, this.mark()));\n" \
               f"    return result;"
        return code


class JavaClass:
    """
    Represents a Java class and allows you to write the class to file.
    """

    def __init__(
            self,
            name: str,
            base_cls = None,
            package: Optional[str] = None,
            visitable: bool = True,
            *,
            comment: Optional[str] = None,
            generics: Optional[List[str]|str] = None,
            modifiers: Iterable = frozenset()
    ):
        self.name = name
        self.base_cls = base_cls if base_cls != '' else None
        self.package = package
        self.visitable: bool = visitable
        self.constructor = JavaMethod(self, name, name)
        if isinstance(generics, str):
            generics = generics.split(',')
        self.comment = comment      # type: Optional[str]
        self.fields = []            # type: List[JavaVariable]
        self.interfaces = set()     # type: Set[JavaInterface]
        self.methods = []           # type: List[JavaMethod]
        self.imports = set()        # type: Set[str]
        self.nested_cls = {}        # type: Dict[str, JavaClass]
        self.generics = generics    # type: Optional[List[str]]
        self.modifiers = set(modifiers)  # type: set
        self.expose_fields = None   # type: Optional[str]

    def __repr__(self):
        return f"{self.__class__.__name__}('{self.name}')"

    def __str__(self):
        return self.declaration

    def add(self, item):
        """
        Adds another method or field to the class.
        """
        if isinstance(item, JavaMethod):
            self.methods.append(item)
        elif isinstance(item, JavaVariable):
            i_name = item.name
            if any(f.name == i_name for f in self.fields):
                raise KeyError(f"field '{i_name}' already defined in class '{self.name}'")
            self.fields.append(item)
            self.constructor.add(item)
        elif isinstance(item, JavaClass):
            self.nested_cls[item.name] = item
        elif isinstance(item, (list, set, tuple)):
            for i in item:
                self.add(i)
        else:
            raise ValueError(f"cannot add item to Java class: '{item}'")

    def add_field(self, name: str, j_type: str) -> JavaVariable:
        var = JavaVariable(name, j_type)
        self.add(var)
        return var

    def add_interface(self, iface):
        assert isinstance(iface, JavaInterface)
        if iface is not None:
            self.interfaces.add(iface)

    def add_method(self, name: str, return_j_type: str|None = None) -> JavaMethod:
        method = JavaMethod(self, name, return_j_type)
        self.add(method)
        return method

    def _create_expose_fields_method(self) -> Optional[str]:
        """
        Returns either `None` or the code of a method that returns a list with the names of all fields, depending
        on the flag `self.expose_fields`.
        """
        if self.expose_fields and self.fields:
            name = self.expose_fields() if callable(self.expose_fields) else str(self.expose_fields)
            fields = [ f.name for f in self.fields ]
            return f"  public String[] {name}() {{\n    return new String[] {{ {', '.join(fields)} }}\n  }}"
        else:
            return None

    @property
    def all_fields(self) -> List[JavaVariable]:
        """
        Returns a (possibly empty) list of all fields, including those of base classes.
        """
        if isinstance(self.base_cls, JavaClass):
            return self.fields + self.base_cls.all_fields
        else:
            return self.fields

    @property
    def base_fields(self) -> List[JavaVariable]:
        """
        Returns a (possibly empty) list of all fields defined in the base class.
        """
        if isinstance(self.base_cls, JavaClass):
            return self.base_cls.all_fields
        else:
            return []

    @property
    def declaration(self) -> str:
        if isinstance(self.base_cls, JavaClass):
            base_cls = ' extends ' + self.base_cls.name
        elif self.base_cls:
            base_cls = ' extends ' + str(self.base_cls)
        else:
            base_cls = ''
        if self.interfaces:
            interfaces = ' implements ' + ', '.join(i.name for i in self.interfaces)
        else:
            interfaces = ''
        lines = []
        if self.package:
            lines.append(f"package {self.package};\n")
        if self.imports:
            for i in sorted(self.imports):
                lines.append(f"import {i};")
            lines.append('')
        prefix = (' '.join(m for m in _JAVA_MODIFIERS if m in self.modifiers) + ' ') if self.modifiers else ''
        generics = f"<{', '.join(self.generics)}>" if self.generics else ''
        lines.append(f"{self.get_comment()}{prefix}class {self.name}{generics}{base_cls}{interfaces} {{")
        # Add fields
        for field in self.fields:
            lines.append(field.declaration)
        lines.append('')
        # If there are values to be included, get them (see `JavaPseudoEnum` below)
        gv = getattr(self, '_get_values', None)
        if gv and (value_decls := gv()):
            lines.append(value_decls)
            lines.append('')
        # Add any nested classes (if there are any)
        if self.nested_cls:
            lines.append('')
            for cls in self.nested_cls.values():
                decl = cls.declaration.replace('\n', '\n  ')
                lines.append('  ' + decl)
                lines.append('')
        # Add constructors if there are any fields (even by inheritance).
        if self.all_fields:
            lines.append(self.constructor.get_all_declarations())
            lines.append('')
        # Add getters and setters
        for field in self.fields:
            if not field.is_private and not field.is_exposed:
                lines.append(field.getter)
                if not field.is_readonly:
                    lines.append(field.setter)
        # Add methods
        for method in self.methods:
            lines.append(method.get_all_declarations())
        # If the field names should we exposed by a specific method, we do that, too
        ef = self._create_expose_fields_method()
        if ef is not None:
            lines.append(ef)
        lines.append('}')
        return '\n'.join(lines)

    def find(self, name: str):
        """
        Looks for a field, method or nested class with the given name and returns it.  Note that it is possible to have
        several methods with the same name, in which case only the first method will be returned.
        """
        for field in self.fields:
            if field.name == name:
                return field
        for method in self.methods:
            if method.name == name:
                return method
        return self.nested_cls.get(name, None)

    def get_comment(self) -> str:
        """
        Returns the comment associated with this object or the empty string.
        """
        if comment := self.comment:
            comment = comment.strip()
            if comment[0] == '/':
                return comment + '\n'
            elif '\n' in comment:
                return '/* ' + comment.replace('\n', '\n * ') + '\n */\n'
            else:
                return '// ' + comment + '\n'
        else:
            return ''

    @property
    def has_base_class(self):
        """
        Returns `True` if this class has a base class.
        """
        return self.base_cls is not None

    @property
    def base_class_name(self) -> Optional[str]:
        """
        Returns the name of the base class if there is any or `None` otherwise.
        """
        if isinstance(self.base_cls, str):
            return self.base_cls
        else:
            return getattr(self.base_cls, 'name', None)

    def save_to_file(self, path: Optional[Path] = None, generator: Optional[str] = None):
        """
        Saves the entire class to a file `name.java` in the specified path.
        """
        name = f"{self.name}.java"
        path = path.joinpath(name) if path else name
        if generator is None:
            generator = '/'.join(Path(__file__).parts[-2:])
        time = datetime.today().strftime('%d %b %Y %H:%M:%S')
        with open(path, 'w') as f:
            f.write(f"// File automatically generated by '{generator}' at {time}.\n\n")
            f.write(self.declaration)


class JavaPseudoEnum(JavaClass):
    """
    This is not really an enum as we need the ability to have a specific base class.
    """

    def __init__(self, name: str, base_cls: str|JavaClass|None = None, package: Optional[str] = None, **kwds):
        super().__init__(name, base_cls, package, visitable=False, **kwds)
        self.values = []            # type: List[Tuple[str, list]]
        self.add(JavaVariable('name', 'String', is_final=True, is_private=True))
        self.add(JavaMethod(self, 'toString', 'String', body='return this.name;'))
        self.add(JavaMethod(self, 'valueOf', name, JavaVariable('s', 'String'), body=self._get_valueOf_method))

    def add(self, item):
        if isinstance(item, str):
            self.add_value(item, [])
        else:
            super().add(item)

    def add_value(self, value: str, args: Optional[list] = None):
        if args is None:
            args = []
        if isinstance(value, str) and value != '':
            for i, (n, a) in enumerate(self.values):
                if n == value:
                    self.values[i] = (value, args)
                    return
        self.values.append( (value, args) )

    def _get_valueOf_method(self) -> str:
        """
        Creates and returns the code body for the `valueOf`-method.
        """
        code = []
        for (value, _) in self.values:
            code.append(f"if (s.equals(\"{value}\"))\n      return {value};")
        code.append("throw new IllegalArgumentException(s);")
        return '    ' + '\n    '.join(code)

    def _get_values(self) -> str:
        """
        Creates the code for the value definitions that will be inserted into the class.
        """
        code = []
        for (value, args) in self.values:
            args = [f"\"{value}\""] + args
            code.append(f"  public static final {self.name} {value} = new {self.name}({', '.join(args)});")
        return '\n'.join(code)


class JavaVisitor(JavaClass):
    """
    Represents an actual visitor.  Simply add any classes that should be
    visited by this visitor, and it will create a method doing so.
    """
    def __init__(self, name: str, base_cls: str|JavaClass|None = None, package: Optional[str] = None, **kwds):
        super().__init__(name, base_cls, package, visitable=False, **kwds)

    def add_visit_method(self, item: JavaClass):
        """Add a method implementation corresponding to the class"""
        self.methods.append(
            JavaMethod(self, 'visit', 'void', JavaVariable('node', item.name),
                       body='// TODO visit fields of a ' + repr(item))
        )


class JavaInterface:
    """
    Represents a Java interface.

    The interface can hold fields and methods (`JavaVariable` and `JavaMethod` above).  However, it will translate the
    fields to getters and setters, and it will only include public and non-static members.  That is, private and
    static members as well as constructors are simply ignored.  This way, you can easily append all fields and methods
    from a class to an interface without having to worry which ones are actually public.
    """

    def __init__(
            self,
            name: str,
            package: Optional[str] = None,
            *,
            comment: Optional[str] = None,
            generics: Optional[List[str]] = None,
            modifiers: Iterable[str] = frozenset()
    ):
        self.name = name
        self.package = package
        self.comment = comment
        self.generics = generics
        self.modifiers = set(modifiers)  # type: Set[str]
        self.fields = []            # type: List[JavaVariable]
        self.methods = []           # type: List[JavaMethod]
        self.imports = set()        # type: Set[str]

    def __repr__(self):
        return f"{self.__class__.__name__}('{self.name}')"

    def __str__(self):
        return self.declaration

    def add(self, item):
        if isinstance(item, JavaMethod):
            self.methods.append(item)
        elif isinstance(item, JavaVariable):
            self.fields.append(item)
        elif isinstance(item, (list, set, tuple)):
            for i in item:
                self.add(i)
        else:
            raise ValueError(f"cannot add value '{item}' to Java interface")

    def add_all(self, other):
        """
        Adds all fields and methods of another class or interface to this interface.
        """
        if isinstance(other, (JavaClass, JavaInterface)):
            self.add(other.fields)
            self.add(other.methods)
        elif isinstance(other, (list, set, tuple)):
            for o in other:
                if isinstance(o, (JavaClass, JavaInterface)):
                    self.add_all(o)
                else:
                    self.add(o)
        else:
            raise ValueError(f"cannot add values from '{other}' to Java interface")


    def find(self, name: str):
        """
        Looks for a method with the given name and returns it.  Note that it is possible to have
        several methods with the same name, in which case only the first method will be returned.
        """
        for method in self.methods:
            if method.name == name:
                return method
        return None

    @property
    def declaration(self) -> str:
        """
        Returns the interface's full declaration.
        """
        code = []
        if self.package:
            code.append(f"package {self.package};\n")
        if self.imports:
            for i in sorted(self.imports):
                code.append(f"import {i};")
            code.append('')
        prefix = (' '.join(m for m in _JAVA_MODIFIERS if m in self.modifiers) + ' ') if self.modifiers else ''
        generics = f"<{','.join(self.generics)}>" if self.generics else ''
        code.append(f"{self.get_comment()}{prefix}interface {self.name}{generics} {{\n")
        for field in self.fields:
            decls = field.interface_declarations()
            if decls:
                code.append(decls)
        for method in self.methods:
            decls = method.interface_declarations()
            if decls:
                code.append(decls)
        code.append('}')
        return '\n'.join(code)

    def get_comment(self) -> str:
        """
        Returns the comment associated with this object or the empty string.
        """
        if comment := self.comment:
            comment = comment.strip()
            if comment[0] == '/':
                return comment + '\n'
            elif '\n' in comment:
                return '/* ' + comment.replace('\n', '\n * ') + '\n */\n'
            else:
                return '// ' + comment + '\n'
        else:
            return ''

    @property
    def has_base_class(self) -> bool:
        return False

    def save_to_file(self, path: Optional[Path] = None, generator: Optional[str] = None):
        """
        Saves the entire class to a file `name.java` in the specified path.
        """
        name = f"{self.name}.java"
        path = path.joinpath(name) if path else name
        if generator is None:
            generator = '/'.join(Path(__file__).parts[-2:])
        with open(path, 'w') as f:
            f.write(f"// File automatically generated by '{generator}' on {datetime.today().strftime('%d %b %Y')}.\n\n")
            f.write(self.declaration)


class JavaVisitorInterface(JavaInterface):
    """
    This is a specialised version of the interface for creating visitors.  Simply add any classes that should be
    visited by this visitor, and it will create the methods for you.
    """

    def add_visit_method(self, item: JavaClass):
        """Add a method implementation corresponding to the class"""
        self.methods.append(
            JavaMethod(self, 'visit', 'void', JavaVariable('node', item.name))
        )
        item.methods.append(
            JavaMethod(item, 'accept', 'void', JavaVariable('visitor', self.name),
                       body = 'visitor.visit(this);')
        )



class JavaCodeScope:
    """
    When constructing the Java code to output there might be unresolved references or interdependent parts.  Using a
    JavaCodeScope we can have small code blocks (in curly braces) that are added to the body of Java methods but not
    fully resolved and turned into code until the full code has been generated and is ready to be shipped to the file.
    """

    def __init__(self):
        self.body = None
        self.level = 0

    def add(self, item):
        """
        Adds a line or nested scope to the body of this scope.
        """
        if isinstance(item, str) or hasattr(item, 'generate_code'):
            if isinstance(item, JavaCodeScope):
                item.level = max(self.level + 1, item.level)
            if isinstance(self.body, list):
                self.body.append(item)
            elif isinstance(self.body, str):
                self.body = [ self.body, item ]
            else:
                self.body = [ item ]
        elif isinstance(item, (list, tuple)):
            for i in item:
                self.add(i)
        else:
            raise ValueError(f"cannot add item to code: '{item}'")

    def generate_code(self) -> str:
        indent = '  ' * self.level
        prefix = indent + self._generate_prefix()
        indent = '\n      ' + indent
        body = indent.join(
            item.generate_code() if hasattr(item, 'generate_code') else item for item in self.body
        )
        return f"{prefix}{{{indent}{body}{indent}}}"

    def _generate_prefix(self) -> str:
        return ''


def main():
    # Here is a simple class with some fields and methods
    jc = JavaClass('Test', 'Ancestor')
    jc.add_field('caption', 'String').is_final = True
    jc.add_field('posX', 'int').is_optional = True
    jc.add_field('posY', 'int').is_optional = True
    jm = jc.add_method('show')
    jm.add('this.visible = true;')
    jc.add(JavaMemoizedMethod(jc, 'eval', 'int', [JavaVariable('code', 'String')],
                              body='return Integer.parse(code);',
                              hash_arg='code', hash_j_type='String'))
    print(jc.declaration)
    # jc.save_to_file()
    print('-' * 50)

    # The interface is generated automatically from the class above
    iface = JavaInterface('TestI')
    iface.add_all(jc)
    print(iface.declaration)
    print('-' * 50)

    # Enums are fully reimplemented, allowing us the flexibility to have custom base classes
    en = JavaPseudoEnum('BinOp')
    en.add(['Add', 'Sub', 'Mul', 'Div'])
    print(en.declaration)


if __name__ == '__main__':
    main()
