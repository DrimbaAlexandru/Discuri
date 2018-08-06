package SignalProcessing.Effects;

import AudioDataSource.AudioSamplesWindow;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import Utils.Interval;

/**
 * Created by Alex on 08.02.2018.
 */
public class Left_Shift implements IEffect
{
    private int max_chunk_size = 1024;
    private int amount = 0;

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        if( amount < 0 )
        {
            throw new DataSourceException( "Amount cannot be negative", DataSourceExceptionCause.INVALID_STATE );
        }
        if( interval.l < amount )
        {
            interval.l = amount;
        }
        if( interval.r >= dataSource.get_sample_number() )
        {
            interval.r = dataSource.get_sample_number();
        }

        int i = interval.l - amount;
        int temp_len, j, k;
        AudioSamplesWindow win;

        while( i < interval.r - amount )
        {
            temp_len = Math.min( interval.r - i, max_chunk_size );
            win = dataSource.get_samples( i, temp_len );
            temp_len = win.get_length();
            if( temp_len - amount <= 0 )
            {
                throw new DataSourceException( "Chunk size smaller than amount to shift. May have caused an infinite loop", DataSourceExceptionCause.GENERIC_ERROR );
            }
            for( k = 0; k < win.get_channel_number(); k++ )
            {
                for( j = 0; j < temp_len - amount; j++ )
                {
                    win.getSamples()[ k ][ j ] = win.getSamples()[ k ][ j + amount ];
                }
            }
            dataDest.put_samples( win );
            i += temp_len - amount;
        }
    }

    @Override
    public float getProgress()
    {
        return 0;
    }

    public void setMax_chunk_size( int max_chunk_size )
    {
        this.max_chunk_size = max_chunk_size;
    }

    public void setAmount( int amount )
    {
        this.amount = amount;
    }
}
