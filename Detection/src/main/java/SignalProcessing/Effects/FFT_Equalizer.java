package SignalProcessing.Effects;

import AudioDataSource.AudioSamplesWindow;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.Filters.Equalizer_FIR;
import SignalProcessing.Filters.FIR;
import SignalProcessing.FourierTransforms.Fourier;
import SignalProcessing.Windowing.Windowing;
import Utils.Complex;
import Utils.Interval;

import java.util.function.Function;

/**
 * Created by Alex on 08.02.2018.
 */
public class FFT_Equalizer implements IEffect
{
    private FIR fir_filter = null;
    private float progress = 0;
    private Function< Float, Float > window_function = Windowing.Hann_window;
    private int FFT_length = 1;

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        if( fir_filter == null )
        {
            throw new DataSourceException( "No filter was set.", DataSourceExceptionCause.INVALID_STATE );
        }

        /*
        *  Local variables
        */
        Interval FFT_possible_interval;
        int i, k, j;
        FFT_length = Math.max( Utils.Util_Stuff.next_power_of_two( fir_filter.getFf_coeff_nr() ), FFT_length );
        Complex[] EQ = new Complex[ FFT_length ];
        Complex[] signal = new Complex[ FFT_length ];
        float half_buf[][] = new float[ dataDest.get_channel_number() ][ FFT_length ];
        AudioSamplesWindow win;
        int start = FFT_length - fir_filter.getFf_coeff_nr() / 2;
        FIR_Equalizer filter = new FIR_Equalizer();

        /*
        *  Variable initialization
        */
        interval.limit( 0, dataSource.get_sample_number() );
        FFT_possible_interval = new Interval( interval.l - FFT_length / 2, interval.r + FFT_length - 1, false );
        FFT_possible_interval.limit( 0, dataSource.get_sample_number() );
        FFT_possible_interval.r = FFT_possible_interval.l + FFT_possible_interval.get_length() / ( FFT_length / 2 ) * ( FFT_length / 2 );
        progress = 0;

        for( j = 0; j < fir_filter.getFf_coeff_nr(); j++ )
        {
            EQ[ ( j + start ) % FFT_length ] = new Complex( fir_filter.getFf()[ j ], 0 );
        }
        for( j = fir_filter.getFf_coeff_nr(); j < FFT_length; j++ )
        {
            EQ[ ( j + start ) % FFT_length ] = new Complex();
        }
        Fourier.FFT_inplace( EQ, FFT_length );

        for( j = 0; j < FFT_length; j++ )
        {
            signal[ j ] = new Complex();
            EQ[ j ].r = EQ[ j ].Ampl() * FFT_length;
            EQ[ j ].i = 0;
        }

        /*
        *   SFFT OLA where possible
        */
        for( i = FFT_possible_interval.l; i <= FFT_possible_interval.r - FFT_length; )
        {
            win = dataSource.get_samples( i, FFT_length );
            if( win.get_length() < FFT_length )
            {
                continue;
            }

            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                Windowing.apply( win.getSamples()[ k ], FFT_length, window_function );

                for( j = 0; j < FFT_length; j++ )
                {
                    signal[ j ].r = win.getSamples()[ k ][ j ];
                    signal[ j ].i = 0;
                }

                Fourier.FFT_inplace( signal, FFT_length );
                for( j = 0; j < FFT_length; j++ )
                {
                    signal[ j ].mul( EQ[ j ].r );
                }
                Fourier.IFFT_inplace( signal, FFT_length );

                for( j = 0; j < FFT_length / 2; j++ )
                {
                    win.getSamples()[ k ][ j ] = half_buf[ k ][ j ] + signal[ j ].r;
                    half_buf[ k ][ j ] = signal[ j + FFT_length / 2 ].r;
                }
            }

            win.getInterval().l = i;
            win.getInterval().r = i + Math.min( FFT_length / 2, interval.r - i );
            win.markModified();
            if( interval.includes( win.getInterval() ) )
            {
                dataDest.put_samples( win );
            }

            progress = 1.0f * ( ( i - FFT_possible_interval.l ) ) / ( FFT_possible_interval.get_length() );
            i += FFT_length / 2;
        }

        /*
        *   Ye old FIR where FFT is not possible
        */
        filter.setFilter( fir_filter );
        filter.apply( dataSource, dataDest, new Interval( interval.l, FFT_possible_interval.l + FFT_length / 2, false ) );
        filter.apply( dataSource, dataDest, new Interval( FFT_possible_interval.r - FFT_length / 2, interval.r, false ) );

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

    public void setFFT_length( int FFT_length )
    {
        this.FFT_length = FFT_length;
    }
}
