package SignalProcessing.FunctionApproximation;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import SignalProcessing.FourierTransforms.Fourier;
import Utils.Complex;
import Utils.Util_Stuff;

/**
 * Created by Alex on 07.06.2018.
 */
public class FourierInterpolator implements FunctionApproximation
{
    private Complex[] fft_coeffs = null;
    float dxs = 1;
    int N;

    /**
     * This method assumes xs are equidistant; xs[0] = 0, xs[1] = 1, ... , xs[n-1] = n-1.
     * @param ys
     * @param n
     * @throws DataSourceException
     */
    public void prepare( float[] ys, int n ) throws DataSourceException
    {
        if( ys.length > n )
        {
            throw new DataSourceException( "n larger than array size", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( !Util_Stuff.is_power_of_two( n ) )
        {
            throw new DataSourceException( "n must be a power of 2", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( n < 2 )
        {
            throw new DataSourceException( "n must be at least 2", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        dxs = 1;

        Complex[] signal = new Complex[ n ];
        for( int i = 0; i < n; i++ )
        {
            signal[ i ] = new Complex( ys[ i ], 0 );
        }
        fft_coeffs = Fourier.FFT( signal, n );
        for( int i = 1; i < n / 2; i++ )
        {
            //fft_coeffs[ i ].add( fft_coeffs[ n - i ] );
            //fft_coeffs[ i ] = Complex.fromAmplPhase( fft_coeffs[ i ].Ampl() + fft_coeffs[ n - i ].Ampl(), fft_coeffs[ i ].sinTheta() + Math.PI );
            fft_coeffs[ i ].mul( new Complex( 2, 0 ) );
            fft_coeffs[ n - i ].set( 0, 0 );
        }
        N = n;
    }

    @Override
    public void prepare( float[] xs, float[] ys, int n ) throws DataSourceException
    {
        dxs = xs[ 1 ] - xs[ 0 ];
        float tolerance = Math.ulp( Math.max( 1.0f, dxs ) );
        for( int i = 2; i < n; i++ )
        {
            if( Math.abs( ( xs[ i ] - xs[ i - 1 ] ) - dxs ) > tolerance * 2 )
            {
                throw new DataSourceException( "x values are not equidistant" );
            }
        }
        prepare( ys, n );
        dxs = xs[ 1 ] - xs[ 0 ];
    }

    @Override
    public void get_values( float[] int_xs, float[] int_ys, int n ) throws DataSourceException
    {
        if( n > int_ys.length || n > int_xs.length )
        {
            throw new DataSourceException( "n larger than array size", DataSourceExceptionCause.INTERPOLATION_EXCEPTION );
        }

        for( int i = 0; i < n; i++ )
        {
            int_ys[ i ] = Fourier.IDFT( int_xs[ i ] / dxs, fft_coeffs, N );
        }
    }
}
