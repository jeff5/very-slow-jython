// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
/** The Very Slow Jython rt4 core classes and API. */
module uk.co.farowl.rt4core {
    // exports uk.co.farowl.vsj4.core;
    exports uk.co.farowl.vsj4.runtime;
    exports uk.co.farowl.vsj4.support;
    exports uk.co.farowl.vsj4.stringlib;

    requires transitive org.slf4j;
    requires org.objectweb.asm;
    requires org.objectweb.asm.tree;
}
