package AudioDataSource.MemoryADS;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import Utils.Interval;

import java.util.Arrays;

/**
 * Created by Alex on 15.06.2018.
 */
public class SingleWindowMemoryADS implements IAudioDataSource
{
    private float[][] buffer = null;
    private int ch_number;
    private int capacity;
    private Interval buffered_interval = new Interval( 0, 0 );
    private int sample_rate;

    public SingleWindowMemoryADS( int capacity, int channel_number, int sample_rate )
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
        Interval asw_interval = new_samples.getInterval();

        if( new_samples.get_length() > capacity )
        {
            throw new DataSourceException( "Write operation larger than ADS capacity", DataSourceExceptionCause.NOT_ENOUGH_SPACE );
        }
        if( !new Interval( buffered_interval.l, capacity ).includes( asw_interval ) )
        {
            throw new DataSourceException( "Write interval can not contained within ADS capacity", DataSourceExceptionCause.NOT_ENOUGH_SPACE );
        }

        int i, k;
        float[] src;
        for( k = 0; k < ch_number; k++ )
        {
            src = new_samples.getSamples()[ k ];
            for( i = 0; i < asw_interval.get_length(); i++ )
            {
                buffer[ k ][ i + asw_interval.l - buffered_interval.l ] = src[ i ];
            }
        }
        buffered_interval.r = Math.max( buffered_interval.r, asw_interval.r );
    }

    public void shift_interval( int amount ) throws DataSourceException
    {
        if( Math.abs( amount ) > buffered_interval.get_length() )
        {
            buffered_interval.l += amount;
            buffered_interval.r = buffered_interval.l;
            return;
        }

        int k, i;
        buffered_interval.l += amount;
        if( amount < 0 )
        {
            throw new DataSourceException( "Shifting to left not yet supported", DataSourceExceptionCause.INVALID_PARAMETER );
            /*
            amount = -amount;
            for( k = 0; k < ch_number; k++ )
            {
                for( i = capacity - 1; i >= amount; i-- )
                {
                    buffer[ k ][ i ] = buffer[ k ][ i - amount ];

                }
                for( i = 0; i < capacity; i++ )
                {
                    buffer[ k ][ i ] = 0.0f;
                }
            }
            return;
            */
        }
        if( amount > 0 )
        {
            for( k = 0; k < ch_number; k++ )
            {
                for( i = 0; i < buffered_interval.get_length() - amount; i++ )
                {
                    buffer[ k ][ i ] = buffer[ k ][ i + amount ];
                }
            }
        }
    }

    public void reset_interval( Interval new_interval ) throws DataSourceException
    {
        if( new_interval.get_length() > capacity )
        {
            throw new DataSourceException( String.format( "New interval %d is longer than the ADS capacity %d", new_interval.get_length(), capacity ), DataSourceExceptionCause.INVALID_PARAMETER );
        }

        buffered_interval.l = new_interval.l;
        buffered_interval.r = new_interval.r;
        for( int k = 0; k < ch_number; k++ )
        {
            Arrays.fill( buffer[ k ], 0.0f );
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
