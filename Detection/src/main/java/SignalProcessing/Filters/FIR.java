package SignalProcessing.Filters;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import SignalProcessing.FourierTransforms.Fourier;
import SignalProcessing.FunctionApproximation.FunctionApproximation;
import SignalProcessing.FunctionApproximation.LinearInterpolation;
import SignalProcessing.Windowing.Windowing;
import Utils.Complex;
import Utils.Interval;
import Utils.MyPair;
import Utils.Util_Stuff;

import java.util.Arrays;

import static Utils.Util_Stuff.plot_in_matlab;


/**
 * Created by Alex on 24.11.2017.
 */
public class FIR
{
    private int ff_coeff_nr;
    private double[] ff;

    private static final double[] riaa_points = { 20, 25, 31, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000, 25000, 48000 };
    private static final double[] riaa_resp = { 19.274, 18.954, 18.516, 17.792, 16.946, 15.852, 14.506, 13.088, 11.563, 9.809, 8.219, 6.677, 5.179, 3.784, 2.648, 1.642, 0.751, 0, -0.744, -1.643, -2.589, -3.700, -5.038, -6.605, -8.210, -9.980, -11.894, -13.734, -15.609, -17.708, -19.620, -21.542, -27.187 };

    public final static FIR derivation_FIR = new FIR( new double[]{ 1, -1 }, 2 );
    public final static FIR identity_FIR = new FIR( new double[]{ 1 }, 1 );

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

    /**
     * @param frequencies: frequencies in Hertz, ordered, between 0 - Nyquist freq
     * @param amplitudes: in dB
     * @param nr_of_frequencies
     * @param sample_rate
     * @param filter_length
     * @return
     * @throws DataSourceException
     */
    public static FIR fromFreqResponse( double[] frequencies, double[] amplitudes, int nr_of_frequencies, int sample_rate, int filter_length ) throws DataSourceException
    {
        if( frequencies.length < nr_of_frequencies || amplitudes.length < nr_of_frequencies )
        {
            throw new DataSourceException( "Array sizes less than expected length", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( filter_length % 2 != 1 )
        {
            throw new DataSourceException( "Filter length must be odd", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        int ifft_size = Util_Stuff.next_power_of_two( ( filter_length - 1 ) );
        FunctionApproximation fa = new LinearInterpolation();
        double[] ifft_xs = new double[ ifft_size ];
        double[] ifft_ys = new double[ ifft_size ];
        boolean interpolate_logarithmically = false;
        Complex[] ifft_in = new Complex[ ifft_size ];
        Complex[] ifft_out;
        double[] final_filter = new double[ filter_length ];
        int i;

        for( i = 0; i < ifft_size; i++ )
        {
            ifft_xs[ i ] = 1.0 * sample_rate / ifft_size * i;
        }
        if( interpolate_logarithmically )
        {
            ifft_xs[ 0 ] = 1e-12;
            Util_Stuff.lin2log( ifft_xs, ifft_size, 2 );
            if( frequencies[ 0 ] < 1e-10 )
            {
                frequencies[ 0 ] = 1e-10;
            }
            Util_Stuff.lin2log( frequencies, nr_of_frequencies, 2 );
        }
        Windowing.apply( amplitudes, nr_of_frequencies, ( Double x ) -> 1.0 / 6 );
        Util_Stuff.log2lin( amplitudes, nr_of_frequencies, 2 );

        fa.prepare( frequencies, amplitudes, nr_of_frequencies );
        fa.get_values( ifft_xs, ifft_ys, ifft_size );

        //plot_in_matlab( frequencies, amplitudes, nr_of_frequencies, ifft_xs, ifft_ys, ifft_size );
        for( i = 0; i < ifft_size / 2; i++ )
        {
            ifft_ys[ ifft_size / 2 + i ] = ifft_ys[ ifft_size / 2 - i ];
        }
        if( interpolate_logarithmically )
        {
            for( i = 0; i < ifft_size; i++ )
            {
                ifft_xs[ i ] = 1.0 * sample_rate / ifft_size * i;
            }
        }
        for( i = 0; i < ifft_size; i++ )
        {
            ifft_in[ i ] = new Complex( ifft_ys[ i ], 0 );
        }
        ifft_out = Fourier.IFFT( ifft_in, ifft_size );
        int f = 0;
        for( i = ( filter_length ) / 2; i >= 0; i-- )
        {
            final_filter[ filter_length - i - 1 ] = final_filter[ i ] = ifft_out[ f ].r();
            f++;
        }
        Windowing.apply( final_filter, filter_length, Windowing.Blackman_window );
        for( i = 0; i < filter_length; i++ )
        {
            final_filter[ i ] /= ifft_size;
        }
        //Utils.plot_in_matlab( final_filter, filter_length );
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

    public static void add_pass_cut_freq_resp( double[] frequencies, double[] amplitudes, int nr_of_frequencies, int cutoff_frequency, double low_gain_per_octave, double high_gain_per_octave ) throws DataSourceException
    {
        int i;
        for( i = 0; i < nr_of_frequencies; i++ )
        {
            if( frequencies[ i ] < 1 )
            {
                amplitudes[ i ] += low_gain_per_octave * ( Math.log( cutoff_frequency ) / Math.log( 2 ) );
                continue;
            }
            if( frequencies[ i ] > cutoff_frequency )
            {
                amplitudes[ i ] += high_gain_per_octave * ( Math.log( frequencies[ i ] / cutoff_frequency ) / Math.log( 2 ) );
            }
            else
            {
                amplitudes[ i ] += low_gain_per_octave * ( Math.log( cutoff_frequency / frequencies[ i ] ) / Math.log( 2 ) );
            }
        }
    }

    public static MyPair< double[], double[] > get_RIAA_response()
    {
        return new MyPair<>( Arrays.copyOf( riaa_points, riaa_points.length ), Arrays.copyOf( riaa_resp, riaa_resp.length ) );
    }

    public static MyPair< double[], double[] > get_inverse_RIAA_response()
    {
        MyPair< double[], double[] > resp = get_RIAA_response();
        double[] resp_ref = resp.getRight();
        for( int i = 0; i < resp.getRight().length; i++ )
        {
            resp_ref[ i ] *= -1;
        }
        return resp;
    }
}
