package SignalProcessing.Effects;

import AudioDataSource.AudioSamplesWindow;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.Filters.FIR;
import SignalProcessing.FourierTransforms.Fourier;
import SignalProcessing.Windowing.Windowing;
import Utils.Interval;
import net.jafama.FastMath;

import java.util.Arrays;
import java.util.function.Function;

/**
 * Created by Alex on 08.02.2018.
 */
public class FFT_Equalizer implements IEffect
{
    private FIR fir_filter = null;
    private float progress = 0;
    final private Function< Float, Float > window_function = Windowing.Hann_window;
    private int OLA_window_size = 0;/* Length of the OLA window size. OLA step is this / 2  */
    private int FFT_length = 0;     /* Length of each FFT                                   */


    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        /*
        *  Local variables
        */
        int i, k, j;
        float EQ_i[];               /* imaginary component of the frequency-domain filter   */
        float EQ_r[];               /* real component of the frequency-domain filter        */
        float signal_r[];           /* real component of the processed signal               */
        float signal_i[];           /* imaginary component of the processed signal          */
        float OLA_window[];         /* Overlap-Add window coefficients                      */
        float zero_pad_window[];    /* Zero padding region window coefficients              */
        float OLA_buffer[][];       /* Overlap-Add accumulator buffer                       */
        float signal_src_ptr[];     /* Pointer to the ASW signal array                      */

        int FIR_offset;             /* Offset of the first FIR coeff in the EQ sample buf   */
        int OLA_zero_padding;       /* Offset of the OLA window in the FFT window           */

        AudioSamplesWindow win;
        FIR_Equalizer filter = new FIR_Equalizer();
        Interval FFT_grab_interval;         /* Interval on which data needs to be grabbed for FFTs  */
        Interval FFT_applicable_interval;   /* Interval on which FFT Equalization can actually be performed */
        int grab_extend;

        /*
        *  Variable initialization
        */
        FFT_length = Math.max( Utils.Util_Stuff.next_power_of_two( fir_filter.getFf_coeff_nr() ), FFT_length );
        OLA_zero_padding = ( FFT_length - OLA_window_size ) / 2;
        if( OLA_zero_padding > 0 )
        {
            OLA_zero_padding = ( ( OLA_zero_padding - 1 ) / ( OLA_window_size / 2 ) + 1 ) * ( OLA_window_size / 2 ); /* Round the zero padding up to a multiple of OLA_window_size/2 */
        }
        FIR_offset = FFT_length - ( fir_filter.getFf_coeff_nr() - 1 ) / 2;

        grab_extend = FFT_length - OLA_window_size / 2 - OLA_zero_padding;

