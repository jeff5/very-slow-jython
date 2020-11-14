package uk.co.farowl.asdl.code;

import java.util.HashMap;
import java.util.Map;

/**
 * A symbol table for an ASDL name-scope, that maps names used <em>in the ASDL source</em> to the
 * information we have about the thing named. The container is type-safe through the parameter
 * <code>T</code>, if we avoid casting. <code>Scope</code>s may be nested, although at present we
 * only need the module scope and a global scope for built-in types.
 *
 * @param <T> The type of object to be looked up in the table
 */
public class Scope<T> {

    /** An inner class representing a symbol in a particular {@link Scope}. */
    public final class Symbol {

        /** Name as given in ASDL source. */
        final String name;
        /** The definition of this <code>Symbol</code>. */
        private T def;

        /**
         * Create a <code>Symbol</code> within the owning <code>Scope</code>. The caller must add it
         * to the {@link Scope#table}.
         *
         * @param name the name in ASDL (and the name in generated code is the same initially).
         * @param definition
         */
        private Symbol(String name, T definition) {
            this.name = name;
            def = definition;
        }

        /**
         * Create a <code>Symbol</code>, undefined.
         *
         * @param asdlName the name in ASDL (and the name in generated code is the same initially).
         */
        private Symbol(String asdlName) {
            this(asdlName, null);
        }

        /** Return the name to be used in code. */
        @Override
        public String toString() {
            return String.format("%s=%s", name, def);
        }

        /** Return the {@link Scope} within which this symbol is defined.
         * @return defining scope. */
        public Scope<T> scope() {
            return Scope.this;
        }

        /**
         * Define this <code>Symbol</code> and return it. Return <code>null</code> if already
         * defined.
         *
         * @param definition the new definition
         * @return this <code>Symbol</code> (for convenience chaining calls, etc.)
         */
        private Symbol defineOrNull(T definition) {
            if (def == null) {
                def = definition;
                return this;
            } else {
                return null;
            }
        }

        /**
         * Return {@code true} iff this {@code Symbol} is defined. @return {@code true} if defined
         *
         * @return {@code true} iff this {@code Symbol} is defined
         */
        public boolean isDefined() {
            return def != null;
        }

        /** Return the definition of this <code>Symbol</code>.
         * @return definition of this <code>Symbol</code> */
        public T definitionOrNull() {
            return def;
        }
    }

    /**
     * The enclosing <code>Scope</code> of this <code>Scope</code>, or <code>null</code> if
     * top-level.
     */
    public final Scope<T> enclosingScope;

    private Map<String, Symbol> table = new HashMap<>();

    /**
     * Create a Scope in which to define {@link Symbol}s for objects of the parametric type
     * <code>T</code>.
     *
     * @param parentScope the enclosing <code>Scope</code> or <code>null</code> if top-level
     */
    public Scope(Scope<T> parentScope) {
        this.enclosingScope = parentScope;
    }

    /**
     * Give the <code>Symbol</code> named a definition. The symbol may be new or exist undefined in
     * this <code>Scope</code>. If it exists and is already defined in this scope, return
     * <code>null</code>.
     *
     * @param name of the <code>Symbol</code> to define in this scope
     * @param definition the new definition
     * @return the <code>Symbol</code> just defined
     * @throws IllegalStateException if already defined
     */
    public Symbol defineOrNull(String name, T definition) throws IllegalStateException {
        Symbol s = table.get(name);
        if (s != null) {
            // Already in the table: give it a value (which is an error if already defined).
            return s.defineOrNull(definition);
        } else {
            // Not in the table: we may safely define it.
            s = new Symbol(name, definition);
            table.put(name, s);
            return s;
        }
    }

    /**
     * Find the <code>Symbol</code> corresponding to the name in this or an enclosing
     * <code>Scope</code>. The <code>Symbol</code> may be undefined at this time (in the sense of
     * being known, but not having a value).
     *
     * @param name to look up
     * @return Symbol found, or null in not found
     */
    public Symbol resolveOrNull(String name) {
        Symbol s = table.get(name);
        if (s != null) {
            return s;
        } else if (enclosingScope != null) {
            return enclosingScope.resolveOrNull(name);
        } else {
            return null;
        }
    }

    /**
     * Find the definition of the <code>Symbol</code> corresponding to the name in this or an
     * enclosing <code>Scope</code>. The <code>Symbol</code> may be undefined at this time (in the
     * sense of being known, but not having a value).
     *
     * @param name to look up
     * @return Symbol found, or null in not found
     */
    public T definition(String name) {
        Symbol s = resolveOrNull(name);
        return s != null ? s.def : null;
    }
}
