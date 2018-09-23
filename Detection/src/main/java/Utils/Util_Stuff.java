package Utils;

import SignalProcessing.Windowing.Windowing;

/**
 * Created by Alex on 21.12.2017.
 */
public class Util_Stuff
{
    public static int next_power_of_two( float val )
    {
        int p = 1;
        while( p <= val )
        {
            p *= 2;
        }
        return p;
    }

    public static boolean is_power_of_two( float val )
    {
        int p = 1;
        while( p < val )
        {
            p *= 2;
        }
        return p == val;
    }

    public static void log2lin( float[] x, int n, float base )
    {
        int i;
        for( i = 0; i <n;i++)
        {
            x[ i ] = ( float )( Math.pow( base, x[ i ] ) );
        }
    }

    public static void dB2lin( float[] x, int n )
    {
        Windowing.apply( x, n, s -> 1.0f / 6 );
        log2lin( x, n, 2 );
    }

    public static void lin2dB( float[] x, int n )
    {
        lin2log( x, n, 2 );
        Windowing.apply( x, n, s -> 6.0f );
    }

    public static void lin2log( float[] x, int n, float base )
    {
        int i;
        final double factor = Math.log( base );
        for( i = 0; i <n;i++)
        {
            x[ i ] = ( float )( Math.log( x[ i ] ) / factor );
        }
    }

    public static int remap_to_interval( int x, int a1, int b1, int a2, int b2 )
    {
        double ratio = 1.0f * ( b2 - a2 ) / ( b1 - a1 );
        return ( int )( ( x - a1 ) * ratio ) + a2;
    }

    public static void plot_in_matlab( float[] in, int off1, int n, float[] out, int off2, int m )
    {
        int i;
        System.out.print( "t1 = [ " );
        for( i = 0; i < n - 1; i++ )
        {
            System.out.print( ( float )i / ( n - 1 ) + ", " );
        }
        System.out.println( "1 ];" );

        System.out.print( "t2 = [ " );
        for( i = 0; i < m - 1; i++ )
        {
            System.out.print( ( float )i / ( m - 1 ) + ", " );
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

    public static void plot_in_matlab( float[] xs, float[] ys, int n )
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
        System.out.println( "plot( xs, ys, '-' );" );
    }

    public static void plot_in_matlab( float[] ys, int n )
    {
        plot_in_matlab( ys, n, 0, "" );
    }

    public static void plot_in_matlab( float[] ys, int len, int x_start, String postfix )
    {
        int i;
        System.out.println( "xs" + postfix + " = linspace( " + x_start + ", " + ( x_start + len - 1 ) + ", " + len + " );" );

        System.out.print( "ys" + postfix + " = [ " );
        for( i = 0; i < len - 1; i++ )
        {
            System.out.print( ys[ i ] + ", " );
        }
        System.out.println( ys[ len - 1 ] + " ];" );
        System.out.println( "plot( xs" + postfix + ", ys" + postfix + ", '-' );" );
    }

    public static void plot_in_matlab( float[] orig_f_xs, float[] orig_f_ys, int orig_f_len,float[] new_xs, float[] new_ys, int new_len )
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
