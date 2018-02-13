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

import java.util.ArrayList;
import java.util.List;

import static Utils.Utils.log2lin;
import static Utils.Utils.next_power_of_two;

/**
 * Created by Alex on 24.11.2017.
 */
public class FIR
{
    private int ff_coeff_nr;
    private double[] ff;

    public void apply( double[] x, Interval range ) throws DataSourceException
    {

        if( x.length < range.r || range.l < 0 )
        {
            throw new DataSourceException( "Supplied array does not have the expected size to properly apply the filter", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        int i, j;
        double newVal;

        for( i = range.r - 1; i >= range.l; i-- )
        {
            newVal = 0;
            for( j = 0; j < Math.min( i + 1, ff_coeff_nr ); j++ ) // ff[j]
            {
                newVal += ff[ j ] * x[ i - j ];
            }
            x[ i ] = newVal;
        }
    }

    public FIR( double coeffs[], int n )
    {
        int i;
        ff = new double[ n ];
        ff_coeff_nr = n;
        System.arraycopy( coeffs, 0, ff, 0, n );
    }

    public static FIR fromFreqResponse( double[] x, int nr_of_frequencies, int sample_rate, int filter_length ) throws DataSourceException
    {
        return fromFreqResponse( x, nr_of_frequencies, sample_rate, filter_length, false );
    }

    public static FIR fromFreqResponse( double[] x, int nr_of_frequencies, int sample_rate, int filter_length, boolean remove_DC_offset ) throws DataSourceException
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
        int interm_filter_length = next_power_of_two( sample_rate - 1 );
        int interm_length = interm_filter_length / 2;
        double[] interm_frq_resp = new double[ interm_length ];
        Complex frq[] = new Complex[ interm_filter_length ];
        double final_filter[] = new double[ filter_length ];

        //Utils.plot_in_matlab( new double[]{ 0, 0 }, 0, 2, x, 0, nr_of_frequencies + 1 );

        lin_interpolator.resize( x, nr_of_frequencies + 1, interm_frq_resp, interm_length );

        log2lin( interm_frq_resp, interm_length, 2 );

        if( remove_DC_offset )
        {
            interm_frq_resp[ 0 ] = 0;
        }

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
        for( i = interm_length; i <= interm_filter_length / 2; i++ )
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

    public int getFf_coeff_nr()
    {
        return ff_coeff_nr;
    }

    public double[] getFf()
    {
        return ff;
    }

    public static double[] pass_cut_freq_resp( int nr_of_frequencies, int cutoff_frequency, int sample_rate, double low_rolloff, double high_rolloff ) throws DataSourceException
    {
        int i;
        double[] frq_resp = new double[ nr_of_frequencies + 1 ];
        final double frq_step = ( double )sample_rate / 2 / nr_of_frequencies;
        final double cutoff_position = ( cutoff_frequency / frq_step );
        final int first_low_position = ( int )cutoff_position;

        frq_resp[ first_low_position ] = low_rolloff * ( Math.log( ( cutoff_position / first_low_position ) ) / Math.log( 2 ) );
        if( first_low_position + 1 <= nr_of_frequencies )
        {
            frq_resp[ first_low_position + 1 ] = high_rolloff * ( Math.log( ( first_low_position + 1 ) / cutoff_position ) / Math.log( 2 ) );
        }
        for( i = first_low_position + 2; i <= nr_of_frequencies; i++ )
        {
            frq_resp[ i ] = frq_resp[ first_low_position + 1 ] + high_rolloff * ( Math.log( ( double )i / ( first_low_position + 1 ) ) / Math.log( 2 ) );
        }
        for( i = first_low_position - 1; i > 0; i-- )
        {
            frq_resp[ i ] = frq_resp[ first_low_position ] + low_rolloff * ( Math.log( ( double )( first_low_position ) / ( i ) ) / Math.log( 2 ) );
        }
        frq_resp[ 0 ] = frq_resp[ 1 ] + low_rolloff ;

        Windowing.apply( frq_resp, frq_resp.length, ( v ) -> 1.0 / 6 );
        return frq_resp;
        //return fromFreqResponse( frq_resp, nr_of_frequencies, sample_rate, filter_length );

    }

    public static double[] get_RIAA_response( int nr_of_frequencies, int sample_rate ) throws DataSourceException
    {
        List< double[] > responses = new ArrayList<>();
        int i, j;
        double[] accumulator;
        double[] source;
        final double plus_20_dB = 20.0 / 6.0;

        responses.add( pass_cut_freq_resp( nr_of_frequencies, 10, sample_rate, 0, -1.29 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 50, sample_rate, 0, -2.39 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 80, sample_rate, 0, -1.03 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 300, sample_rate, 0, 1.32 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 500, sample_rate, 0, 0.38 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 2500, sample_rate, 0, -1.49 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 5000, sample_rate, 0, -0.65 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 8000, sample_rate, 0, -0.85 ) );

        accumulator = responses.get( 0 );

        for( j = 1; j < responses.size(); j++ )
        {
            source = responses.get( j );
            for( i = 0; i <= nr_of_frequencies; i++ )
            {
                accumulator[ i ] += source[ i ];
            }
        }
        for( i = 0; i <= nr_of_frequencies; i++ )
        {
            accumulator[ i ] += plus_20_dB;
        }
        return accumulator;
    }

    public static double[] get_inverse_RIAA_response( int nr_of_frequencies, int sample_rate ) throws DataSourceException
    {
        List< double[] > responses = new ArrayList<>();
        int i, j;
        double[] accumulator;
        double[] source;
        final double minus_20_dB = -20.0 / 6.0;

        responses.add( pass_cut_freq_resp( nr_of_frequencies, 10, sample_rate, -48, 1.29 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 50, sample_rate, 0, 2.39 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 80, sample_rate, 0, 1.03 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 300, sample_rate, 0, -1.32 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 500, sample_rate, 0, -0.38 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 2500, sample_rate, 0, 1.49 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 5000, sample_rate, 0, 0.65 ) );
        responses.add( pass_cut_freq_resp( nr_of_frequencies, 8000, sample_rate, 0, 0.85 ) );

        accumulator = responses.get( 0 );

        for( j = 1; j < responses.size(); j++ )
        {
            source = responses.get( j );
            for( i = 0; i < nr_of_frequencies; i++ )
            {
                accumulator[ i ] += source[ i ];
            }
        }
        for( i = 0; i < nr_of_frequencies; i++ )
        {
            accumulator[ i ] += minus_20_dB;
        }
        return accumulator;
    }
}
