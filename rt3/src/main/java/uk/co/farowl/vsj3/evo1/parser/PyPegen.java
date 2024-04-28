package uk.co.farowl.vsj3.evo1.parser;

import uk.co.farowl.vsj3.evo1.ast.*;
import uk.co.farowl.vsj3.evo1.ast.Module;

import java.util.Arrays;

/**
 *
 */
public class PyPegen {

    public final static Object Ellipsis = "...";

    public final static Object False = Boolean.FALSE;

    public final static Object None = "None";

    public final static Object True = Boolean.TRUE;

    public static ASTExpr dummyName() {
        // TODO: insert a proper dummy name
        return null;
    }

    @SafeVarargs
    public static <T extends AST> T[] singletonSeq(T... nodes) {
        if (nodes.length == 1 && nodes[0] != null)
            return nodes;
        else
            return null;
    }

    public static <T extends AST> T[] seqInsertInFront(T node, T[] seq) {
        if (seq == null)
            return singletonSeq(node);
        T[] result = Arrays.copyOf(seq, seq.length + 1);
        System.arraycopy( seq, 0, result, 1, seq.length );
        result[0] = node;
        return result;
    }

    public static AST[] seqAppendToEnd(AST[] seq, AST node) {
        if (seq == null)
            return singletonSeq(node);
        AST[] result = new AST[seq.length + 1];
        System.arraycopy( seq, 0, result, 0, seq.length );
        result[seq.length] = node;
        return result;
    }

    private static int getFlattenedSeqSize(AST[][] seqs) {
        int result = 0;
        for (AST[] seq : seqs)
            result += seq.length;
        return result;
    }

    public static <T extends AST> T[] seqFlatten(T[][] seqs) {
        if (seqs.length == 0)
            return null;
        int flattened_seq_size = getFlattenedSeqSize(seqs);
        T[] flattened_seq = Arrays.copyOf(seqs[0], flattened_seq_size);
        int dest_index = 0;
        for (T[] seq : seqs) {
            System.arraycopy(seq, 0, flattened_seq, dest_index, seq.length);
            dest_index += seq.length;
        }
        return flattened_seq;
    }

    public static <T extends AST> T seqLastItem(T[] seq) {
        return seq[seq.length - 1];
    }

    public static <T extends AST> T seqFirstItem(T[] seq) {
        return seq[0];
    }

    public static ASTExpr joinNamesWithDot(ASTExpr first_name, ASTExpr second_name) {
        if (first_name instanceof Name && second_name instanceof Name) {
            String id = ((Name) first_name).getId() + "." + ((Name) second_name).getId();
            return new Name(id, _ExprContext.Load, first_name.getLineno(), first_name.getColOffset(),
                    second_name.getEndLineno(), second_name.getEndColOffset());
        } else
            throw new IllegalArgumentException("both arguments must be of type 'Name'");
    }

    public static int seqCountDots(Token[] seq) {
        int number_of_dots = 0;
        for (Token token : seq) {
            switch (token.getTokenType()) {
                case ELLIPSIS ->
                    number_of_dots += 3;
                case DOT ->
                    number_of_dots += 1;
                default ->
                    throw new IllegalArgumentException("unexpected type of token");
            }
        }
        return number_of_dots;
    }

    public static Alias aliasForStar(int lineno, int col_offset, int end_lineno, int end_col_offset) {
        return new Alias("*", null, lineno, col_offset, end_lineno, end_col_offset);
    }

    public static String[] mapNamesToIds(ASTExpr[] seq) {
        String[] new_seq = new String[seq.length];
        for (int i = 0; i < seq.length; i++) {
            if (seq[i] instanceof Name)
                new_seq[i] = ((Name) seq[i]).getId();
            else
                new_seq[i] = null;
        }
        return new_seq;
    }

    public static CmpopExprPair cmpopExprPair(_Cmpop cmpop, ASTExpr expr) {
        return new CmpopExprPair(cmpop, expr);
    }

