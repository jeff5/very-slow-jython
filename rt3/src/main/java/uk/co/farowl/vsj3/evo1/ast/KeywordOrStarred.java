package uk.co.farowl.vsj3.evo1.ast;

public abstract class KeywordOrStarred extends AST {

    public abstract boolean isKeyword();

    public abstract Keyword getKeyword();

    public abstract Starred getStarred();

    public static class _Keyword extends KeywordOrStarred {
        final Keyword keyword;

        public _Keyword(Keyword keyword) {
            this.keyword = keyword;
        }

        @Override
        public Keyword getKeyword() {
            return this.keyword;
        }

        @Override
        public Starred getStarred() {
            throw new ClassCastException("Tried to access starred of a keyword.");
        }

        @Override
        public boolean isKeyword() {
            return true;
        }
    }

    public static class _Starred extends KeywordOrStarred {
        final Starred starred;

        public _Starred(Starred starred) {
            this.starred = starred;
        }

        @Override
        public Keyword getKeyword() {
            throw new ClassCastException("Tried to access keyword of a starred.");
        }

        @Override
        public Starred getStarred() {
            return this.starred;
        }

        @Override
        public boolean isKeyword() {
            return false;
        }
    }
}
