package Utils.DataStructures;

/**
 * Created by Alex on 31.05.2018.
 */
public class Node<T>
{
    private T value = null;
    private Node< T > next = null;
    private Node< T > prev = null;

    public Node( T val )
    {
        value = val;
    }

    public Node( T val, Node< T > prev, Node< T > next )
    {
        value = val;
        this.prev = prev;
        this.next = next;
    }

    public void setValue( T value )
    {
        this.value = value;
    }

    public void setNext( Node< T > next )
    {
        this.next = next;
    }

    public void setPrev( Node< T > prev )
    {
        this.prev = prev;
    }

    public Node< T > getNext()
    {
        return next;
    }

    public Node< T > getPrev()
    {
        return prev;
    }

    public T getValue()
    {
        return value;
    }
}