    public static _Cmpop[] getCmpops(AST[] seq) {
        _Cmpop[] new_seq = new _Cmpop[seq.length];
        for (int i = 0; i < seq.length; i++) {
            if (seq[i] instanceof CmpopExprPair)
                new_seq[i] = ((CmpopExprPair) seq[i]).getCmpop();
        }
        return new_seq;
    }

    public static ASTExpr[] getExprs(AST[] seq) {
        ASTExpr[] new_seq = new ASTExpr[seq.length];
        for (int i = 0; i < seq.length; i++) {
            if (seq[i] instanceof CmpopExprPair)
                new_seq[i] = ((CmpopExprPair) seq[i]).getExpr();
        }
        return new_seq;
    }

    private static ASTExpr[] setSeqContext(ASTExpr[] seq, _ExprContext ctx) {
        ASTExpr[] new_seq = new ASTExpr[seq.length];
        for (int i = 0; i < seq.length; i++)
            new_seq[i] = setExprContext(seq[i], ctx);
        return new_seq;
    }

    public static ASTExpr setExprContext(ASTExpr expr, _ExprContext ctx) {
        if (expr instanceof HasExprContext<?>)
            return ((HasExprContext<?>) expr).withCtx(ctx);
        else
            return expr;
    }

    public static KeyValuePair keyValuePair(ASTExpr key, ASTExpr value) {
        return new KeyValuePair(key, value);
    }

    public static ASTExpr[] getKeys(AST[] seq) {
        ASTExpr[] new_seq = new ASTExpr[seq.length];
        for (int i = 0; i < seq.length; i++)
            new_seq[i] = ((KeyValuePair) seq[i]).getKey();
        return new_seq;
    }

    public static ASTExpr[] getValues(AST[] seq) {
        ASTExpr[] new_seq = new ASTExpr[seq.length];
        for (int i = 0; i < seq.length; i++)
            new_seq[i] = ((KeyValuePair) seq[i]).getValue();
        return new_seq;
    }

    public static KeyPatternPair keyPatternPair(ASTExpr key, ASTPattern pattern) {
        return new KeyPatternPair(key, pattern);
    }

    public static ASTExpr[] getPatternKeys(AST[] seq) {
        ASTExpr[] new_seq = new ASTExpr[seq.length];
        for (int i = 0; i < seq.length; i++)
            new_seq[i] = ((KeyPatternPair) seq[i]).getKey();
        return new_seq;
    }

    public static ASTPattern[] getPatterns(AST[] seq) {
        ASTPattern[] new_seq = new ASTPattern[seq.length];
        for (int i = 0; i < seq.length; i++)
            new_seq[i] = ((KeyPatternPair) seq[i]).getPattern();
        return new_seq;
    }

    public static NameDefaultPair nameDefaultPair(Arg arg, ASTExpr value, String tc) {
        return new NameDefaultPair(arg, value);
    }

    public static SlashWithDefault slashWithDefault(Arg[] plain_names, AST[] names_with_defaults) {
        return new SlashWithDefault(plain_names, names_with_defaults);
    }

    public static StarEtc starEtc(Arg vararg, AST[] kwonlyargs, Arg kwarg) {
        return new StarEtc(vararg, kwonlyargs, kwarg);
    }

    public static <T extends AST> T[] joinSequences(T[] a, T[] b) {
        T[] new_seq = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(a, 0, new_seq, 0, a.length);
        System.arraycopy(b, 0, new_seq, a.length, b.length);
        return new_seq;
    }

    private static Arg[] getNames(AST[] names_with_defaults) {
        Arg[] seq = new Arg[names_with_defaults.length];
        for (int i = 0; i < seq.length; i++)
            seq[i] = ((NameDefaultPair)names_with_defaults[i]).getArg();
        return seq;
    }

    private static ASTExpr[] getDefaults(AST[] names_with_defaults) {
        ASTExpr[] seq = new ASTExpr[names_with_defaults.length];
        for (int i = 0; i < seq.length; i++)
            seq[i] = ((NameDefaultPair)names_with_defaults[i]).getValue();
        return seq;
    }

