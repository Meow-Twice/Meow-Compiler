package util;

/**
 * 自定义的链表节点
 */
public class ILinkNode {
    private ILinkNode prev;
    private ILinkNode next;

    public boolean hasPrev() {
        return prev != null;
    }

    public boolean hasNext() {
        return next != null;
    }

    // remove the node itself from the linked list
    public void remove() {
        // prev.next = next
        if (hasPrev()) {
            getPrev().setNext(getNext());
        }
        // next.prev = prev
        if (hasNext()) {
            getNext().setPrev(getPrev());
        }
    }

    // insert another node after this
    public void insertAfter(ILinkNode node) {
        node.setPrev(this);
        node.setNext(getNext());
        if (hasNext()) {
            getNext().setPrev(node);
        }
        setNext(node);
    }

    // insert another node before this
    // -> node -> this->
    public void insertBefore(ILinkNode node) {
        node.setNext(this);
        node.setPrev(getPrev());
        if (hasPrev()) {
            getPrev().setNext(node);
        }
        setPrev(node);
    }

    public ILinkNode() {
    }

    public ILinkNode getPrev() {
        return this.prev;
    }

    public void setPrev(final ILinkNode prev) {
        this.prev = prev;
    }

    public ILinkNode getNext() {
        return this.next;
    }

    public void setNext(final ILinkNode next) {
        this.next = next;
    }

    public static class EmptyNode extends ILinkNode {

    }

    
}