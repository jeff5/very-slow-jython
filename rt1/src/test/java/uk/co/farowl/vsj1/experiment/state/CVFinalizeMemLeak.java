package uk.co.farowl.vsj1.experiment.state;

/*
 * [This is the version *not* using weak references. The MyRuntimeImpl gets
 * collected and one of the MyClassValue objects, but not the class
 * loader.]
 *
 * This program demonstrates the way to use ClassValue without causing the
 * entire runtime to remain in memory when it has finished. This is
 * important in circumstances where the runtime is repeatedly reloaded
 * through a new class loader, as in a web-services container (e.g.
 * Tomcat).
 *
 * The problem arises when a ClassValue has as key a class that is
 * permanent in the JVM, such as where we provide behaviour to Java type
 * Integer. ClassValue stores a map on the class on the target class
 * (Integer, say) from ClassValue instance to the stored value. The map
 * references the ClassValue object weakly but the stored value strongly.
 * Thus, if the stored value has a custom type, whose class was loaded with
 * the runtime, as is likely, the long-lived target class keeping a strong
 * reference to it, keeps alive the value's class, its loader, and all the
 * classes and static data of the runtime. Where the key is a class from
 * the moribund runtime, the problem does not arise, since the map resides
 * on that class and the ClassValue object is within the runtime.
 *
 * Good practice is to store the value by WeakReference, so that when the
 * runtime state is no longer referenced from elsewhere, this reference
 * will not keep it alive.
 *
 * A similar potential issue (and potentially similar good practice) exists
 * where ThreadLocal is used and Java Thread objects are re-used from a
 * pool.
 *
 * See:
 * http://mail.openjdk.java.net/pipermail/mlvm-dev/2016-January/006568.html
 * but this is a lot evolved from that, to be more like our use for
 * ClassValue in an interpreter that might be unloaded (as a finished web
 * app, say).
 *
 */

import java.io.File;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CVFinalizeMemLeak {

    public static class MyRuntimeImpl extends AbstractMap<String, Object>
            implements Runnable {

        private static HashMap<Class<?>, MyValue> registry = null;

        private static void register(Class<?> key, MyValue value) {
            if (registry == null) {
                registry = new HashMap<>();
            }
            registry.put(key, value);
        }

        public static class MyValue {

            final String value;

            MyValue(String value) {
                this.value = value;
            }

            @Override
            protected void finalize() throws Throwable {
                System.out
                        .println(this + " '" + value + "' finalized (from "
                                + this.getClass().getClassLoader() + ")");
            }
        }

        public static class MyClassValue extends ClassValue<MyValue> {

            @Override
            protected MyValue computeValue(Class<?> type) {
                MyValue ret = registry.get(type);
                // It would be ok to deref to the value:
                registry.remove(type);
                return ret;
            }

            @Override
            protected void finalize() throws Throwable {
                System.out.println(this + " (class value) finalized (from "
                        + this.getClass().getClassLoader() + ")");
            }
        }

        private MyClassValue myValueMap = new MyClassValue();

        private static void initialise() {
            if (registry == null) {
                register(Integer.TYPE, new MyValue("int"));
                register(MyValue.class, new MyValue("my-value"));
            }
        }

        private Map<String, Object> globals = null;

        @Override
        public Set<java.util.Map.Entry<String, Object>> entrySet() {
            return globals.entrySet();
        }

        @Override
        public void run() {
            // Initialise
            initialise();
            globals = new HashMap<>();
            // Work
            MyValue w = myValueMap.get(Integer.TYPE);
            globals.put("strong", w);
            globals.put("m", myValueMap.get(MyValue.class));
            // Tear-down
        }

        @Override
        protected void finalize() throws Throwable {
            System.out
                    .println("MyRuntimeImpl " + this + " finalized (from "
                            + this.getClass().getClassLoader() + ")");
        }
    }

    static final String OUTERCLASS = CVFinalizeMemLeak.class.getName();

    public static void main(String[] args) throws Exception {

        // Get the class path as an array of URLs for the custom loaders
        String[] cp = System.getProperty("java.class.path")
                .split(Pattern.quote(File.pathSeparator));
        URL[] urls = Stream.of(cp).map(f -> {
            try {
                return Paths.get(f).toUri().toURL();
            } catch (MalformedURLException e) {
                throw new UncheckedIOException(e);
            }
        }).collect(Collectors.toList()).toArray(new URL[0]);

        for (int i = 0; i < 5; i++) {

            // Use a custom loader, different each time around the loop
            URLClassLoader classLoader = new URLClassLoader(urls,
                    ClassLoader.getSystemClassLoader().getParent()) {

                @Override
                protected void finalize() throws Throwable {
                    System.out.println(this + " (classloader) finalized");
                }
            };

            /*
             * This instance of the runtime has a different class and
             * loader from last time. None of the runtime components from
             * last time around are needed in memory, and hopefully we have
             * seen them unloaded.
             */
            Object runtime =
                    classLoader.loadClass(OUTERCLASS + "$MyRuntimeImpl")
                            .getDeclaredConstructor().newInstance();
            System.out.println("\n" + i + " classloader = " + classLoader);

            ((Runnable)runtime).run();

            @SuppressWarnings("unchecked")
            Map<String, Object> globals = (Map<String, Object>)runtime;
            Object value = globals.get("strong");

            /*
             * Drop references to the runtime instance, but keep the
             * MyValue and the Reference to it: what is garbage now? (It
             * should be: MyClassValue and the MyRuntimeImpl.)
             */
            runtime = null;
            globals = null;
            classLoader.close();
            classLoader = null;

            System.out.println("GC 1");
            System.gc();
            Thread.sleep(200);

            assert value != null;
            assert value.getClass().getClassLoader() == classLoader;

            /*
             * Drop reference to the MyValue: what is garbage now? Nothing
             * new. Integer.TYPE still refers to MyRuntimeImpl.myValueMap,
             * which keeps it alive, and its class, and even the loader we
             * used.)
             */
            value = null;

            System.out.println("GC 2");
            System.gc();
            Thread.sleep(200);
        }
    }
}
