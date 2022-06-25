package ir;

import util.ILinkNode;

import java.util.Objects;

public class Use extends ILinkNode {
    private Instr user;
    private Value used;
    private int idx;

    public Use(){
        super();
    }

    public Use(Instr user, Value used, int idx) {
        super();
        this.user = user;
        this.used = used;
        this.idx = idx;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Use use = (Use) o;
        return idx == use.idx && used.equals(use.used) && user.equals(use.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(used, user, idx);
    }

    public Value getUsed() {
        return used;
    }

    public Instr getUser() {
        return user;
    }

    public int getIdx() {
        return idx;
    }
}
