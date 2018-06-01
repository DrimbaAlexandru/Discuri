package Utils.DataStructures;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import Utils.Interval;

/**
 * Created by Alex on 31.05.2018.
 */
public class OrderedNonOverlappingIntervalSet
{
    private Node< Interval > first = new Node< Interval >( null );
    private Node< Interval > last = new Node< Interval >( null );
    private Node< Interval > current = first;

    private int size = 0;
    private int position = -1;

    public OrderedNonOverlappingIntervalSet()
    {
        first.setNext( last );
        last.setPrev( first );
    }

    public void moveCursorOnOrAfter( int pos )
    {
        moveCursorOnOrBefore( pos );
        if( current == first || !current.getValue().contains( pos ) )
        {
            moveCursorNext();
        }
    }

    /**
     * Moves the cursor to the interval with maximum L such as L<=pos
     * */
    private void moveCursorOnOrBefore( int pos )
    {
        if( last.getPrev().getValue() != null && last.getPrev().getValue().l <= pos )
        {
            current = last.getPrev();
            position = size - 1;
            return;
        }

        if( first.getNext().getValue() != null && first.getNext().getValue().l > pos )
        {
            current = first;
            position = -1;
            return;
        }

        if( current == first )
        {
            current = first.getNext();
            position = 0;
        }
        while( current.getValue() != null && current.getValue().l <= pos )
        {
            current = current.getNext();
            position++;
        }

        if( current == last )
        {
            current = last.getPrev();
            position = size - 1;
        }
        while( current.getValue() != null && current.getValue().l > pos )
        {
            current = current.getPrev();
            position--;
        }
    }

    public void add( Interval i )
    {
        remove( i );
        moveCursorOnOrBefore( i.l );

        Node< Interval > nod = new Node< Interval >( i, current, current.getNext() );
        current.setNext( nod );
        nod.getNext().setPrev( nod );
        size++;
        current = nod;
        position++;
        compact_current();
    }

    private void splitCurrent( int splitPoint )
    {
        Interval i = current.getValue();
        if( i == null || !( splitPoint > i.l && splitPoint < i.r - 1 ) )
        {
            return;
        }
        Interval i2 = new Interval( splitPoint, i.r, false );
        i.r = splitPoint;
        Node< Interval > node = new Node< Interval >( i2, current, current.getNext() );
        current.getNext().setPrev( node );
        current.setNext( node );
        size++;
    }

    private void remove_current( boolean move_forward )
    {
        if( current == first || current == last )
        {
            return;
        }
        Node< Interval > nextCurrent = move_forward ? current.getNext() : current.getPrev();
        position = move_forward ? position : position - 1;
        current.getPrev().setNext( current.getNext() );
        current.getNext().setPrev( current.getPrev() );
        current.setPrev( null );
        current.setNext( null );
        current = nextCurrent;
        size--;
    }

    private void compact_current()
    {
        if( current.getValue() == null )
        {
            return;
        }
        Node< Interval > n = current.getNext(), p = current.getPrev();

        if( n.getValue() != null && n.getValue().l == current.getValue().r )
        {
            current.getValue().r = n.getValue().r;
            moveCursorNext();
            remove_current( false );
        }

        if( p.getValue() != null && p.getValue().r == current.getValue().l )
        {
            p.getValue().r = current.getValue().r;
            remove_current( false );
        }
    }

    public void remove( Interval i )
    {
        moveCursorOnOrBefore( i.l );
        if( current.getValue() == null || !current.getValue().contains( i.l ) )
        {
            moveCursorNext();
        }

        if( current.getValue() == null || current.getValue().l >= i.r )
        {
            return;
        }

        if( current.getValue().l < i.l && current.getValue().r - 1 > i.l )
        {
            splitCurrent( i.l );
            moveCursorNext();
        }
        while( current.getValue() != null && i.includes( current.getValue() ) )
        {
            remove_current( true );
        }
        if( current.getValue() == null )
        {
            return;
        }

        if( current.getValue().l < i.r && current.getValue().r > i.r )
        {
            splitCurrent( i.r );
            remove_current( false );
        }
    }

    public void moveCursorOnFirst()
    {
        current = first.getNext();
        position = 0;
    }

    public void moveCursorOnLast()
    {
        current = last.getPrev();
        position = size - 1;
    }

    public void moveCursorNext()
    {
        current = current.getNext();
        position++;
    }

    public void moveCursorPrevious()
    {
        current = current.getPrev();
        position--;
    }

    public Interval getCurrent()
    {
        return current.getValue();
    }

    public int getSize()
    {
        return size;
    }

    public int getPosition()
    {
        return position;
    }

    public void moveToPosition( int newPosition ) throws DataSourceException
    {
        if( newPosition < -1 || newPosition > size )
        {
            throw new DataSourceException( "YOU TRY'NA KILL THIS APP??", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        while( position > newPosition )
        {
            moveCursorPrevious();
        }
        while( position < newPosition )
        {
            moveCursorNext();
        }
    }

    public OrderedNonOverlappingIntervalSet Clone()
    {
        OrderedNonOverlappingIntervalSet set = new OrderedNonOverlappingIntervalSet();
        set.current = current;
        set.first = first;
        set.last = last;
        set.size = size;
        set.position = position;

        return set;
    }
}
