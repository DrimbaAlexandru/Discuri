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

    public static void plot_in_matlab( double[] in, int off1, int n, double[] out, int off2, int m )
    {
        int i;
        System.out.print( "t1 = [ " );
        for( i = 0; i < n - 1; i++ )
        {
            System.out.print( ( double )i / ( n - 1 ) + ", " );
        }
        System.out.println( "1 ];" );

        System.out.print( "t2 = [ " );
        for( i = 0; i < m - 1; i++ )
        {
            System.out.print( ( double )i / ( m - 1 ) + ", " );
        }
        System.out.println( "1 ];" );

        System.out.print( "in = [ " );
        for( i = 0; i < n - 1; i++ )
        {
            System.out.print( in[ i + off1 ] + ", " );
        }
        System.out.println( in[ off1 + n - 1 ] + " ];" );

        System.out.print( "out = [ " );
        for( i = 0; i < m - 1; i++ )
        {
            System.out.print( out[ i + off2 ] + ", " );
        }
        System.out.println( out[ m - 1 + off2 ] + " ];" );

        System.out.println( "plot( t1, in, 'LineWidth', 1, ...\n t2, out, 'LineWidth', 1 );" );
        System.out.println( "legend( 'original signal', 'resized signal' );" );
    }
}
