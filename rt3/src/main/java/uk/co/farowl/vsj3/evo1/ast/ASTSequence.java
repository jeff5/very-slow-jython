package uk.co.farowl.vsj3.evo1.ast;

import java.util.ArrayList;
import java.util.Arrays;

public class ASTSequence extends AST {

    private final ArrayList<AST> items;

    public ASTSequence() {
        this.items = new ArrayList<>();
    }

    public ASTSequence(AST item) {
        this.items = new ArrayList<>();
        if (item != null)
            this.items.add(item);
    }

    public ASTSequence(AST[] seq) {
        this.items = new ArrayList<>(seq.length);
        this.items.addAll(Arrays.asList(seq));
    }

    public ASTSequence(ASTSequence seq) {
        this.items = new ArrayList<>(seq.getLength());
        this.items.addAll(seq.items);
    }

    public void append(AST item) {
        if (item != null)
            items.add(item);
    }

    public void clear() {
        this.items.clear();
    }

    public ASTSequence flatten() {
        ASTSequence seq = new ASTSequence();
        for (AST item : items) {
            if (item instanceof ASTSequence)
                seq.items.addAll(((ASTSequence) item).flatten().items);
            else
                seq.items.add(item);
        }
        return seq;
    }

    public AST get(int index) {
        return items.get(index);
    }

    public ASTSequence getFirst() {
        ASTSequence seq = new ASTSequence();
        for (AST item : items)
            if (item instanceof ASTPair<?,?>)
                seq.append(((ASTPair<?, ?>) item).getFirst());
        return seq;
    }

    public AST[] getItems() {
        AST[] seq = new AST[items.size()];
        items.toArray(seq);
        return seq;
    }

    public int getLength() {
        return items.size();
    }

    public ASTSequence getSecond() {
        ASTSequence seq = new ASTSequence();
        for (AST item : items)
            if (item instanceof ASTPair<?,?>)
                seq.append(((ASTPair<?, ?>) item).getSecond());
        return seq;
    }

    public void insert(AST value) {
        if (value != null)
            items.add(0, value);
    }

    public void insert(int index, AST value) {
        if (value != null)
            items.add(index, value);
    }

    public AST remove(int index) {
        return items.remove(index);
    }

    public void setItem(int index, AST value) {
        items.set(index, value);
    }

    public int size() {
        return items.size();
    }
}
