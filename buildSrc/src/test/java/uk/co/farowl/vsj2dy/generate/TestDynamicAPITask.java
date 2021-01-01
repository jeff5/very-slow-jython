package uk.co.farowl.vsj2dy.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj2dy.generate.DynamicAPITask.CompilationUnit;
import uk.co.farowl.vsj2dy.generate.DynamicAPITask.Line;
import uk.co.farowl.vsj2dy.generate.DynamicAPITask.ParseError;

/**
 * Test various moving parts within the DynamicAPITask, outside the
 * context of Gradle execution.
 */
class TestDynamicAPITask {

    static final String example = String.join("\n", //
            "package com.example.insoucient.impala", // line 1
            "# Example for test", "", "", // lines 2-4
            "class Operations", // line 5
            "# another comment", "", // line 6-7
            "unary neg", // line 8
            "");

    @Test
    void parseBlankOrComment() {

        // Empty
        Line blank = Line.recognise(42, "");
        assertEquals(Line.Kind.BLANK, blank.kind);
        assertEquals(0, blank.arg.length);
        assertEquals(42, blank.lineno);
        assertEquals("", blank.line);

        // Comment (various space prefixes
        String c = "#hello !%# world";
        Line comment = Line.recognise(1, c);
        assertEquals(Line.Kind.BLANK, comment.kind);
        assertEquals(1, comment.lineno);
        assertEquals(c, comment.line);
        comment = Line.recognise(2, " " + c);
        assertEquals(Line.Kind.BLANK, comment.kind);
        comment = Line.recognise(3, "\t" + c);
        assertEquals(Line.Kind.BLANK, comment.kind);
        comment = Line.recognise(4, "  \t  \t " + c);
        assertEquals(Line.Kind.BLANK, comment.kind);
    }

    @Test
    void parsePackage() {

        // Simple package name
        Line pkg = Line.recognise(10, "package x");
        assertEquals(10, pkg.lineno);
        assertEquals(Line.Kind.PACKAGE, pkg.kind);
        assertEquals(1, pkg.arg.length);
        assertEquals("x", pkg.arg[0]);

        // Dotted package name
        pkg = Line.recognise(0, "package         com.example.x21.y");
        assertEquals(Line.Kind.PACKAGE, pkg.kind);
        assertEquals("com.example.x21.y", pkg.arg[0]);

        // Trailing comment
        pkg = Line.recognise(0, "package com.example.x.y## package");
        assertEquals(Line.Kind.PACKAGE, pkg.kind);
        assertEquals("com.example.x.y", pkg.arg[0]);
    }

    @Test
    void parseClass() {
        // Unary operation
        Line line = Line.recognise(11, "class MyNum42");
        assertEquals(11, line.lineno);
        assertEquals(Line.Kind.CLASS, line.kind);
        assertEquals(1, line.arg.length);
        assertEquals("MyNum42", line.arg[0]);
    }

    @Test
    void parseUnary() {
        // Unary operation
        Line line = Line.recognise(20, "unary neg");
        assertEquals(20, line.lineno);
        assertEquals(Line.Kind.UNARY, line.kind);
        assertEquals(1, line.arg.length);
        assertEquals("neg", line.arg[0]);
    }

    @Test
    void parseBinary() {
        // Binary operation
        Line line = Line.recognise(30, "binary add");
        assertEquals(30, line.lineno);
        assertEquals(Line.Kind.BINARY, line.kind);
        assertEquals(1, line.arg.length);
        assertEquals("add", line.arg[0]);
    }

    @Test
    void parseEnd() {
        // Binary operation
        Line line = Line.recognise(99, null);
        assertEquals(99, line.lineno);
        assertEquals(Line.Kind.EOF, line.kind);
        assertEquals(0, line.arg.length);
        assertEquals("", line.line);
    }

    @Test
    void parseExample() throws IOException, ParseError {
        Reader r = new StringReader(example);
        CompilationUnit unit = new CompilationUnit(r);
        unit.generateClass();
        assertEquals("com/example/insoucient/impala",
                unit.getPackagePath());
        assertEquals("Operations", unit.className);
    }
}
