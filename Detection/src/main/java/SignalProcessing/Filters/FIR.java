package SignalProcessing.Filters;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import SignalProcessing.FourierTransforms.Fourier;
import SignalProcessing.Interpolation.FFTInterpolator;
import SignalProcessing.Interpolation.Interpolator;
import SignalProcessing.Interpolation.LinearInterpolator;
import SignalProcessing.Windowing.Windowing;
import Utils.Complex;
import Utils.Interval;

/**
 * Created by Alex on 24.11.2017.
 */
public class FIR
{
    private int tap_nr;
    private double[] b;
/*
    public void apply_with_implicit_left_zero_padding( double[] x, int N ) throws DataSourceException
    {
        if( x.length < N )
        {
            throw new DataSourceException( "Supplied array does not have enough space to properly apply the filter", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        int i, j, buf_idx = 0;
        double[] ybuf = new double[ tap_nr ];

        for( i = 0; i < tap_nr; i++ ) // y[i]
        {
            ybuf[ buf_idx ] = 0;
            for( j = 0; j <= i; j++ ) // b[j]
            {
                ybuf[ buf_idx ] += b[ j ] * x[ i - j ];
            }
            buf_idx++;
        }

        for( i = tap_nr; i < N; i++ )
        {
            if( buf_idx == tap_nr )
            {
                buf_idx = 0;
            }
            x[ i - tap_nr ] = ybuf[ buf_idx ];
            ybuf[ buf_idx ] = 0;
            for( j = 0; j < tap_nr; j++ ) // b[j]
            {
                ybuf[ buf_idx ] += b[ j ] * x[ i - j ];
            }
            buf_idx++;
        }

        for( i = N - tap_nr; i < N; i++ )
        {
            if( buf_idx == tap_nr )
            {
                buf_idx = 0;
            }
            x[ i ] = ybuf[ buf_idx ];
            buf_idx++;
        }
    }
*/
    public void apply( double[] x, Interval range ) throws DataSourceException
    {

        if( x.length > range.r || range.l < 0 )
        {
            throw new DataSourceException( "Supplied array does not have the expected size to properly apply the filter", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        int i, j;
        double newVal;

        for( i = range.r - 1; i >= range.l; i-- )
        {
            newVal = 0;
            for( j = 0; j < Math.min( i + 1, tap_nr ); j++ ) // b[j]
            {
                newVal += b[ j ] * x[ i - j ];
            }
            x[ i ] = newVal;
        }
    }

    public FIR( double coeffs[], int n )
    {
        int i;
        b = new double[ n ];
        tap_nr = n;
        System.arraycopy( coeffs, 0, b, 0, n );
    }

    public static FIR fromFreqResponse( double[] x, int nr_of_frequencies, int sample_rate, int filter_length ) throws DataSourceException
    {
        if( !Utils.Utils.is_power_of_two( nr_of_frequencies ) || nr_of_frequencies == 1 )
        {
            throw new DataSourceException( "Number of frequencies must be a power of two", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( filter_length % 2 == 0 )
        {
            throw new DataSourceException( "Filter length must be an odd number", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( sample_rate % 2 != 0 )
        {
            throw new DataSourceException( "Sample rate must be a number", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( filter_length > sample_rate )
        {
            throw new DataSourceException( "Filter length cannot be larger than sample rate", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        int i, copy_offset;
        int interm_filter_length = sample_rate % 2 == 0 ? sample_rate + 1 : sample_rate;
        final Interpolator interpolator = new FFTInterpolator();
        Complex frq[] = new Complex[ nr_of_frequencies * 2 ];
        double filter[] = new double[ nr_of_frequencies * 2 + 1 ];
        double resized_filter[] = new double[ interm_filter_length ];
        double final_filter[] = new double[ filter_length ];

        frq[ 0 ] = new Complex( 0, 0 );
        for( i = 1; i <= nr_of_frequencies; i++ )
        {
            if( i % 2 == 0 )
            {
                frq[ i ] = new Complex( x[ i ], 0 );
            }
            else
            {
                frq[ i ] = new Complex( -x[ i ], 0 );
            }
        }
        for( i = nr_of_frequencies + 1; i < nr_of_frequencies * 2; i++ )
        {
            frq[ i ] = new Complex( 0, 0 );
        }

        frq = Fourier.IFFT( frq, nr_of_frequencies * 2 );

        for( i = 0; i <= nr_of_frequencies / 2; i++ )
        {
            filter[ i ] = frq[ i * 2 ].r();
            filter[ nr_of_frequencies - i ] = frq[ i * 2 ].r();
        }

        interpolator.resize( filter, nr_of_frequencies + 1, resized_filter, interm_filter_length );

        copy_offset = ( interm_filter_length - 1 ) / 2 - ( filter_length - 1 ) / 2;
        for( i = 0; i < filter_length; i++ )
        {
            final_filter[ i ] = resized_filter[ copy_offset + i ];
        }

        return new FIR( final_filter, filter_length );
    }

    public static FIR fromFreqResponse2( double[] x, int nr_of_frequencies, int sample_rate, int filter_length ) throws DataSourceException
    {
        if( nr_of_frequencies == 1 )
        {
            throw new DataSourceException( "Number of frequencies must be at least 2", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( filter_length % 2 == 0 )
        {
            throw new DataSourceException( "Filter length must be an odd number", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( sample_rate % 2 != 0 )
        {
            throw new DataSourceException( "Sample rate must be an even number", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( filter_length > sample_rate )
        {
            throw new DataSourceException( "Filter length cannot be larger than sample rate", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        final Interpolator interpolator = new FFTInterpolator();
        final Interpolator lin_interpolator = new LinearInterpolator();

        int i, copy_offset;
        int interm_filter_length = Utils.Utils.next_power_of_two( sample_rate - 1 );
        int interm_length = interm_filter_length / 2;
        double[] interm_frq_resp = new double[ interm_length ];
        Complex frq[] = new Complex[ interm_filter_length ];
        double final_filter[] = new double[ filter_length ];

        //x[ 0 ] = x[ 1 ];
        lin_interpolator.resize( x, nr_of_frequencies + 1, interm_frq_resp, interm_length );
        //x[ 0 ] = 0;
        //interm_frq_resp[ 0 ] = 0;
        Utils.Utils.log2lin( interm_frq_resp, interm_length, 2 );

        for( i = 0; i < interm_length; i++ )
        {
            if( i % 2 == 0 )
            {
                frq[ i ] = new Complex( interm_frq_resp[ i ], 0 );
            }
            else
            {
                frq[ i ] = new Complex( -interm_frq_resp[ i ], 0 );
            }
        }
        for( i = interm_length; i <= interm_filter_length/2; i++ )
        {
            if( i % 2 == 0 )
            {
                frq[ i ] = new Complex( 0, 0 );
            }
            else
            {
                frq[ i ] = new Complex( 0, 0 );
            }
        }
        for( i = interm_filter_length / 2 + 1; i < interm_filter_length; i++ )
        {
            frq[ i ] = new Complex( 0, 0 );
        }

        frq = Fourier.IFFT( frq, interm_filter_length );
        copy_offset = interm_filter_length / 2 - ( filter_length - 1 ) / 2;

        for( i = 0; i < filter_length; i++ )
        {
            final_filter[ i ] = frq[ copy_offset + i ].r();
        }
        Windowing.apply( final_filter, filter_length, ( v ) -> 1.0 / ( ( double )interm_filter_length / 2 / ( filter_length ) ) );

        return new FIR( final_filter, filter_length );
    }

    public int getTap_nr()
    {
        return tap_nr;
    }

    public double[] getB()
    {
        return b;
    }
}
