package util;

import java.util.Iterator;

public class Ilist<E> implements Iterable<Ilist.Node<E>>{
    public Node<E> head;
    public Node<E> tail;
    public int nodeNum = 0;

    public Ilist() {
        nodeNum = 0;
        head = tail = null;
    }

    public void clear() {
        this.head = null;
        this.tail = null;
        nodeNum = 0;
    }

    public Node<E> getHead() {
        return head;
    }

    public void setHead(Node<E> head) {
        this.head = head;
    }

    public Node<E> getTail() {
        return tail;
    }

    public void setTail(Node<E> tail) {
        this.tail = tail;
    }

    public void insertBefore(Node<E> node, Node<E> insertBefore) {
        node.prev = insertBefore.prev;
        node.next = insertBefore;
        if (insertBefore.prev != null) {
            insertBefore.prev.next = node;
        }
        insertBefore.prev = node;

        if (head == insertBefore) {
            head = node;
        }
        nodeNum++;
    }

    public void insertAfter(Node<E> node, Node<E> insertAfter) {
        node.prev = insertAfter;
        node.next = insertAfter.next;
        if (insertAfter.next != null) {
            insertAfter.next.prev = node;
        }
        insertAfter.next = node;

        if (tail == insertAfter) {
            tail = node;
        }
        nodeNum++;
    }

    public void insertAtEnd(Node<E> node) {
        node.prev = tail;
        node.next = null;
        if (tail == null) {
            head = tail = node;
        } else {
            tail.next = node;
            tail = node;
        }
        nodeNum++;
    }

    public void insertAtBegin(Node<E> node) {
        node.prev = null;
        node.next = head;
        if (head == null) {
            head = tail = node;
        } else {
            head.prev = node;
            head = node;
        }
        nodeNum++;
    }

    public void remove(Node<E> node) {
        if(node == null){
            return;
        }
        if (node.prev != null) {
            node.prev.next = node.next;
        } else {
            head = node.next;
        }

        if (node.next != null) {
            node.next.prev = node.prev;
        } else {
            tail = node.prev;
        }
        nodeNum--;
    }

    @Override
    public Iterator<Node<E>> iterator() {
        return new IIterator(head);
    }

    class IIterator implements Iterator<Node<E>> {

        Node<E> cur = new Node<>(null);
        Node<E> nxt = null;

        IIterator(Node<E> head) {
            cur.next = head;
        }

        @Override
        public boolean hasNext() {
            return nxt != null || cur.next != null;
        }

        @Override
        public Node<E> next() {
            if (nxt == null) {
                cur = cur.next;
            } else {
                cur = nxt;
            }
            nxt = null;
            return cur;
        }

        @Override
        public void remove() {
            Node<E> prev = cur.prev;
            Node<E> next = cur.next;
            // Node<E> parent = cur.getParent();
            if (prev != null) {
                prev.next = next;
            // } else {
            //     parent.setEntry(next);
            }

            if (next != null) {
                next.prev = prev;
            // } else {
            //     parent.setLast(prev);
            }
            // --parent.numNode;

            nxt = next;
            cur.next = cur.prev = null;
            cur.val = null;
        }
    }
    
    public static class Node<E> {

        public E val;
        public Node<E> prev = null;
        public Node<E> next = null;
        public Object parent = null;

        public Node(E val){
            this.val = val;
        }

    }

}
