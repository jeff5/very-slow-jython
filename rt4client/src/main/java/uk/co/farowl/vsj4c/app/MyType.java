package uk.co.farowl.vsj4c.app;

import java.lang.invoke.MethodHandles;

import uk.co.farowl.vsj4.runtime.Exposed;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.PyUtil;
import uk.co.farowl.vsj4.runtime.TypeSpec;

/** An example Python type defined in an application. */
class MyType {

        private int content;

        MyType(int content) { this.content = content; }

        Object __str__() { return "MyType(" + content + ")"; }

        @Exposed.PythonMethod
        void set_content(int v) {
            this.content = 2 * v;
        }

        @Override
        public String toString() {
            return PyUtil.defaultToString(this);
        }

        static final PyType TYPE = PyType.fromSpec(new TypeSpec("MyType",
                MethodHandles.lookup()));
    }