    private static Arg[] makePosOnlyArgs(Arg[] slash_without_default, SlashWithDefault slash_with_default) {
        Arg[] posonlyargs;
        if (slash_without_default != null)
            posonlyargs = slash_without_default;
        else if (slash_with_default != null) {
            Arg[] slash_with_default_names = getNames(slash_with_default.getNamesWithDefaults());
            posonlyargs = joinSequences(slash_with_default.getPlainNames(), slash_with_default_names);
        }
        else
            posonlyargs = new Arg[0];
        return posonlyargs;
    }

    private static Arg[] makePosArgs(Arg[] plain_names, AST[] names_with_default) {
        Arg[] posargs;
        if (plain_names != null && names_with_default != null) {
            Arg[] names_with_default_names = getNames(names_with_default);
            posargs = joinSequences(plain_names, names_with_default_names);
        }
        else if (plain_names == null && names_with_default != null) {
            posargs = getNames(names_with_default);
        }
        else if (plain_names != null && names_with_default == null) {
            posargs = plain_names;
        }
        else {
            posargs = new Arg[0];
        }
        return posargs;
    }

    private static ASTExpr[] makePosDefaults(SlashWithDefault slash_with_default, AST[] names_with_default) {
        ASTExpr[] posdefaults;
        if (slash_with_default != null && names_with_default != null) {
            ASTExpr[] slash_with_default_values = getDefaults(slash_with_default.getNamesWithDefaults());
            ASTExpr[] names_with_default_values = getDefaults(names_with_default);
            posdefaults = joinSequences(slash_with_default_values, names_with_default_values);
        }
        else if (slash_with_default == null && names_with_default != null) {
            posdefaults = getDefaults(names_with_default);
        }
        else if (slash_with_default != null && names_with_default == null) {
            posdefaults = getDefaults(slash_with_default.getNamesWithDefaults());
        }
        else {
            posdefaults = new ASTExpr[0];
        }
        return posdefaults;
    }

    public static Arguments makeArguments(Arg[] slash_without_default,
                                          SlashWithDefault slash_with_default,
                                          Arg[] plain_names,
                                          AST[] names_with_default,
                                          StarEtc star_etc) {
        Arg[] posonlyargs = makePosOnlyArgs(slash_without_default, slash_with_default);
        Arg[] posargs = makePosArgs(plain_names, names_with_default);
        ASTExpr[] posdefaults = makePosDefaults(slash_with_default, names_with_default);
        Arg vararg = null;
        if (star_etc != null && star_etc.getVararg() != null)
            vararg = star_etc.getVararg();
        Arg[] kwonlyargs;
        if (star_etc != null && star_etc.getKwonlyargs() != null)
            kwonlyargs = getNames(star_etc.getKwonlyargs());
        else
            kwonlyargs = new Arg[0];
        ASTExpr[] kwdefaults;
        if (star_etc != null && star_etc.getKwonlyargs() != null)
            kwdefaults = getDefaults(star_etc.getKwonlyargs());
        else
            kwdefaults = new ASTExpr[0];
        Arg kwarg = null;
        if (star_etc != null && star_etc.getKwarg() != null)
            kwarg = star_etc.getKwarg();
        return new Arguments(posonlyargs, posargs, vararg, kwonlyargs, kwdefaults, kwarg, posdefaults);
    }

    public static Arguments emptyArguments() {
        return new Arguments(new Arg[0], new Arg[0], null, new Arg[0], new ASTExpr[0], null, new ASTExpr[0]);
    }

