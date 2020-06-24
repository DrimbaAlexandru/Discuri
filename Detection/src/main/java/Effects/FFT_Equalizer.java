package Effects;

import AudioDataSource.AudioSamplesWindow;
import Utils.DataTypes.EffectType;
import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.Filters.FIR;
import SignalProcessing.FourierTransforms.Fourier;
import SignalProcessing.Windowing.Windowing;
import Utils.DataTypes.Interval;
import Utils.Util_Stuff;
import net.jafama.FastMath;

import java.util.Arrays;
import java.util.function.Function;

/**
 * Created by Alex on 08.02.2018.
 */
public class FFT_Equalizer implements IEffect
{
    private FIR fir_filter = null;
    private FIR_Equalizer filter = null;
    private float progress = 0;
    final private Function< Float, Float > window_function = Windowing.Hann_window;
    private int OLA_window_size = 0;   /* Length of the OLA window size, without zero padding. OLA step is this / 2  */
    private int FFT_length = 0;        /* Length of each FFT operation, or of zero padded OLA window                 */

    private float EQ_i[];               /* imaginary component of the frequency-domain filter   */
    private float EQ_r[];               /* real component of the frequency-domain filter        */
    private float signal_r[];           /* real component of the processed signal               */
    private float signal_i[];           /* imaginary component of the processed signal          */
    private float OLA_window[];         /* Overlap-Add window coefficients                      */
    private float OLA_buffer[][];       /* Overlap-Add accumulator buffer                       */

    private boolean init_done = false;
    private int prev_ch_nr = 0;


