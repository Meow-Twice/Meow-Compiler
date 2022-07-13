package util;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

public class Ilist<E extends ILinkNode> implements Iterable<E> {
    // 此种写法head和tail仍然是ILinkNode类
    // public E head;
    // public E tail;
    // public int size = 0;
    //
    // public Ilist() {
    //     head = (E) new ILinkNode();
    //     tail = (E) new ILinkNode();
    //     head.setNext(tail);
    //     tail.setPrev(head);
    //     size = 0;
    // }
    public ILinkNode head;
    public ILinkNode tail;
    public int size = 0;

    public Ilist() {
        head = new ILinkNode();
        tail = new ILinkNode();
        head.setNext(tail);
        tail.setPrev(head);
        size = 0;
    }

    public void clear() {
        size = 0;
        head.setNext(tail);
        tail.setPrev(head);
    }

    public E getBegin() {
        return (E) head.getNext();
    }

    public E getEnd() {
        return (E) tail.getPrev();
    }

    public void insertBefore(E node, E insertBefore) {
        node.setPrev(insertBefore.getPrev());
        node.setNext(insertBefore);
        insertBefore.getPrev().setNext(node);
        insertBefore.setPrev(node);
        size++;
    }

    public void insertAfter(E node, E insertAfter) {
        node.setNext(insertAfter.getNext());
        node.setPrev(insertAfter);
        insertAfter.getNext().setPrev(node);
        insertAfter.setNext(node);
        size++;
    }

    public void insertAtEnd(E node) {
        node.setPrev(tail.getPrev());
        node.setNext(tail);
        tail.getPrev().setNext(node);
        tail.setPrev(node);
        size++;
    }

    public void insertAtBegin(E node) {
        node.setNext(head.getNext());
        node.setPrev(head);
        head.getNext().setPrev(node);
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
        return new IIterator();
    }

    class IIterator implements Iterator<E> {

        ILinkNode cur = head;

        IIterator() {
        }

        @Override
        public boolean hasNext() {
            return cur.getNext() != tail;
        }

        @Override
        public E next() {
            cur = cur.getNext();
            return (E) cur;
        }

        @Override
        public void remove() {
            cur.getPrev().setNext(cur.getNext());
            cur.getNext().setPrev(cur.getPrev());
            size--;
        }

    }

}
