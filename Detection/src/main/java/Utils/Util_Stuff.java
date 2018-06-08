package Utils;

/**
 * Created by Alex on 21.12.2017.
 */
public class Util_Stuff
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

    public static void plot_in_matlab( double[] xs, double[] ys, int n )
    {
        int i;
        System.out.print( "xs = [ " );
        for( i = 0; i < n - 1; i++ )
        {
            System.out.print( xs[ i ] + ", " );
        }
        System.out.println( xs[ n - 1 ] + " ];" );

        System.out.print( "ys = [ " );
        for( i = 0; i < n - 1; i++ )
        {
            System.out.print( ys[ i ] + ", " );
        }
        System.out.println( ys[ n - 1 ] + " ];" );
        System.out.println( "plot( xs, ys, '-o' );" );
    }

    public static void plot_in_matlab( double[] ys, int n )
    {
        int i;
        System.out.println( "xs = linspace( 0, " + ( n - 1 ) + ", " + n + " );" );

        System.out.print( "ys = [ " );
        for( i = 0; i < n - 1; i++ )
        {
            System.out.print( ys[ i ] + ", " );
        }
        System.out.println( ys[ n - 1 ] + " ];" );
        System.out.println( "plot( xs, ys, '-o' );" );
    }

    public static void plot_in_matlab( double[] orig_f_xs, double[] orig_f_ys, int orig_f_len,double[] new_xs, double[] new_ys, int new_len )
    {
        int i;
        System.out.print( "orig_f_xs = [ " );
        for( i = 0; i < orig_f_len - 1; i++ )
        {
            System.out.print( orig_f_xs[ i ] + ", " );
        }
        System.out.println( orig_f_xs[ orig_f_len - 1 ] + " ];" );

        System.out.print( "orig_f_ys = [ " );
        for( i = 0; i < orig_f_len - 1; i++ )
        {
            System.out.print( orig_f_ys[ i ] + ", " );
        }
        System.out.println( orig_f_ys[ orig_f_len - 1 ] + " ];" );

        System.out.print( "new_xs = [ " );
        for( i = 0; i < new_len - 1; i++ )
        {
            System.out.print( new_xs[ i ] + ", " );
        }
        System.out.println( new_xs[ new_len - 1 ] + " ];" );

        System.out.print( "new_ys = [ " );
        for( i = 0; i < new_len - 1; i++ )
        {
            System.out.print( new_ys[ i ] + ", " );
        }
        System.out.println( new_ys[ new_len - 1 ] + " ];" );
        if( new_len > orig_f_len )
        {
            System.out.println( "plot( orig_f_xs, orig_f_ys, '-o', new_xs, new_ys );" );
        }
        else
        {
            System.out.println( "plot( orig_f_xs, orig_f_ys, new_xs, new_ys, '-o' );" );
        }
    }
}
