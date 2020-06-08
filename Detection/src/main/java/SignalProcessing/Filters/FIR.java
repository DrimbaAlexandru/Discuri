package SignalProcessing.Filters;

import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import SignalProcessing.FourierTransforms.Fourier;
import SignalProcessing.FunctionApproximation.FunctionApproximation;
import SignalProcessing.FunctionApproximation.LinearInterpolation;
import SignalProcessing.Windowing.Windowing;
import Utils.DataTypes.Complex;
import Utils.DataTypes.Interval;
import Utils.DataTypes.MyPair;
import Utils.Util_Stuff;

import java.util.Arrays;

import static Utils.Util_Stuff.plot_in_matlab;


/**
 * Created by Alex on 24.11.2017.
 */
public class FIR
{
    private int ff_coeff_nr;
    private float[] ff;

    private static final float[] riaa_points = { 20, 25, 31, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000, 25000, 48000 };
    private static final float[] riaa_resp = { 19.274f, 18.954f, 18.516f, 17.792f, 16.946f, 15.852f, 14.506f, 13.088f, 11.563f, 9.809f, 8.219f, 6.677f, 5.179f, 3.784f, 2.648f, 1.642f, 0.751f, 0, -0.744f, -1.643f, -2.589f, -3.700f, -5.038f, -6.605f, -8.210f, -9.980f, -11.894f, -13.734f, -15.609f, -17.708f, -19.620f, -21.542f, -27.187f };

    public final static FIR derivation_FIR = new FIR( new float[]{ 1, -1 }, 2 );
    public final static FIR identity_FIR = new FIR( new float[]{ 1 }, 1 );

    public void apply( float[] x, Interval range ) throws DataSourceException
    {

        if( x.length < range.r || range.l < 0 )
        {
            throw new DataSourceException( "Supplied array does not have the expected size to properly apply the filter", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        int i, j;
        float newVal;

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

    public FIR( float coeffs[], int n )
    {
        int i;
        ff = new float[ n ];
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
    public static FIR fromFreqResponse( float[] frequencies, float[] amplitudes, int nr_of_frequencies, int sample_rate, int filter_length ) throws DataSourceException
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
        float[] ifft_xs = new float[ ifft_size ];
        float[] ifft_ys = new float[ ifft_size ];
        boolean interpolate_logarithmically = false;
        Complex[] ifft_in = new Complex[ ifft_size ];
        Complex[] ifft_out;
        float[] final_filter = new float[ filter_length ];
        int i;

        for( i = 0; i < ifft_size; i++ )
        {
            ifft_xs[ i ] = 1.0f * sample_rate / ifft_size * i;
        }
        if( interpolate_logarithmically )
        {
            ifft_xs[ 0 ] = 1e-12f;
            Util_Stuff.lin2log( ifft_xs, ifft_size, 2 );
            if( frequencies[ 0 ] < 1e-10f )
            {
                frequencies[ 0 ] = 1e-10f;
            }
            Util_Stuff.lin2log( frequencies, nr_of_frequencies, 2 );
        }
        Windowing.apply( amplitudes, nr_of_frequencies, ( Float x ) -> 1.0f / 6 );
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
                ifft_xs[ i ] = 1.0f * sample_rate / ifft_size * i;
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
            final_filter[ filter_length - i - 1 ] = final_filter[ i ] = ifft_out[ f ].r;
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

    public float[] getFf()
    {
        return ff;
    }

    public static void add_pass_cut_freq_resp( float[] frequencies, float[] amplitudes, int nr_of_frequencies, int cutoff_frequency, float low_gain_per_octave, float high_gain_per_octave ) throws DataSourceException
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

    public static MyPair< float[], float[] > get_RIAA_response()
    {
        return new MyPair<>( Arrays.copyOf( riaa_points, riaa_points.length ), Arrays.copyOf( riaa_resp, riaa_resp.length ) );
    }

    public static MyPair< float[], float[] > get_flat_response()
    {
        return new MyPair<>( new float[]{ 1000 }, new float[]{ 0 } );
    }

    public static MyPair< float[], float[] > get_inverse_RIAA_response()
    {
        MyPair< float[], float[] > resp = get_RIAA_response();
        float[] resp_ref = resp.getRight();
        for( int i = 0; i < resp.getRight().length; i++ )
        {
            resp_ref[ i ] *= -1;
        }
        return resp;
    }
}
