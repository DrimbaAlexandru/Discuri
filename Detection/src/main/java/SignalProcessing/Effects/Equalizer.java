package SignalProcessing.Effects;

import AudioDataSource.AudioSamplesWindow;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.Filters.Equalizer_FIR;
import SignalProcessing.Filters.FIR;
import Utils.Interval;

/**
 * Created by Alex on 08.02.2018.
 */
public class Equalizer implements IEffect
{
    private FIR fir_filter = null;
    private int max_chunk_size = 32768;
    private final static float[] identity_FIR_coeffs = { 1 };
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

        final boolean isInPlace = ( dataDest == dataSource );
        float[][] prev_left_samples = null;
        float[][] flushing_buffer = new float[ dataSource.get_channel_number() ][ max_chunk_size ];
        final Equalizer_FIR equalizer_fir = new Equalizer_FIR( fir_filter );
        if( isInPlace )
        {
            prev_left_samples = new float[ dataSource.get_channel_number() ][ buf_len ];
        }

        int temp_len;
        int i, j, k;
        int first_needed_sample_index;
        int first_fetchable_sample_index;

        AudioSamplesWindow win;
        Interval applying_range = new Interval( 0, 0 );

        interval.r = Math.min( dataSource.get_sample_number(), interval.r );
        interval.l = Math.max( 0, interval.l );

        i = interval.l;
        progress = 0;

        first_needed_sample_index = i - buf_len;
        first_fetchable_sample_index = Math.max( 0, first_needed_sample_index );
        temp_len = Math.min( interval.r - first_fetchable_sample_index + fir_filter.getFf_coeff_nr() / 2, max_chunk_size );
        win = dataSource.get_samples( first_fetchable_sample_index, temp_len );
        temp_len = win.get_length();
        applying_range.l = i - first_fetchable_sample_index;
        if( i >= dataSource.get_sample_number() - 1 - fir_filter.getFf_coeff_nr() / 2 )
        {
            applying_range.r = applying_range.l + ( interval.r - i );
        }
        else
        {
            applying_range.r = temp_len - fir_filter.getFf_coeff_nr() / 2;
        }
        if( applying_range.get_length() <= 0 )
        {
            throw new DataSourceException( "Avoided infinite loop!", DataSourceExceptionCause.INVALID_STATE );
        }

        for( k = 0; k < dataSource.get_channel_number(); k++ )
        {
            if( isInPlace && i + applying_range.get_length() < interval.r )
            {
                for( j = 0; j < buf_len; j++ )
                {
                    prev_left_samples[ k ][ buf_len - 1 - j ] = win.getSamples()[ k ][ applying_range.r - 1 - j ];
                }
            }
            equalizer_fir.apply( win.getSamples()[ k ], applying_range );
            win.markModified();
        }
        for( k = 0; k < dataSource.get_channel_number(); k++ )
        {
            for( j = 0; j < applying_range.get_length(); j++ )
            {
                flushing_buffer[ k ][ j ] = win.getSamples()[ k ][ applying_range.l + j ];
            }
        }
        dataDest.put_samples( new AudioSamplesWindow( flushing_buffer, applying_range.l + win.get_first_sample_index(), applying_range.get_length(), win.get_channel_number() ) );

        i += applying_range.get_length();
        progress = 1.0f * ( i - interval.l ) / interval.get_length();

        for( ; i < interval.r; )
        {
            first_needed_sample_index = i - buf_len;
            first_fetchable_sample_index = Math.max( 0, first_needed_sample_index );
            temp_len = Math.min( interval.r - first_fetchable_sample_index + fir_filter.getFf_coeff_nr() / 2, max_chunk_size );
            win = dataSource.get_samples( first_fetchable_sample_index, temp_len );
            temp_len = win.get_length();
            applying_range.l = i - first_fetchable_sample_index;
            if( i >= dataSource.get_sample_number() - 1 - fir_filter.getFf_coeff_nr() / 2 )
            {
                applying_range.r = applying_range.l + ( interval.r - i );
            }
            else
            {
                applying_range.r = temp_len - fir_filter.getFf_coeff_nr() / 2;
            }

            if( applying_range.get_length() <= 0 )
            {
                throw new DataSourceException( "Avoided infinite loop!", DataSourceExceptionCause.INVALID_STATE );
            }

            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                if( isInPlace )
                {
                    for( j = 0; j < buf_len; j++ )
                    {
                        win.putSample( first_fetchable_sample_index + buf_len - 1 - j, k, prev_left_samples[ k ][ buf_len - 1 - j ] );
                        prev_left_samples[ k ][ buf_len - 1 - j ] = win.getSamples()[ k ][ applying_range.r - 1 - j ];
                    }
                }

                equalizer_fir.apply( win.getSamples()[ k ], applying_range );

                win.markModified();

            }
            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                for( j = 0; j < applying_range.get_length(); j++ )
                {
                    flushing_buffer[ k ][ j ] = win.getSamples()[ k ][ applying_range.l + j ];
                }
            }
            dataDest.put_samples( new AudioSamplesWindow( flushing_buffer, applying_range.l + win.get_first_sample_index(), applying_range.get_length(), win.get_channel_number() ) );

            i += applying_range.get_length();
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
