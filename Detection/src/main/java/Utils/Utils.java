package Utils;

/**
 * Created by Alex on 21.12.2017.
 */
public class Utils
{
    public static < T > boolean memeq( T array[], int offset, int length, T value )
    {
        int i;
        for(i=offset;i<offset+length;i++)
        {
            if( !array[ i ].equals( value ) )
            {
                return false;
            }
        }
        return true;
    }

    public static int next_power_of_two( double val )
    {
        int p = 1;
        while( p <= val )
        {
            p *= 2;
        }
        return p;
    }

    public static boolean is_power_of_two( double val )
    {
        int p = 1;
        while( p < val )
        {
            p *= 2;
        }
        return p == val;
    }

    public static void log2lin( double[] x, int n, double base )
    {
        int i;
        for( i = 0; i <n;i++)
        {
            x[ i ] = Math.pow( base, x[ i ] );
        }
    }

    public static void lin2log( double[] x, int n, double base )
    {
        int i;
        final double factor = Math.log( base );
        for( i = 0; i <n;i++)
        {
            x[ i ] = Math.log( x[ i ] ) / factor;
        }
    }

}