    public static ASTStmt functionDefDecorators(ASTExpr[] decorators, ASTStmt function_def) {
        if (function_def instanceof AsyncFunctionDef) {
            AsyncFunctionDef func = (AsyncFunctionDef) function_def;
            return new AsyncFunctionDef(
                    func.getName(), func.getArgs(), func.getBody(), decorators, func.getReturns(), func.getTypeComment()
            );
        }
        else if (function_def instanceof FunctionDef) {
            FunctionDef func = (FunctionDef) function_def;
            return new FunctionDef(
                    func.getName(), func.getArgs(), func.getBody(), decorators, func.getReturns(), func.getTypeComment()
            );
        }
        else
            throw new IllegalArgumentException("Last argument must be a function definition.");
    }

    public static ClassDef classDefDecorators(ASTExpr[] decorators, ASTStmt class_def) {
        if (class_def instanceof ClassDef) {
            ClassDef cls = (ClassDef) class_def;
            return new ClassDef(
                    cls.getName(), cls.getBases(), cls.getKeywords(), cls.getBody(), decorators
            );
        }
        else
            throw new IllegalArgumentException("Last argument must be a class definition.");
    }

    public static KeywordOrStarred keywordOrStarred(Keyword keyword, boolean is_keyword) {
        return new KeywordOrStarred._Keyword(keyword);
    }

    public static KeywordOrStarred keywordOrStarred(Starred starred, boolean is_keyword) {
        return new KeywordOrStarred._Starred(starred);
    }

    public static KeywordOrStarred keywordOrStarred(AST expr, int is_keyword) {
        if (expr instanceof Keyword)
            return keywordOrStarred((Keyword)expr, is_keyword != 0);
        if (expr instanceof Starred)
            return keywordOrStarred((Starred)expr, is_keyword != 0);
        else
            throw new IllegalArgumentException("Argument is neither a keyword nor starred.");
    }

    private static int seqNumberOfStarredExprs(AST[] seq) {
        int n = 0;
        for (int i = 0; i < seq.length; i++) {
            if (seq[i] instanceof KeywordOrStarred && !((KeywordOrStarred) seq[i]).isKeyword())
                n++;
        }
        return n;
    }

    public static ASTExpr[] seqExtractStarredExprs(AST[] kwargs) {
        int new_len = seqNumberOfStarredExprs(kwargs);
        ASTExpr[] new_seq = new ASTExpr[new_len];
        int idx = 0;
        for (int i = 0; i < kwargs.length; i++)
            if (kwargs[i] instanceof KeywordOrStarred && !((KeywordOrStarred) kwargs[i]).isKeyword()) {
                new_seq[idx++] = ((KeywordOrStarred) kwargs[i]).getStarred();
            }
        return new_seq;
    }

    public static Keyword[] seqDeleteStarredExprs(AST[] kwargs) {
        int new_len = kwargs.length - seqNumberOfStarredExprs(kwargs);
        Keyword[] new_seq = new Keyword[new_len];
        int idx = 0;
        for (int i = 0; i < kwargs.length; i++)
            if (kwargs[i] instanceof KeywordOrStarred && ((KeywordOrStarred) kwargs[i]).isKeyword()) {
                new_seq[idx++] = ((KeywordOrStarred) kwargs[i]).getKeyword();
            }
        return new_seq;
    }

    public static ASTExpr concatenateStrings(Token[] tokens) {
        // TODO: action_helpers.c, LINE 857ff
        if (tokens == null || tokens.length == 0)
            return null;
        StringBuilder sb = new StringBuilder();
        for (Token tk : tokens)
            sb.append(tk.string);
        int lineno = tokens[0].getLineno();
        int colOffset = tokens[0].getColOffset();
        int end_lineno = tokens[tokens.length - 1].getLineno();
        int end_colOffset = tokens[tokens.length - 1].getColOffset();
        return new Constant(sb.toString(), null, lineno, colOffset, end_lineno, end_colOffset);
    }

    public static ASTExpr ensureImaginary(ASTExpr exp) {
        // TODO: throw an exception if exp _is not_ a constant with a complex value
        // RAISE_SYNTAX_ERROR_KNOWN_LOCATION(exp, "imaginary number required in complex literal");
        return exp;
    }

