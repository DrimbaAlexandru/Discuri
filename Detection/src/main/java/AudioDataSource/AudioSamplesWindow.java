package AudioDataSource;

import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import SignalProcessing.Windowing.Windowing;
import Utils.DataTypes.Interval;

import java.util.function.Function;

import static Utils.Exceptions.DataSourceExceptionCause.CHANNEL_NOT_VALID;
import static Utils.Exceptions.DataSourceExceptionCause.SAMPLE_NOT_CACHED;

/**
 * Created by Alex on 10.09.2017.
 */
public class AudioSamplesWindow
{
    private float samples[][]; /* Access: samples[channel][index] */
    private Interval interval;
    private int channel_number;
    private boolean modified;

    public AudioSamplesWindow( float _samples[][], int first_sample_index, int length, int _channel_number )
    {
        samples = _samples;
        interval = new Interval( first_sample_index, length );
        channel_number = _channel_number;
        modified = false;
    }

    public boolean containsSample( int sample_index )
    {
        return interval.contains( sample_index );
    }

    public boolean fitsSample( int sample_index )
    {
        return new Interval( interval.l, get_capacity() ).contains( sample_index );
    }

    public float getSample( int sample_index, int channel ) throws DataSourceException
    {
        try
        {
            return samples[ channel ][ ( sample_index - interval.l ) ];
        }
        catch( ArrayIndexOutOfBoundsException e )
        {
            if( !containsSample( sample_index ) )
            {
                throw new DataSourceException( "Requested frame (" + sample_index + ") is not cached", SAMPLE_NOT_CACHED );
            }

            if( ( channel < 0 ) || ( channel >= channel_number ) )
            {
                throw new DataSourceException( "Requested channel is not valid", CHANNEL_NOT_VALID );
            }
        }
        return 0;
    }

    public void putSample( int sample_index, int channel, float newValue ) throws DataSourceException
    {
        try
        {
            samples[ channel ][ ( sample_index - interval.l ) ] = newValue;
            modified = true;
        }
        catch( ArrayIndexOutOfBoundsException e )
        {
            if( !containsSample( sample_index ) )
            {
                throw new DataSourceException( "Requested sample (" + sample_index + ") is not cached", SAMPLE_NOT_CACHED );
            }

            if( ( channel < 0 ) || ( channel >= channel_number ) )
            {
                throw new DataSourceException( "Requested channel is not valid", CHANNEL_NOT_VALID );
            }
        }
    }

    public float[][] getSamples()
    {
        return samples;
    }

    public int get_first_sample_index() {
        return interval.l;
    }

    public int get_after_last_sample_index()
    {
        return interval.r;
    }

    public int get_length() {
        return interval.get_length();
    }

    public int get_channel_number() {
        return channel_number;
    }

    public boolean isModified() {
        return modified;
    }

    public void markModified()
    {
        this.modified = true;
    }

    public void markAsFlushed()
    {
        modified = false;
    }

    public Interval getInterval()
    {
        return interval;
    }

    public void applyWindow( Function< Float, Float > function )
    {
        for( int ch = 0; ch < get_channel_number(); ch++ )
        {
            Windowing.apply( samples[ ch ], get_length(), function );
        }
    }

    public int get_capacity()
    {
        return samples[ 0 ].length;
    }

    public void set_length( int new_length ) throws DataSourceException
    {
        if( new_length > get_capacity() )
        {
            throw new DataSourceException( "New length exceedes window capacity", DataSourceExceptionCause.NOT_ENOUGH_FREE_SPACE );
        }
        else
        {
            interval.r = interval.l + new_length;
        }
    }

}
