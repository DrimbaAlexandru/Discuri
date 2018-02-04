package SignalProcessing.Effects;

import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.FIR.FIR;
import Utils.Interval;

/**
 * Created by Alex on 11.01.2018.
 */
public class FIR_Filter implements IEffect
{
    private FIR filter = null;
    private final static int max_buffer_size = 16;
    private static double[] buffer = new double[ max_buffer_size ];

    @Override
    public String getName()
    {
        return "FIR Filter";
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        if( filter == null )
        {
            throw new DataSourceException( "No filter was set.", DataSourceExceptionCause.INVALID_STATE );
        }
        int filter_length = filter.getTap_nr();
        int temp_len;
        int i, j, k;
        int first_needed_sample_index;
        int first_fetchable_sample_index;
        int data_start;
        int N;
        AudioSamplesWindow win;

        interval.r = Math.min( dataSource.get_sample_number(), interval.r );

        for( i = interval.r; i > interval.l; )
        {
            first_needed_sample_index = Math.max( i - max_buffer_size + filter_length - 1, interval.l - filter_length + 1 );
            N = i - first_needed_sample_index;
            first_fetchable_sample_index = Math.max( 0, first_needed_sample_index );
            temp_len = i - first_fetchable_sample_index;
            win = dataSource.get_samples( first_fetchable_sample_index, temp_len );
            temp_len = win.get_length();
            data_start = Math.max( first_fetchable_sample_index - first_needed_sample_index, 0 );
            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                for( j = 0; j < data_start; j++ )
                {
                    buffer[ j ] = 0;
                }
                for( j = 0; j < temp_len; j++ )
                {
                    buffer[ j + data_start ] = win.getSample( first_fetchable_sample_index + j, k );
                }
                for( j = N; j < N + filter_length - 1; j++ )
                {
                    buffer[ j ] = 0;
                }
                filter.apply_FIR( buffer, N - filter_length + 1 );
                for( j = filter_length - 1; j < N; j++ )
                {
                    win.putSample( j + win.get_first_sample_index() - data_start, k, buffer[ j ] );
                }
            }
            dataDest.put_samples( win );
            i -= N - filter_length + 1;
        }
    }

    public void setFilter( FIR filter )
    {
        this.filter = filter;
    }
}
