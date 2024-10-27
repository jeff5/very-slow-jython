// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.support.internal;

/** Exposers are of type or module kind. */
public enum ScopeKind {

    MODULE("$module"), //
    TYPE("$self");

    ScopeKind(String selfName) { this.selfName = selfName; }

    /** Name of a "self" parameter in instance methods. */
    public String selfName;
}
