grammar ASDL ;

/*
 * This statement of the grammar of ASDL [1] is taken from CPython
 * (Parser/asdl.py), which is in turn based on work by Eli Benderski.
 *
 * Parser for ASDL [1] definition files. Reads in an ASDL description and parses
 * it into an AST that describes it.
 *
 * The EBNF we're parsing here: Figure 1 of the paper [1]. Extended to support
 * modules and attributes after a product. Words starting with Capital letters
 * are terminals. Literal tokens are in "double quotes". Others are
 * non-terminals.
 *
 * module        ::= "module" Id "{" [definitions] "}"
 * definitions   ::= { TypeId "=" type }
 * type          ::= product | sum
 * product       ::= fields ["attributes" fields]
 * fields        ::= "(" { field, "," } field ")"
 * field         ::= TypeId ["?" | "*"] [id]
 * sum           ::= constructor { "|" constructor } ["attributes" fields]
 * constructor   ::= ConstructorId [fields]
 *
 * [1] "The Zephyr Abstract Syntax Description Language" by Wang, et. al. See
 *     http://asdl.sourceforge.net/
 *-------------------------------------------------------------------------------
 */

@header {
package uk.co.farowl.asdl;
}

module        : Module id '{' definition* '}' ;
definition    : TypeId '=' type ;
type          : product | sum ;
product       : fields attributes? ;
attributes    : Attributes fields ;
fields        : '(' ( field ',' )* field ')' ;
field         : TypeId cardinality=('?' | '*')? id? ;
sum           : constructor ( '|' constructor )* attributes? ;
constructor   : ConstructorId fields? ;

/*
 * In some contexts ASDL does not care whether an identifier starts with an upper-
 * or lower case letter. And the Python implementation of the parser doesn't even
 * care if it's a keyword. :-o
 */
id            : TypeId | ConstructorId | Module | Attributes ;


/*
In CPython 3.8, Parser/asdl.py makes these ASDL data types pre-defined:

builtin_types = {'identifier', 'string', 'bytes', 'int', 'object', 'singleton',
                 'constant'}

The ASDL parser does not treat these as special. They are just identifiers
that must be pre-defined for the ASDL compiler to interpret.

The 'bytes', 'object' and 'singleton' types are not used in version 3.8.
*/

// Types for describing tokens in an ASDL specification.

// Give all the tokens names for readability of generated code.
Module:     'module' ;
Attributes: 'attributes' ;
Equals:     '=' ;
Comma:      ',' ;
Question:   '?' ;
Pipe:       '|' ;
LParen:     '(' ;
RParen:     ')' ;
Asterisk:   '*' ;
LBrace:     '{' ;
RBrace:     '}' ;

// Comment starts with -- and runs to end of line
Comment:    '--' .*? '\r'? '\n'     -> skip ;
Space:      [ \t\r\n]   -> skip ;

/** A ConstructorId begins with an upper case letter. */
ConstructorId: [A-Z][A-Za-z_0-9]* ;

/** A TypeId begins with a lower case letter. */
TypeId: [a-z][A-Za-z_0-9]* ;

