package SignalProcessing.LiniarPrediction;

/**
 * Created by Alex on 10.11.2017.
 */
public class BurgeMethodExtrapolation
{
    private double a[];
    private double x[];
    private double f[];
    private double b[];
    private int k;
    private int coeffs;
    private int N;

    public BurgeMethodExtrapolation( double[] original, int N, int coeffs )
    {
        k = 0;
        int i;
        this.N = N;
        this.coeffs = coeffs;
        a = new double[ coeffs + 2 ];
        x = new double[ N ];
        f = new double[ N ];
        b = new double[ N ];
        for( i = 1; i <= coeffs; i++ )
        {
            a[ i ] = 0;
        }
        a[ 0 ] = 1;
        a[ coeffs + 1 ] = 0;
        for( i = 0; i < N; i++ )
        {
            x[ i ] = original[ i ];
            f[ i ] = x[ i ];
            b[ i ] = x[ i ];
        }

        calculate_coeffs();
    }

    private double forward_L_P_error( )
    {
        double error = 0;
        double approx;
        int n, i;
        for( n = k; n <= N; n++ )
        {
            approx = 0;
            for( i = 0; i <= n; i++ )
            {
                approx += a[ i ] + x[ n - i ];
            }
            error += Math.pow( x[ n ] + approx, 2 );
        }
        return error;
    }

    private double backward_L_P_error()
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
        for( n = 0; n < N - k - 1; n++ )
        {
            up += -2 * ( f[ n + k + 1 ] * b[ n ] );
            down += ( f[ n + k + 1 ] * f[ n + k + 1 ] + b[ n ] * b[ n ] );
        }
        return up / down;
    }

    private void update_A( double niu )
    {
        double olda[] = new double[ coeffs + 2 ];
        int i;
        for( i = 0; i < coeffs + 2; i++ )
        {
            olda[ i ] = a[ i ];
        }
        for( i = 1; i <= k + 1; i++ )
        {
            a[ i ] = olda[ i ] + niu * olda[ k + 1 - i ];
        }
    }

    private void update_fb( double niu )
    {
        double of[] = new double[ N ];
        double ob[] = new double[ N ];
        int n;
        for( n = 0; n < N; n++ )
        {
            of[ n ] = f[ n ];
            ob[ n ] = b[ n ];
        }
        for( n = k + 1; n < N; n++ )
        {
            f[ n ] = of[ n ] + niu * ob[ n - k - 1 ];
        }
        for( n = 0; n < N - k - 1; n++ )
        {
            b[ n ] = ob[ n ] + niu * of[ n + k + 1 ];
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

    public void predict_forward( int s, double buf[] )
    {
        int i, n;
        for( n = N + 1; n < N + s + 1; n++ )
        {
            buf[ n - N - 1 ] = 0;
            for( i = 1; i <= coeffs; i++ )
            {
                if( n - i <= N )
                {
                    buf[ n - N - 1 ] -= a[ i ] * x[ n - i ];
                }
                else
                {
                    buf[ n - N - 1 ] -= a[ i ] * buf[ n - i - N - 1 ];
                }
            }
        }
    }
}
