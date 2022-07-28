package mir;

import util.ILinkNode;

import java.util.Objects;

public class Use extends ILinkNode {
    private Instr user;
    private Value used;
    private int idx;

    private static int use_num = 0;

    private int hash = ++use_num;

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
        return hash == use.hash;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash);
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
