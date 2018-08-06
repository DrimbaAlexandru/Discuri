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
    private float[] fft_xs = null;
    private float[] fft_ys = null;
    int N;

    @Override
    public void prepare( float[] xs, float[] ys, int n ) throws DataSourceException
    {
        if( xs.length > n )
        {
            throw new DataSourceException( "n larger than array size", DataSourceExceptionCause.INTERPOLATION_EXCEPTION );
        }
        if( !Util_Stuff.is_power_of_two( n ) )
        {
            throw new DataSourceException( "n must be a power of 2", DataSourceExceptionCause.INTERPOLATION_EXCEPTION );
        }
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
        fft_xs = new float[ N ];
        fft_ys = new float[ N ];
        for( int i = 0; i < N; i++ )
        {
            fft_xs[ i ] = i * ( 1.0f / N );
            fft_ys[ i ] = ys[ i ];
        }
    }

    @Override
    public void get_values( float[] int_xs, float[] int_ys, int n ) throws DataSourceException
    {
        if( n > int_ys.length )
        {
            throw new DataSourceException( "n larger than array size", DataSourceExceptionCause.INTERPOLATION_EXCEPTION );
        }
        float lin_xs[], lin_ys[];
        int_xs = new float[ n ];
        int lin_n;

        if( n <= N )
        {
            lin_xs = fft_xs;
            lin_ys = fft_ys;
            lin_n = N;
        }
        else
        {
            lin_n = Util_Stuff.next_power_of_two( n - 1 );
            Complex[] ifft = new Complex[ lin_n ];
            lin_xs = new float[ lin_n ];
            lin_ys = new float[ lin_n ];
            for( int i = 0; i < N; i++ )
            {
                ifft[ i ] = fft_coeffs[ i ];
            }
            for( int i = N; i < lin_n; i++ )
            {
                ifft[ i ] = new Complex();
            }
            ifft = Fourier.IFFT( ifft, lin_n );
            for( int i = 0; i < lin_n; i++ )
            {
                lin_xs[ i ] = i * ( 1.0f / lin_n );
                lin_ys[ i ] = ifft[ i ].r();
            }
        }

        LinearInterpolation lin = new LinearInterpolation();
        lin.prepare( lin_xs, lin_ys, lin_n );
        for( int i = 0; i < n; i++ )
        {
            int_xs[ i ] = i * ( 1.0f / n );
        }
        lin.get_values( int_xs, int_ys, n );
    }
}
