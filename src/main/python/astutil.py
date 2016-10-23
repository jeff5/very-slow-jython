# Utilities for manipulating ASTs

import ast

class PrettyPrintVisitor(object):
    """A visitor that returns a pretty-printable textual description.

       There is also an indication whether this had to be wrapped. All the
       visit-like methods must accept tree-depth and available width
       parameters and return a pair (str, bool), providing the formatted
       object and whether or not the line-wrapping was necessary. There
       are two types of visit-like methods: those named visit_<node>,
       where <node> is the type name of an AST node, and those named
       leaf_<type> where <type> is any other type (such as int or str).
    """

    def __init__(self, annotate_fields=True, include_attributes=False,
                 width=72, indent=2):
        self.annotate_fields = annotate_fields
        self.include_attributes = include_attributes
        self.width = width
        self.indent = indent

    def visit(self, node, depth=0, available=None):
        """Visit a node."""
        method_name = 'visit_' + node.__class__.__name__
        method = getattr(self, method_name, self.generic_visit_node)
        return method(node, depth, available or self.width)

    def leaf(self, value, depth, available):
        """Visit a leaf (non-node) value."""
        method_name = 'leaf_' + value.__class__.__name__
        method = getattr(self, method_name, self.generic_leaf)
        return method(value, depth, available)

    def generic_visit_value(self, value, depth, available):
        """Called for each field value or list-field item in a node."""
        if isinstance(value, ast.AST):
            return self.visit(value, depth, available)
        elif isinstance(value, list):
            return self.generic_visit_list(value, depth, available)
        else:
            return self.leaf(value, depth, available)

    def _result(self, depth, elements, wrapped, left='[', right=']'):
        """Helper to provide the text of either a list or a node, and
           the decision to wrap as a boolean. All the visit-like methods
           return such a pair.
        """
        if not wrapped:
            return left + ', '.join(elements) + right
        else:
            spaces = (depth * self.indent) * ' '
            first = left + '\n' + spaces
            sep = ',\n' + spaces
            #last = '\n' + spaces[:-indent] + right # K&R
            last = right # Lisp
            return first + sep.join(elements) + last

    def generic_visit_node(self, node, depth, available):
        """Format an AST node, also returning whether it was wrapped.

        An AST node looks like Name(a=x, b=y, c=z) or
        Name(
          a=x,
          b=y,
          c=z)
        """
        # Increase the indent level by one
        depth += 1
        # We do this by pursuing two hypotheses in parallel.
        # Hypothesis 1: we have to wrap this node. In that case, the space
        # available is:
        available_w = self.width - depth * self.indent - 1 # . . . <text>)
        # We use this hypothesis when formatting each member of the node, so
        # it gives the "optimistic" answer. As soon as any member has to be
        # wrapped even to fit the optimistic space, hypothesis 1 is proved.
        # Hypothesis 2: this node all fits on one line. We keep checking that
        # the "optimistically formatted" members fit as they use up available
        # space. As soon as we run out, hypothesis 1 is proved.
        # Hypothesis 2 is true if we reach the end without proving 1.
        name = node.__class__.__name__
        available -= len(name) + 2 # needed by 'Name(' and ')'
        wrapped = (available <= 0)

        # Each field (and possibly attribute) undergoes this:
        def add(n):
            nonlocal wrapped, available
            v = getattr(node, n)
            t, w = self.generic_visit_value(v, depth, available_w)
            if self.annotate_fields:
                t = '{:s}={:s}'.format(n,t)
            if not wrapped:
                if w:
                    # t is wrapped within itself so we have to be wrapped.
                    wrapped = True
                else:
                    # t is not wrapped, but does it overflow the line?
                    available -= len(t) + 2  # length of '<t>, '
                    wrapped = (available <= 0)
            elements.append(t)

        # Work down the list taking away the space used
        elements = []
        for f in node._fields: add(f)
        if self.include_attributes and node._attributes:
            for a in node._attributes: add(a)
        return self._result(depth, elements, wrapped, name+'(', ')'), wrapped

    def generic_visit_list(self, value, depth, available):
        """Format a list value, returning whether it was wrapped.

        A list looks like [a, b, c] or
        [
          a,
          b,
          c]
        """
        # Increase the indent level by one
        depth += 1
        # We do this by pursuing two hypotheses in parallel.
        # Hypothesis 1: we have to wrap this list. In that case, the space
        # available is:
        available_w = self.width - depth * self.indent - 1 # . . . <text>)
        # We use this hypothesis when formatting each element of the list, so
        # it gives the "optimistic" answer. As soon as any element has to be
        # wrapped even to fit the optimistic space, hypothesis 1 is proved.
        # Hypothesis 2: this list all fits on one line. We keep checking that
        # the "optimistically formatted" elements fit as they use up available
        # space. As soon as we run out, hypothesis 1 is proved.
        # Hypothesis 2 is true if we reach the end without proving 1.
        available -= 2 # needed by '[' and ']'
        wrapped = (available <= 0)

        # Work down the list taking away the space used
        elements = []
        for v in value:
            t, w = self.generic_visit_value(v, depth, available_w)
            if not wrapped:
                if w:
                    # t is wrapped within itself so we have to be wrapped.
                    wrapped = True
                else:
                    # t is not wrapped, but does it overflow the line?
                    available -= len(t) + 2  # length of '<t>, '
                    wrapped = (available <= 0)
            elements.append(t)
        return self._result(depth, elements, wrapped), wrapped

    def generic_leaf(self, value, depth, available):
        """Called when a field is neither a node nor a list."""
        return repr(value), False


