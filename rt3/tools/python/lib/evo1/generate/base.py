# base.py: foundations for source-code generation from templates
#
# Module: evo1.generate.base

class ImplementationGenerator:

    # Adjust the indent to match that requested
    def set_indent(self, i):
        self.emitter.indent = i

    # Create a warning comment
    def emit_object_template(self, e, src):
        name = getattr(src, 'name', '?').replace('\\', '/')
        e.emit_line("/*")
        e.emit_line(" * Generated by java_object_gen using ")
        e.emit(f"generator {self.__class__.__name__}.")
        e.emit_line(f" * Source: {name}")
        e.emit_line(" */")

    # Emit methods selectable by a single type
    def special_methods(self, e):
        pass

    # Emit methods selectable by a pair of types (for call sites)
    def special_binops(self, e):
        pass

    def emit_object_plumbing(self, e):
        pass
