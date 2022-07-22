package backend;

import java.util.HashSet;

public class Tools<T> {
    public void changeWorkSet(T t, HashSet<T> from, HashSet<T> to){
        from.remove(t);
        to.add(t);
    }
}