        if( fir_filter == null || OLA_window_size < 16 || FFT_length < 16 || FFT_length < OLA_window_size || !Utils.Util_Stuff.is_power_of_two( FFT_length ) || OLA_window_size % 2 != 0 )
        {
            throw new DataSourceException( "Parameters not correctly set.", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( fir_filter.getFf_coeff_nr() % 2 != 1 )
        {
            throw new DataSourceException( "Filter has even number of coefficients. An odd number is needed", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        EQ_r = new float[ FFT_length ];
        EQ_i = new float[ FFT_length ];
        signal_r = new float[ FFT_length ];
        signal_i = EQ_i; /* These two arrays are never used at the same time. Using two variables to keep a clear separation of the intended use */
        OLA_window = new float[ OLA_window_size ];
        zero_pad_window = ( OLA_zero_padding > 0 ) ? new float[ OLA_zero_padding ] : null;
        OLA_buffer = new float[ dataSource.get_channel_number() ][ FFT_length ];

        interval.limit( 0, dataSource.get_sample_number() );

        FFT_grab_interval = new Interval( interval.l - grab_extend, interval.r + OLA_zero_padding, false );
        FFT_grab_interval.limit( 0, dataSource.get_sample_number() - OLA_window_size );
        FFT_grab_interval.r = FFT_grab_interval.l + FFT_grab_interval.get_length() / ( OLA_window_size / 2 ) * ( OLA_window_size / 2 );   /* make the length a multiple of ( OLA_window_size / 2 ) */

        FFT_applicable_interval = new Interval( 0, 0 );
        FFT_applicable_interval.l = Math.max( interval.l, FFT_grab_interval.l + grab_extend );
        FFT_applicable_interval.r = Math.min( interval.r, FFT_grab_interval.r - OLA_zero_padding );

        progress = 0;

        /*
        * Convert from FIR filter coefficients to a frequency response using FFT and zero padding.
        * Place the FIR coefficients with the center one at the beginning of the EQ samples
        */
        // Arrays.fill( EQ_i, 0.0f );
        // Arrays.fill( EQ_r, 0.0f );
        for( j = 0; j < fir_filter.getFf_coeff_nr(); j++ )
        {
            EQ_r[ ( FIR_offset + j ) % FFT_length ] = fir_filter.getFf()[ j ];
        }
        Fourier.FFT_inplace( EQ_r, EQ_i, FFT_length );

        /*
        * Only store the intensity of each frequency, as we are not interested in its phase (which ideally should be 0 )
        */
        for( j = 0; j < FFT_length; j++ )
        {
            EQ_r[ j ] = ( float )FastMath.sqrt( EQ_r[ j ] * EQ_r[ j ] + EQ_i[ j ] * EQ_i[ j ] ) * FFT_length;
        }

        Arrays.fill( OLA_window, 1 );
        Windowing.apply( OLA_window, OLA_window_size, window_function );

        if( OLA_zero_padding > 0 )
        {
            Arrays.fill( zero_pad_window, 1 );
            Windowing.apply( zero_pad_window, OLA_zero_padding, Windowing.half_Hann_window );
        }

        /*
        *   SFFT OLA where possible
        */
        for( i = FFT_grab_interval.l; i < FFT_grab_interval.r; )
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

                /* Window the zero-padded sections */
                for( j = 0; j < OLA_zero_padding; j++ )
                {
                    signal_r[ j ] *= zero_pad_window[ j ];
                    signal_r[ FFT_length - 1 - j ] *= zero_pad_window[ j ];
                }

                /* Perform the Overlap Add */
                for( j = 0; j < FFT_length; j++ )
                {
                    OLA_buffer[ k ][ j ] += signal_r[ j ];
                }

                /* Save the data to the destination if an OLA is complete in the first part of the buffer */
                if( FFT_applicable_interval.contains( i - OLA_zero_padding ) )
                {
                    System.arraycopy( OLA_buffer[ k ], 0, signal_src_ptr, 0, OLA_window_size / 2 );
                }

                /* Shift the OLA accumulator */
                System.arraycopy( OLA_buffer[ k ], OLA_window_size / 2, OLA_buffer[ k ], 0, FFT_length - OLA_window_size / 2 );
                Arrays.fill( OLA_buffer[ k ], FFT_length - OLA_window_size / 2, FFT_length, 0.0f );
            }

            win.getInterval().l = i - OLA_zero_padding;
            win.getInterval().r = i - OLA_zero_padding + OLA_window_size / 2;
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
        filter.setFilter( fir_filter );
        filter.setMax_chunk_size( FFT_length );
        filter.apply( dataSource, dataDest, new Interval( interval.l, FFT_applicable_interval.l, false ) );
        filter.apply( dataSource, dataDest, new Interval( FFT_applicable_interval.r, interval.r, false ) );

        progress = 1;
    }

    @Override
    public float getProgress()
    {
        return progress;
    }

    public void setFilter( FIR filter )
    {
        fir_filter = filter;
    }

    public void set_OLA_window_size( int OLA_step_size )
    {
        this.OLA_window_size = OLA_step_size;
    }

    public void setFFT_length( int FFT_length )
    {
        this.FFT_length = FFT_length;
    }
}
