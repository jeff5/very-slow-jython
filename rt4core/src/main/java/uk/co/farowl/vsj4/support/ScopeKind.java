// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.support;

/**
 * The scope within which a method was be found affects argument
 * processing, how it is processed for exposure, and how its signature
 * is presented in documentation.
 */
public enum ScopeKind {

    MODULE("$module"), //
    TYPE("$self");

    ScopeKind(String selfName) { this.selfName = selfName; }

    /** Name of a "self" parameter in instance methods. */
    public String selfName;
}