package SignalProcessing.LinearPrediction;

import java.util.Arrays;

/**
 * Created by Alex on 10.11.2017.
 */
public class BurgMethod
{
    private double a[];
    private double x[];
    private double f[];
    private double b[];
    private int k;
    private int coeffs;
    private int N;

    public BurgMethod( float[] original, int start_offset, int N, int coeffs )
    {
        N--;
        k = 0;
        int i;
        this.N = N;
        this.coeffs = coeffs;
        a = new double[ coeffs + 2 ];
        x = new double[ N + 1 ];
        f = new double[ N + 1 ];
        b = new double[ N + 1 ];
        for( i = 1; i <= coeffs; i++ )
        {
            a[ i ] = 0;
        }
        a[ 0 ] = 1;
        a[ coeffs + 1 ] = 0;
        for( i = 0; i < N + 1; i++ )
        {
            x[ i ] = original[ i + start_offset ];
        }
        //x = Arrays.copyOfRange( original, start_offset, start_offset + N + 1 );
        f = Arrays.copyOf( x, N + 1 );
        b = Arrays.copyOf( x, N + 1 );

        calculate_coeffs();
    }

    public double forward_L_P_error( )
    {
        double error = 0;
        double approx;
        int n, i;
        for( n = coeffs; n <= N; n++ )
        {
            approx = 0;
            for( i = 1; i <= coeffs; i++ )
            {
                approx -= a[ i ] * x[ n - i ];
            }
            error += Math.pow( x[ n ] - approx, 2 );
        }
        return error;
    }

    public double backward_L_P_error()
    {
        double error = 0;
        double approx;
        int n, i;
        for( n = 0; n <= N - k; n++ )
        {
            approx = 0;
            for( i = 0; i <= n; i++ )
            {
                approx += a[ i ] + x[ n + i ];
            }
            error += Math.pow( x[ n ] + approx, 2 );
        }
        return error;
    }

    private double get_niu()
    {
        double up = 0, down = 0;
        int n;
        for( n = 0; n <= N - k - 1; n++ )
        {
            up += -2 * ( f[ n + k + 1 ] * b[ n ] );
            down += ( f[ n + k + 1 ] * f[ n + k + 1 ] + b[ n ] * b[ n ] );
        }
        return up / down;
    }

    private void update_A( double niu )
    {
        int i;
        double aux;
        for( i = 0; i <= k / 2; i++ )
        {
            aux = a[ i ];
            a[ i ] += niu * a[ k + 1 - i ];
            a[ k + 1 - i ] += niu * aux;
        }
        if( k % 2 == 1 )
        {
            a[ ( k + 1 ) / 2 ] *= ( 1 + niu );
        }
    }

    private void update_fb( double niu )
    {
        double aux;
        for( int n = 0; n <= N - k - 1; n++ )
        {
            aux = b[ n ];
            b[ n ] += niu * f[ n + k + 1 ];
            f[ n + k + 1 ] += niu * aux;
        }
    }

    private void calculate_coeffs()
    {
        double niu;
        for( k = 0; k < coeffs; k++ )
        {
            niu = get_niu();
            update_A( niu );
            update_fb( niu );
        }
        k = coeffs - 1;
    }

    public double[] get_coeffs()
    {
        return Arrays.copyOf( a, coeffs + 1 );
    }

    public int get_nr_coeffs()
    {
        return coeffs;
    }
}
