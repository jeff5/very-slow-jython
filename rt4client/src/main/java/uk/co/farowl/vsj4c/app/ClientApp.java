package uk.co.farowl.vsj4c.app;

public class ClientApp {

    public static void main(String[] args) {
        // Styles of slot invocation feasible for the author:

//        Interp interp = new Interp();
//
//        /*
//         * This wraps errors nicely in a sub-class of RuntimeError so
//         * the developer doesn't have to declare them.
//         */
//        Object y = interp.neg(-42);
//        System.out.printf("neg(-42) = %d\n", y);
//
//        y = interp.add(33, 9);
//        System.out.printf("add(33, 9) = %d\n", y);
//
//        /*
//         * The application has an extension module in Java. A module of
//         * any kind has to be created as an instance in an interpreter.
//         */
//        Extension ext = new Extension();
//        System.out.println(ext);
//
//        /*
//         * Functions defined in Java and marked as Exposed.PythonMethod
//         * appear in the dictionary of the instance.
//         */
//        Object foo = ext.getDict().get("foo");
//        System.out.println(foo);
//        for (int i = 1; i < 8; i++) {
//            Object r = interp.call(foo, i);
//            System.out.printf("%3d %5d\n", i, r);
//        }
//
//        /*
//         * A user-defined type can become a Python type. The toy doen't
//         * support construction via Python at the moment.
//         */
//        Object mt = new MyType(3);
//        System.out.println(mt);
//        interp.callMethod(mt, "set_content", 21);
//        System.out.println(mt);
    }

    static class MyType {

//        private int content;
//
//        MyType(int content) { this.content = content; }
//
//        @Exposed.PythonMethod
//        Object __str__() { return "MyType(" + content + ")"; }
//
//        @Exposed.PythonMethod
//        static void set_content(MyType self, int v) {
//            self.content = 2 * v;
//        }
//
//        @Override
//        public String toString() {
//            return PyUtil.defaultToString(this);
//        }
//
//        static final PyType TYPE = PyType.register("MyType",
//                MyType.class, MethodHandles.lookup());
    }
}
