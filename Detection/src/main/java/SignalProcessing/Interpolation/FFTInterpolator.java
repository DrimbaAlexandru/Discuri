package SignalProcessing.Interpolation;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import SignalProcessing.FourierTransforms.Fourier;
import Utils.Complex;

import static Utils.Utils.is_power_of_two;
import static Utils.Utils.next_power_of_two;

/**
 * Created by Alex on 06.02.2018.
 */
public class FFTInterpolator implements Interpolator
{
    @Override
    public void resize( double[] in, int n, double[] out, int m ) throws DataSourceException
    {
        if( is_power_of_two( ( double )( m - 1 ) / ( n - 1 ) ) )
        {
            upsample( in, n, out, m );
        }
        else
        {
            int interm_len = next_power_of_two( ( double )( m - 1 ) / ( n - 1 ) ) * ( n - 1 ) + 1;
            double[] interm_buf = new double[ interm_len ];
            upsample( in, n, interm_buf, interm_len );
            LinearInterpolator linearInterpolator = new LinearInterpolator();
            linearInterpolator.resize( interm_buf, interm_len, out, m );
        }
    }

    public void upsample( double[] in, int n, double[] out, int m ) throws DataSourceException
    {
        if( in.length < n || out.length < m || !is_power_of_two( ( double )( m - 1 ) / ( n - 1 ) ) )
        {
            throw new DataSourceException( "Array sizes are incorrect or their ratio is not a power of two", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        int ratio = ( m - 1 ) / ( n - 1 );
        int init_fft_size = next_power_of_two( n - 1 );
        int final_fft_size = init_fft_size * ratio;
        int i, j;
        Complex[] in_coeffs = new Complex[ init_fft_size ];
        Complex[] out_coeffs = new Complex[ final_fft_size ];
        for( i = 0; i < n; i++ )
        {
            in_coeffs[ i ] = new Complex( in[ i ], 0 );
        }
        for( i = n; i < init_fft_size; i++ )
        {
            in_coeffs[ i ] = new Complex( 0, 0 );
        }
        in_coeffs = Fourier.FFT( in_coeffs, init_fft_size );
        //out_coeffs[ 0 ] = in_coeffs[ 0 ];
        for( i = 0; i <= init_fft_size / 2; i++ )
        {
            out_coeffs[ i ] = in_coeffs[ i ];//.sub( in_coeffs[ init_fft_size - i ] );
        }
        for( i = 1; i < init_fft_size / 2; i++ )
        {
            out_coeffs[ final_fft_size - i ] = in_coeffs[ init_fft_size - i ];
        }
        for( i = init_fft_size / 2 + 1; i <= final_fft_size - init_fft_size / 2; i++ )
        {
            out_coeffs[ i ] = new Complex();
        }
        out_coeffs = Fourier.IFFT( out_coeffs, final_fft_size );
        for( i = 0; i < m; i++ )
        {
            out[ i ] = out_coeffs[ i ].r();
        }
    }
}
