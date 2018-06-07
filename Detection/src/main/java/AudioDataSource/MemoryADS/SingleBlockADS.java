package AudioDataSource.MemoryADS;

import AudioDataSource.ADCache.AudioSamplesWindow;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import Utils.Interval;

/**
 * Created by Alex on 27.02.2018.
 */
public class SingleBlockADS implements IAudioDataSource
{
    private int channel_number = 0;
    private Interval interval = new Interval( 0, 0 );
    private int sample_rate = 0;
    private double[][] buffer = null;

    public SingleBlockADS( int sample_rate, int channel_number, int init_size, Interval init_interval )
    {
        this.channel_number = channel_number;
        this.sample_rate = sample_rate;
        interval = new Interval( init_interval.l, init_interval.get_length() );
        buffer = new double[ channel_number ][ init_size ];
    }

    @Override
    public int get_channel_number()
    {
        return channel_number;
    }

    @Override
    public int get_sample_number()
    {
        return interval.r;
    }

    public int get_first_sample_index()
    {
        return interval.l;
    }

    @Override
    public int get_sample_rate()
    {
        return sample_rate;
    }

    @Override
    public AudioSamplesWindow get_samples( int first_sample_index, int length ) throws DataSourceException
    {
        Interval get_interval = new Interval( first_sample_index, length );
        if( interval.includes( get_interval ) )
        {
            double buf[][] = new double[ channel_number ][ get_interval.get_length() ];
            int i, k;
            for( k = 0; k < channel_number; k++ )
            {
                for( i = 0; i < get_interval.get_length(); i++ )
                {
                    buf[ k ][ i ] = buffer[ k ][ get_interval.l - interval.l + i ];
                }
            }
            return new AudioSamplesWindow( buf, get_interval.l, get_interval.get_length(), channel_number );
        }
        else
        {
            throw new DataSourceException( "Samples not stored", DataSourceExceptionCause.SAMPLE_NOT_CACHED );
        }
    }

    @Override
    public void put_samples( AudioSamplesWindow new_samples ) throws DataSourceException
    {
        if( new_samples.get_channel_number() != channel_number )
        {
            throw new DataSourceException( DataSourceExceptionCause.CHANNEL_NOT_VALID );
        }
        if( interval.includes( new_samples.getInterval() ) )
        {
            int i, k;
            for( k = 0; k < channel_number; k++ )
            {
                for( i = 0; i < new_samples.get_length(); i++ )
                {
                    buffer[ k ][ i + new_samples.get_first_sample_index() - interval.l ] = new_samples.getSamples()[ k ][ i ];
                }
            }
        }
        else
        {
            buffer = new_samples.getSamples();
            interval.l = new_samples.get_first_sample_index();
            interval.r = new_samples.get_after_last_sample_index();
        }
    }

    @Override
    public void close() throws DataSourceException
    {

    }

    public double[][] getBuffer()
    {
        return buffer;
    }

    public Interval getInterval()
    {
        return new Interval( interval.l, interval.get_length() );
    }

    public void setInterval( Interval newInterval ) throws DataSourceException
    {
        if( newInterval.get_length() > buffer[ 0 ].length )
        {
            throw new DataSourceException( "Interval length must smaller than the buffer length", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        else
        {
            interval.l = newInterval.l;
            interval.r = newInterval.r;
        }
    }
}
