package SignalProcessing.Effects;

import AudioDataSource.AudioSamplesWindow;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.Filters.FIR;
import Utils.Interval;

/**
 * Created by Alex on 11.01.2018.
 */
public class FIR_Filter implements IEffect
{
    private FIR filter = null;
    private int max_chunk_size = 32768;
    private double progress = 0;

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        if( filter == null )
        {
            throw new DataSourceException( "No filter was set.", DataSourceExceptionCause.INVALID_STATE );
        }
        if( filter.getFf_coeff_nr() >= max_chunk_size )
        {
            throw new DataSourceException( "Buffer is smaller or equal to filter length", DataSourceExceptionCause.INVALID_STATE );
        }
        int filter_length = filter.getFf_coeff_nr();
        int temp_len;
        int i, k;
        int first_needed_sample_index;
        int first_fetchable_sample_index;
        AudioSamplesWindow win;
        Interval applying_range = new Interval( 0, 0 );

        interval.r = Math.min( dataSource.get_sample_number(), interval.r );
        interval.l = Math.max( 0, interval.l );
        progress = 0;

        for( i = interval.r; i > interval.l; )
        {
            first_needed_sample_index = Math.max( i - max_chunk_size, interval.l - filter_length + 1 );
            first_fetchable_sample_index = Math.max( 0, first_needed_sample_index );
            temp_len = i - first_fetchable_sample_index;
            win = dataSource.get_samples( first_fetchable_sample_index, temp_len );
            temp_len = win.get_length();
            applying_range.l = filter_length - 1 - ( first_fetchable_sample_index - first_needed_sample_index );
            applying_range.r = temp_len;
            if( applying_range.get_length() == 0 )
            {
                throw new DataSourceException( "Avoided infinite loop!", DataSourceExceptionCause.INVALID_STATE );
            }

            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                filter.apply( win.getSamples()[ k ], applying_range );
                win.markModified();

            }
            dataDest.put_samples( win );
            i -= applying_range.get_length();
            progress = 1.0 * ( interval.r - i ) / interval.get_length();
        }
    }

    @Override
    public double getProgress()
    {
        return progress;
    }

    public void setFilter( FIR filter )
    {
        this.filter = filter;
    }

    public void setMax_chunk_size( int max_chunk_size )
    {
        this.max_chunk_size = max_chunk_size;
    }
}
