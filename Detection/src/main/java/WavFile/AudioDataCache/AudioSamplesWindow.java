package WavFile.AudioDataCache;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;

import static AudioDataSource.Exceptions.DataSourceExceptionCause.CHANNEL_NOT_VALID;
import static AudioDataSource.Exceptions.DataSourceExceptionCause.SAMPLE_NOT_CACHED;

/**
 * Created by Alex on 10.09.2017.
 */
public class AudioSamplesWindow
{
    private double samples[][]; /* Access: samples[channel][index] */
    private int sample_number;
    private int channel_number;
    private int first_sample_index;
    private boolean modified;

    public AudioSamplesWindow( double _samples[][], int _first_sample_index, int _sample_number, int _channel_number )
    {
        samples = _samples;
        first_sample_index = _first_sample_index;
        sample_number = _sample_number;
        channel_number = _channel_number;
        modified = false;
    }

    public AudioSamplesWindow( double _samples[][], int _first_sample_index, int _sample_number, int _channel_number, boolean make_copy  )
    {
        samples = _samples;
        first_sample_index = _first_sample_index;
        sample_number = _sample_number;
        channel_number = _channel_number;
        modified = false;
    }

    public boolean containsSample( int sample_index )
    {
        return !( ( sample_index < first_sample_index ) || ( sample_index >= first_sample_index + sample_number ) );
    }

    public double getSample( int sample_index, int channel ) throws DataSourceException
    {
        try
        {
            return samples[ channel ][ ( sample_index - first_sample_index ) ];
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

    public void putSample( int sample_index, int channel, double newValue ) throws DataSourceException
    {
        try
        {
            samples[ channel ][ ( sample_index - first_sample_index ) ] = newValue;
            modified = true;
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
    }

    public double[][] getSamples()
    {
        return samples;
    }

    public int getFirst_sample_index() {
        return first_sample_index;
    }

    public int getSample_number() {
        return sample_number;
    }

    public int getChannel_number() {
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

}
