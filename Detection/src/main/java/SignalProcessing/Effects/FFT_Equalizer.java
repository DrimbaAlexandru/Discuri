package SignalProcessing.Effects;

import AudioDataSource.AudioSamplesWindow;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.Filters.Equalizer_FIR;
import SignalProcessing.Filters.FIR;
import SignalProcessing.FourierTransforms.Fourier;
import Utils.Complex;
import Utils.Interval;

/**
 * Created by Alex on 08.02.2018.
 */
public class FFT_Equalizer implements IEffect
{
    private FIR fir_filter = null;
    private int max_chunk_size = 32768;
    private float progress = 0;

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        if( fir_filter == null )
        {
            throw new DataSourceException( "No filter was set.", DataSourceExceptionCause.INVALID_STATE );
        }

        final int buf_len = fir_filter.getFf_coeff_nr() / 2;

        if( fir_filter.getFf_coeff_nr() > max_chunk_size )
        {
            throw new DataSourceException( "Chunk size must be at least the size of the filter", DataSourceExceptionCause.INVALID_STATE );
        }

        int temp_len;
        int i, k, j;
        int FFT_length = Utils.Util_Stuff.next_power_of_two( fir_filter.getFf_coeff_nr() );
        Complex[] EQ_FD;
        Complex[] EQ_TD = new Complex[ FFT_length ];
        Complex[] signal_TD = new Complex[ FFT_length ];
        Complex[] signal_FD;
        float[] eq_ft_ampl = new float[ FFT_length ];

        int start = FFT_length - fir_filter.getFf_coeff_nr() / 2;
        for( j = 0; j < fir_filter.getFf_coeff_nr(); j++ )
        {
            EQ_TD[ ( j + start ) % FFT_length ] = new Complex( fir_filter.getFf()[ j ], 0 );
        }
        for( j = fir_filter.getFf_coeff_nr(); j < FFT_length; j++ )
        {
            EQ_TD[ ( j + start ) % FFT_length ] = new Complex();
        }
        EQ_FD = Fourier.FFT( EQ_TD, FFT_length );

        for( j = 0; j < FFT_length; j++ )
        {
            signal_TD[ j ] = new Complex();
            eq_ft_ampl[ j ] = EQ_FD[ j ].Ampl() * FFT_length;
        }

        AudioSamplesWindow win;

        interval.limit( 0, dataSource.get_sample_number() );

        i = interval.l;
        progress = 0;

        for( ; i < interval.r; )
        {
            temp_len = Math.min( FFT_length, interval.r - i );
            win = dataSource.get_samples( i, temp_len );
            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                for( j = 0; j < temp_len; j++ )
                {
                    signal_TD[ j ].set( win.getSamples()[ k ][ j ], 0 );
                }
                for( j = temp_len; j < FFT_length; j++ )
                {
                    signal_TD[ j ].set( 0, 0 );
                }
                signal_FD = Fourier.FFT( signal_TD, FFT_length );
                for( j = 0; j < FFT_length; j++ )
                {
                    signal_FD[ j ].mul( eq_ft_ampl[ j ] );
                }
                signal_TD = Fourier.IFFT( signal_FD, FFT_length );
                for( j = 0; j < temp_len; j++ )
                {
                    win.getSamples()[ k ][ j ] = signal_TD[ j ].r();
                }
            }
            win.markModified();
            dataDest.put_samples( win );
            i += temp_len;
            progress = 1.0f * ( i - interval.l ) / interval.get_length();
        }
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

    public void setMax_chunk_size( int max_chunk_size )
    {
        this.max_chunk_size = max_chunk_size;
    }
}
