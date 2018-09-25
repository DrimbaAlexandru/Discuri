package AudioDataSource.MemoryADS;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import Utils.Interval;

/**
 * Created by Alex on 15.06.2018.
 */
public class InMemoryADS implements IAudioDataSource
{
    private float[][] buffer = null;
    private int ch_number;
    private int capacity;
    private Interval buffered_interval = new Interval( 0, 0 );
    private int sample_rate;

    public InMemoryADS( int capacity, int channel_number, int sample_rate )
    {
        this.capacity = capacity;
        this.ch_number = channel_number;
        this.sample_rate = sample_rate;
        this.buffer = new float[ channel_number ][ capacity ];
    }

    @Override
    public int get_channel_number()
    {
        return ch_number;
    }

    @Override
    public int get_sample_number()
    {
        return buffered_interval.r;
    }

    public Interval get_buffer_interval()
    {
        return new Interval( buffered_interval.l, buffered_interval.r, false );
    }

    @Override
    public int get_sample_rate()
    {
        return sample_rate;
    }

    @Override
    public AudioSamplesWindow get_samples( int first_sample_index, int length ) throws DataSourceException
    {
        Interval requested = new Interval( first_sample_index, length );
        if( !buffered_interval.includes( requested ) )
        {
            throw new DataSourceException( "Samples not stored in the ADS", DataSourceExceptionCause.SAMPLE_NOT_CACHED );
        }
        float[][] samples = new float[ ch_number ][ length ];
        int k, i;
        for( k = 0; k < ch_number; k++ )
        {
            for( i = 0; i < length; i++ )
            {
                samples[ k ][ i ] = buffer[ k ][ i + first_sample_index - buffered_interval.l ];
            }
        }
        return new AudioSamplesWindow( samples, first_sample_index, length, ch_number );
    }

    @Override
    public void put_samples( AudioSamplesWindow new_samples ) throws DataSourceException
    {
        /*if( new_samples.get_first_sample_index() > buffered_interval.r )
        {
            throw new DataSourceException( "FRAIERE!!!" );
        }*/
        if( new_samples.get_length() > capacity )
        {
            throw new DataSourceException( "Write operation larger than ADS capacity", DataSourceExceptionCause.NOT_ENOUGH_SPACE );
        }
        if( !new Interval( buffered_interval.l, capacity ).includes( new_samples.getInterval() ) )
        {
            throw new DataSourceException( "Write interval can not contained in ADS", DataSourceExceptionCause.SAMPLE_NOT_CACHED );
        }
        int i, k;
        Interval copyable_interval = new Interval( new_samples.get_first_sample_index(), new_samples.get_length() );
        copyable_interval.limit( buffered_interval.l, buffered_interval.l + capacity );
        for( k = 0; k < ch_number; k++ )
        {
            for( i = copyable_interval.l; i < copyable_interval.r; i++ )
            {
                buffer[ k ][ i - buffered_interval.l ] = new_samples.getSample( i, k );
            }
        }
        buffered_interval.r = Math.max( buffered_interval.r, new_samples.get_after_last_sample_index() );
    }

    public void shift_interval( int amount )
    {
        buffered_interval.l += amount;
        if( Math.abs( amount ) > buffered_interval.get_length() )
        {
            buffered_interval.r = buffered_interval.l;
            return;
        }
        int k, i;
        if( amount < 0 )
        {
            amount = -amount;
            for( k = 0; k < ch_number; k++ )
            {
                for( i = capacity - 1; i >= amount; i-- )
                {
                    buffer[ k ][ i ] = buffer[ k ][ i - amount ];
                }
            }
            return;
        }
        if( amount > 0 )
        {
            for( k = 0; k < ch_number; k++ )
            {
                for( i = 0; i < capacity - amount; i++ )
                {
                    buffer[ k ][ i ] = buffer[ k ][ i + amount ];
                }
            }
        }
    }

    public int getCapacity()
    {
        return capacity;
    }

    @Override
    public void close() throws DataSourceException
    {
    }
}