class EmitJavaVisitor(PrettyPrintVisitor):
    """A visitor that returns a Java expression equivalent to the AST.
    """

    def __init__(self, include_attributes=False, width=72, indent=4):
        PrettyPrintVisitor.__init__(self, False, include_attributes, width, indent)

    def node_sugar(self, node):
        """Return a pair of strings to wrap the constructor.

        The returned strings are based on the node type, to go before
        and after the arguments of the constructor, so for a node of
        type X, return 'X(' and ')'. As a special case, a node with no
        fields (an enumeration constant) will return simply 'X' and ''.
        """
        left = node.__class__.__name__
        if node._fields or self.include_attributes and node._attributes:
            left += '('
            right = ')'
        else:
            right = ''
        return left, right

    def list_sugar(self):
        """Return a pair of strings to wrap a list."""
        return 'list(', ')'

    def generic_visit_node(self, node, depth, available):
        """Format an AST node, also returning whether it was wrapped.

        An AST node looks like Name(a=x, b=y, c=z) or
        Name(
          a=x,
          b=y,
          c=z)
        """
        # Increase the indent level by one
        depth += 1
        # We do this by pursuing two hypotheses in parallel.
        # Hypothesis 1: we have to wrap this node. In that case, the space
        # available is:
        available_w = self.width - depth * self.indent - 1 # . . . <text>)
        # We use this hypothesis when formatting each member of the node, so
        # it gives the "optimistic" answer. As soon as any member has to be
        # wrapped even to fit the optimistic space, hypothesis 1 is proved.
        # Hypothesis 2: this node all fits on one line. We keep checking that
        # the "optimistically formatted" members fit as they use up available
        # space. As soon as we run out, hypothesis 1 is proved.
        # Hypothesis 2 is true if we reach the end without proving 1.
        left, right = self.node_sugar(node)
        available -= len(left) + len(right) # space for 'Name(' and ')'
        wrapped = (available <= 0)

        # Each field (and possibly attribute) undergoes this:
        def add(n):
            nonlocal wrapped, available
            v = getattr(node, n)
            t, w = self.generic_visit_value(v, depth, available_w)
            if self.annotate_fields:
                t = '{:s}={:s}'.format(n,t)
            if not wrapped:
                if w:
                    # t is wrapped within itself so we have to be wrapped.
                    wrapped = True
                else:
                    # t is not wrapped, but does it overflow the line?
                    available -= len(t) + 2  # length of '<t>, '
                    wrapped = (available <= 0)
            elements.append(t)

        # Work down the list taking away the space used
        elements = []
        for f in node._fields: add(f)
        if self.include_attributes and node._attributes:
            for a in node._attributes: add(a)
        return self._result(depth, elements, wrapped, left, right), wrapped

    def generic_visit_list(self, value, depth, available):
        """Format a list value, returning whether it was wrapped.

        A list looks like [a, b, c] or
        [
          a,
          b,
          c]
        """
        # Increase the indent level by one
        depth += 1
        # We do this by pursuing two hypotheses in parallel.
        # Hypothesis 1: we have to wrap this list. In that case, the space
        # available is:
        available_w = self.width - depth * self.indent - 1 # . . . <text>)
        # We use this hypothesis when formatting each element of the list, so
        # it gives the "optimistic" answer. As soon as any element has to be
        # wrapped even to fit the optimistic space, hypothesis 1 is proved.
        # Hypothesis 2: this list all fits on one line. We keep checking that
        # the "optimistically formatted" elements fit as they use up available
        # space. As soon as we run out, hypothesis 1 is proved.
        # Hypothesis 2 is true if we reach the end without proving 1.
        left, right = self.list_sugar()
        available -= len(left) + len(right) # space for '[' and ']'
        wrapped = (available <= 0)

        # Work down the list taking away the space used
        elements = []
        for v in value:
            t, w = self.generic_visit_value(v, depth, available_w)
            if not wrapped:
                if w:
                    # t is wrapped within itself so we have to be wrapped.
                    wrapped = True
                else:
                    # t is not wrapped, but does it overflow the line?
                    available -= len(t) + 2  # length of '<t>, '
                    wrapped = (available <= 0)
            elements.append(t)
        return self._result(depth, elements, wrapped, left, right), wrapped

    def leaf_str(self, value, depth, available):
        """Called when a field is a str."""
        r = repr(value)
        if r[0]=="'":
            r = '"' + r[1:-1].replace('"', '\\"').replace("\\'", "'") + '"'
        return r, False


def pretty(prog):
    if isinstance(prog, ast.AST):
        prog = ast.parse(prog)
    v = PrettyPrintVisitor(width=80)
    r = v.visit(prog)
    print(r[0])

def pretty_java(prog):
    if isinstance(prog, ast.AST):
        prog = ast.parse(prog)
    v = EmitJavaVisitor()
    r = v.visit(prog)
    print(r[0])

if __name__ == "__main__":
    
    # Test the PrettyPrintVisitor against the standard library ast.dump in
    # a couple of examples.
    
    prog = """\
x = 41
y = x + 1
print(y)
"""

    prog2 = """\
class A(object):
    def f(self, y):
        x = 1
        while x<10000:
            x = x * y
        return x

def h(a, b=5):
    c = b*2
    return a*c

a = A()
for i in range(2, 11):
    print(h(i), a.f(i))
a.label = 'processed'
"""

    #exec(prog)
    #pp(prog)

    for p in (prog, prog2):
        tree = ast.parse(p)
        v = PrettyPrintVisitor(width=80)
        pp = v.visit(tree)[0]
        # Check same as the library version, apart from white space.
        result = pp.replace(' ', '').replace('\n', '')
        exp = ast.dump(ast.parse(p)).replace(' ', '')
        assert result == exp
        # Show Java output
        pretty_java(p)
