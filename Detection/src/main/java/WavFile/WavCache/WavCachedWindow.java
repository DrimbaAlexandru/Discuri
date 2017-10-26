package WavFile.WavCache;

/**
 * Created by Alex on 10.09.2017.
 */
public class WavCachedWindow {

    private double samples[][];
    private int sample_number;
    private int channel_number;
    private int first_sample_index;
    private boolean modified;

    private WavCacheError lastErr = WavCacheError.NO_ERR;

    public WavCachedWindow( double _samples[][], int _first_sample_index, int _sample_number, int _channel_number )
    {
        samples = _samples;
        first_sample_index = _first_sample_index;
        sample_number = _sample_number;
        channel_number = _channel_number;
        modified = false;
    }

    public boolean containsSample( int sample_index )
    {
        lastErr = WavCacheError.NO_ERR;
        return !( ( sample_index < first_sample_index ) || ( sample_index >= first_sample_index + sample_number ) );
    }

    public double getSample( int sample_index, int channel )
    {
        if( !containsSample( sample_index ) )
        {
            lastErr = WavCacheError.SAMPLE_NOT_CACHED;
            throw new ArrayIndexOutOfBoundsException( "Requested frame ("+sample_index+") is not cached" );
        }

        if( ( channel < 0 ) || ( channel >= channel_number ) )
        {
            lastErr = WavCacheError.CHANNEL_NOT_VALID;
            throw new ArrayIndexOutOfBoundsException( "Requested channel is not valid" );
        }

        lastErr = WavCacheError.NO_ERR;
        return samples[ ( sample_index - first_sample_index ) ][ channel ];
    }

    public void putSample( int sample_index, int channel, double newValue )
    {
        if( !containsSample( sample_index ) )
        {
            lastErr = WavCacheError.SAMPLE_NOT_CACHED;
            throw new ArrayIndexOutOfBoundsException( "Requested frame is not cached" );
        }
        if( ( channel < 0 ) || ( channel >= channel_number ) )
        {
            lastErr = WavCacheError.CHANNEL_NOT_VALID;
            throw new ArrayIndexOutOfBoundsException( "Requested channel is not valid" );
        }

        samples[ ( sample_index - first_sample_index ) ][ channel ] = newValue;
        modified = true;
        lastErr = WavCacheError.NO_ERR;
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

    public void markAsFlushed()
    {
        modified = false;
    }

    public WavCacheError getLastErr() {
        return lastErr;
    }
}
