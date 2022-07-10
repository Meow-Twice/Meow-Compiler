package util;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

public class Ilist<E extends ILinkNode> implements Iterable<E> {
    public E head;
    public E tail;
    public int size = 0;

    public Ilist() {
        head = (E) new ILinkNode();
        tail = (E) new ILinkNode();
        size = 0;
    }

    public void clear() {
        size = 0;
    }

    public E getBegin() {
        return (E) head.getNext();
    }

    public E getEnd() {
        return (E) head.getNext();
    }

    public void insertBefore(E node, E insertBefore) {
        node.setPrev(insertBefore.getPrev());
        node.setNext(insertBefore);
        insertBefore.setPrev(node);
        size++;
    }

    public void insertAfter(E node, E insertAfter) {
        node.setNext(insertAfter.getNext());
        node.setPrev(insertAfter);
        insertAfter.setNext(node);
        size++;
    }

    public void insertAtEnd(E node) {
        node.setPrev(tail.getPrev());
        node.setNext(tail);
        tail.setPrev(node);
        size++;
    }

    public void insertAtBegin(E node) {
        node.setNext(head.getPrev());
        node.setPrev(head);
        head.setNext(node);
        size++;
    }

    public void remove(E node) {
        node.getPrev().setNext(node.getNext());
        node.getNext().setPrev(node.getPrev());
        size--;
    }

    @Override
    public Iterator<E> iterator() {
        return new IIterator(getBegin());
    }

    class IIterator implements Iterator<E> {

        int curIdx;

        E cur = (E) new ILinkNode();
        E nxt = tail;

        IIterator(E head) {
            cur.setNext(head);
        }

        @Override
        public boolean hasNext() {
            return cur.getNext() != tail;
        }

        @Override
        public E next() {
            cur = (E) cur.getNext();
            return cur;
        }

        @Override
        public void remove() {
            cur.getPrev().setNext(cur.getNext());
            cur.getNext().setPrev(cur.getPrev());
            size--;
        }

    }

}