    public static ASTExpr ensureReal(ASTExpr exp) {
        // TODO: throw an exception if exp _is_ a constant with a complex value
        // RAISE_SYNTAX_ERROR_KNOWN_LOCATION(exp, "real number required in complex literal");
        return exp;
    }

    public static ASTMod makeModule(ASTStmt[] a) {
        TypeIgnore[] type_ignores = null;
        // TODO: Create type ignores (action_helpers, LINE 967ff)
        return new Module(a, type_ignores);
    }

    public static Object newTypeComment(String s) {
        return s;
    }

    public static Arg addTypeCommentToArg(Arg arg, String tc) {
        if (tc == null)
            return arg;
        return new Arg(arg.getArg(), arg.getAnnotation(), tc,
                arg.getLineno(), arg.getColOffset(), arg.getEndLineno(), arg.getEndColOffset());
    }

    public static boolean checkBarryAsFlufl(Token t) {
        // TODO: Should this be retained or thrown out as an inside-joke of CPython?
        return false;
    }

    public static boolean checkLegacyStmt(ASTExpr name) {
        if (name instanceof Name) {
            String[] candidates = { "print", "exec" };
            String n = ((Name) name).getId();
            for (String candidate : candidates)
                if (n.equals(candidate))
                    return true;
        }
        return false;
    }

    public static String getExprName(ASTExpr e) {
        if (e instanceof Constant) {
            Object value = ((Constant) e).getValue();
            if (value == None)
                return "None";
            if (value == False)
                return "False";
            if (value == True)
                return "True";
            if (value == Ellipsis)
                return "Ellipsis";
            return "literal";
        }
        if (e != null)
            return e.getClass().getSimpleName();
        else
            return "null";
    }

    public static ASTExpr getLastComprehensionItem(Comprehension comprehension) {
        if (comprehension.getIfs() == null || comprehension.getIfs().length == 0)
            return comprehension.getIter();
        return seqLastItem(comprehension.getIfs());
    }

    public static ASTExpr collectCallSeqs(ASTExpr[] a, AST[] b,
                                          int lineno, int col_offset, int end_lineno, int end_col_offset) {
        if (b == null)
            return new Call(dummyName(), a, null, lineno, col_offset, end_lineno, end_col_offset);
        int total_len = a.length;
        ASTExpr[] starreds = seqExtractStarredExprs(b);
        Keyword[] keywords = seqDeleteStarredExprs(b);
        total_len += starreds.length;
        ASTExpr[] args = new ASTExpr[total_len];
        System.arraycopy(a, 0, args, 0, a.length);
        System.arraycopy(starreds, 0, args, a.length, starreds.length);
        return new Call(dummyName(), args, keywords, lineno, col_offset, end_lineno, end_col_offset);
    }

    // AST Error reporting helpers

    public static ASTExpr getInvalidTarget(ASTExpr e, TargetsType targetsType) {
        // TODO: action_helpers.c, LINE 1176ff
        return null;
    }

    public static Object argumentsParsingError(ASTExpr e) {
        // TODO: action_helpers.c, LINE 1233ff
        return null;
    }

    public static Object nonparenGenexpInCall(ASTExpr args, Comprehension[] comprehensions) {
        // TODO: action_helpers.c, LINE 1252ff
        return null;
    }

    // Other methods not originally from action_helpers:

    public static ASTStmt[] interactiveExit() {
        return null;
    }

    public static Object raiseError(Object errtype, String errmsg, Object ... args) {
        return null;
    }

    public static Object raiseErrorKnownLocation(Object errtype, int lineno, int col_offset, int end_lineno, int end_col_offset, String errmsg, Object... va) {
        return null;
    }

    public static Object raiseSyntaxErrorInvalidTarget(_TARGETS_TYPE type, Object e) {
        return null;
    }

    // public static void setSyntaxError(Token last_token) {}


    public static ASTExpr auxMakeSeq(Object... items) {
        return null;
    }
}