    private void init( int channel_count ) throws DataSourceException
    {
        filter = new FIR_Equalizer();

        EQ_r = new float[ FFT_length ];
        EQ_i = new float[ FFT_length ];
        signal_r = new float[ FFT_length ];
        signal_i = EQ_i; /* These two arrays are never used at the same time. Using two variables to keep a clear separation of the intended use */
        OLA_window = new float[ OLA_window_size ];
        OLA_buffer = new float[ channel_count ][ OLA_window_size * 2 ];

        int FIR_offset;             /* Offset of the first FIR coeff in the EQ sample buf   */
        int i;

        FIR_offset = FFT_length - ( fir_filter.getFf_coeff_nr() - 1 ) / 2;

        /*
         * Convert from FIR filter coefficients to a frequency response using FFT and zero padding.
         * Place the FIR coefficients with the center one at the beginning of the EQ samples
         */
//        Arrays.fill( EQ_i, 0.0f );
//        Arrays.fill( EQ_r, 0.0f );
        for( i = 0; i < fir_filter.getFf_coeff_nr(); i++ )
        {
            EQ_r[ ( FIR_offset + i ) % FFT_length ] = fir_filter.getFf()[ i ];
        }
        Fourier.FFT_inplace( EQ_r, EQ_i, FFT_length );

        /*
         * Only store the intensity of each frequency, as we are not interested in its phase (which ideally should be 0 )
         */
        for( i = 0; i < FFT_length; i++ )
        {
            EQ_r[ i ] = ( float )FastMath.sqrt( EQ_r[ i ] * EQ_r[ i ] + EQ_i[ i ] * EQ_i[ i ] ) * FFT_length;
        }

        Arrays.fill( OLA_window, 1 );
        Windowing.apply( OLA_window, OLA_window_size, window_function );

        filter.setFilter( fir_filter );
        filter.setMax_chunk_size( OLA_window_size * 2 );

        init_done = true;
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        /*
        *  Local variables
        */
        int i, k, j;
        float signal_src_ptr[];     /* Pointer to the ASW signal array                      */
        int OLA_zero_padding;       /* Amount of zero padding required on each side of the OLA window           */

        AudioSamplesWindow win;
        Interval FFT_grab_interval;         /* Interval on which data needs to be grabbed for FFTs  */
        Interval FFT_applicable_interval;   /* Interval on which FFT Equalization can actually be performed */

        if( fir_filter == null )
        {
            throw new DataSourceException( "Filter not set.", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        /*
        *  Variable initialization
        */
        if( !init_done || prev_ch_nr != dataSource.get_channel_number() )
        {
            init( dataSource.get_channel_number() );
        }
        prev_ch_nr = dataSource.get_channel_number();

        OLA_zero_padding = ( FFT_length - OLA_window_size ) / 2;

        interval.limit( 0, dataSource.get_sample_number() );

        /* We need an extra OLA window on each side to account for the effect of the filters in zero padded areas */
        FFT_grab_interval = new Interval( interval.l - OLA_window_size, interval.r + OLA_window_size, false );
        FFT_grab_interval.limit( 0, dataSource.get_sample_number() );
        FFT_grab_interval.r = FFT_grab_interval.l + FFT_grab_interval.get_length() / ( OLA_window_size / 2 ) * ( OLA_window_size / 2 );   /* make the length a multiple of ( OLA_window_size / 2 ) */

        FFT_applicable_interval = new Interval( 0, 0 );
        FFT_applicable_interval.l = Math.max( interval.l, FFT_grab_interval.l + OLA_window_size );
        FFT_applicable_interval.r = Math.min( interval.r, FFT_grab_interval.r - OLA_window_size );

        progress = 0;

        /*
        *   STFT OLA where possible
        */
        for( i = FFT_grab_interval.l; i <= FFT_grab_interval.r - OLA_window_size; )
        {
            win = dataSource.get_samples( i, OLA_window_size );
            if( win.get_length() < OLA_window_size )
            {
                throw new DataSourceException( "Read less samples than expected", DataSourceExceptionCause.INVALID_STATE );
            }

            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                signal_src_ptr = win.getSamples()[ k ];

                Arrays.fill( signal_r, 0, OLA_zero_padding, 0.0f );
                Arrays.fill( signal_r, FFT_length - OLA_zero_padding, FFT_length, 0.0f );
                Arrays.fill( signal_i, 0.0f );

                /* Copy the input signal values into the working array and apply the OLA windowing */
                for( j = 0; j < OLA_window_size; j++ )
                {
                    signal_r[ j + OLA_zero_padding ] = signal_src_ptr[ j ] * OLA_window[ j ];
                }

                /* Compute the FFT of the input signal */
                Fourier.FFT_inplace( signal_r, signal_i, FFT_length );

                /* Multiply with the FFT of the equalization filter */
                for( j = 0; j < FFT_length; j++ )
                {
                    signal_r[ j ] *= EQ_r[ j ];
                    signal_i[ j ] *= EQ_r[ j ];
                }

                /* Compute the IFFT of the equalized input signal */
                Fourier.IFFT_inplace( signal_r, signal_i, FFT_length );

                /* Perform the Overlap Add */
                for( j = 0; j < FFT_length; j++ )
                {
                    OLA_buffer[ k ][ j + OLA_window_size / 2 - OLA_zero_padding ] += signal_r[ j ];
                }

                /* Save the data to the destination if an OLA step is complete in the first OLA step of the buffer */
                if( FFT_applicable_interval.contains( i - OLA_window_size / 2 ) )
                {
                    System.arraycopy( OLA_buffer[ k ], 0, signal_src_ptr, 0, OLA_window_size / 2 );
                }

                /* Shift the OLA accumulator */
                System.arraycopy( OLA_buffer[ k ], OLA_window_size / 2, OLA_buffer[ k ], 0, OLA_window_size / 2 * 3 );
                Arrays.fill( OLA_buffer[ k ], OLA_window_size / 2 * 3, OLA_window_size * 2 , 0.0f );
            }

            win.getInterval().l = i - OLA_window_size / 2;
            win.getInterval().r = i;
            win.markModified();
            if( FFT_applicable_interval.includes( win.getInterval() ) )
            {
                dataDest.put_samples( win );
            }

            i += OLA_window_size / 2;
            progress = 1.0f * ( ( i - FFT_grab_interval.l ) ) / ( FFT_grab_interval.get_length() );
        }

        /*
        *   Ye olde FIR where FFT is not possible
        */
        filter.apply( dataSource, dataDest, new Interval( interval.l, FFT_applicable_interval.l, false ) );
        filter.apply( dataSource, dataDest, new Interval( FFT_applicable_interval.r, interval.r, false ) );

        progress = 1;
    }

    @Override
    public float getProgress()
    {
        return progress;
    }

    @Override
    public EffectType getEffectType()
    {
        return EffectType.NORMAL_AUDIO_EFFECT;
    }

    public void setFilter( FIR filter ) throws DataSourceException
    {
        init_done = false;
        fir_filter = filter;
        if( filter.getFf_coeff_nr() % 2 == 0 )
        {
            throw new DataSourceException( "Filter length must be an odd value", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        setFFT_length( Util_Stuff.next_power_of_two( filter.getFf_coeff_nr() ) * 2 );
    }

    public void setFFT_length( int FFT_length ) throws DataSourceException
    {
        if( !Util_Stuff.is_power_of_two( FFT_length ) )
        {
            throw new DataSourceException( "FFT length must be a power of two", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        if( FFT_length < fir_filter.getFf_coeff_nr() )
        {
            throw new DataSourceException( "FFT length must be longer than the filter tap count", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        if( FFT_length - ( fir_filter.getFf_coeff_nr() - 1 ) < FFT_length / 2 )
        {
            throw new DataSourceException( "OLA window size less than half of FFT length", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        this.FFT_length = FFT_length;
        this.OLA_window_size = FFT_length - ( fir_filter.getFf_coeff_nr() - 1 );
    }

    public int getFFT_length()
    {
        return FFT_length;
    }
}
