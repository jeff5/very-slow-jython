package uk.co.farowl.vsj2ex;

import uk.co.farowl.vsj2.evo4.Number;
import uk.co.farowl.vsj2.evo4.Py;

public class PyObjectUse {

    public static void main(String[] args) {

        try {
            var deep = Py.str("The answer is: ");
            System.out.print(deep);

            var thought = Number.multiply(Py.val(6), Py.val(7));
            System.out.println(thought);

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
