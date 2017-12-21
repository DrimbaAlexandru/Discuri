package Utils;

/**
 * Created by Alex on 15.12.2017.
 */
public class Interval
{
    public int l;
    public int r;

    public Interval( int start, int len )
    {
        l = start;
        r = start + len;
    }

    public Interval getIntersection( Interval other )
    {
        int nl, nr;
        nl = Math.max( l, other.l );
        nr = Math.min( r, other.r );
        if( nl >= nr )
        {
            return null;
        }
        else
        {
            return new Interval( nl, nr );
        }
    }

    public boolean contains( int index )
    {
        return ( ( l <= index ) && ( index < r ) );
    }

    @Override
    public boolean equals( Object obj )
    {
        return ( obj instanceof Interval ) && ( ( ( Interval )obj ).l == l ) && ( ( ( Interval )obj ).r == r );
    }

    public int get_length()
    {
        return r - l;
    }

}
