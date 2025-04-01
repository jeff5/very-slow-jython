package uk.co.farowl.vsj4c.app;

import uk.co.farowl.vsj4.runtime.Abstract;
import uk.co.farowl.vsj4.runtime.Callables;
import uk.co.farowl.vsj4.runtime.PyNumber;
import uk.co.farowl.vsj4c.ext.Extension;

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

        /*
         * The application has an extension module in Java. A module of
         * any kind has to be created as an instance in an interpreter.
         */
        Extension ext = new Extension();
        System.out.println(ext);

//        /*
//         * Functions defined in Java and marked as PythonMethod appear
//         * in the dictionary of the instance.
//         */
//        Object foo = ext.getDict().get("foo");
//        System.out.println(foo);
//        for (int i = 1; i < 8; i++) {
//            Object r = interp.call(foo, i);
//            System.out.printf("%3d %5d\n", i, r);
//        }

        /*
         * A user-defined type can be a Python type. An instance may be
         * constructed conventionally for Java.
         */
        Object mt = new MyType(3);
        System.out.println(mt);

        try {
            // Any method may be found on the type ...
            Object f = Abstract.getAttr(MyType.TYPE, "set_content");
            // ... and called on an instance ...
            Callables.call(f, mt, 12);
            System.out.println(mt);

            // ... or found, bound and called via the instance.
            f = Abstract.getAttr(mt, "set_content");
            Callables.call(f, 21);
            System.out.println(mt);

            // A method that is Python-inherited from object.
            f = Abstract.getAttr(mt, "__repr__");
            Object r = Callables.call(f);
            System.out.println(r); // <MyType object at ...>

            // Special methods support corresponding Abstract API.
            System.out.println(Abstract.str(mt));
            System.out.println(Abstract.repr(mt));
            System.out.println(PyNumber.negative(42));

        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    static void visibility() {
        // Place to test visibility to compiler
    }
}
