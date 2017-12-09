package AudioDataSource;

import AudioDataSource.Exceptions.DataSourceException;
import WavFile.AudioDataCache.AudioSamplesWindow;
import WavFile.AudioDataCache.AudioDataCache;

/**
 * Created by Alex on 06.12.2017.
 */
public class CachedAudioDataSource implements IAudioDataSource
{
    private AudioDataCache cache;
    private IAudioDataSource dataSource;

    public CachedAudioDataSource( IAudioDataSource dataSource, int max_cached_samples )
    {
        this.dataSource = dataSource;
        cache = new AudioDataCache( max_cached_samples * dataSource.get_channel_number() );
    }

    @Override
    public int get_channel_number()
    {
        return dataSource.get_channel_number();
    }

    @Override
    public int get_sample_number()
    {
        return dataSource.get_sample_number();
    }

    @Override
    public int get_sample_rate()
    {
        return dataSource.get_sample_rate();
    }

    @Override
    public AudioSamplesWindow get_samples( int first_sample_index, int length ) throws DataSourceException
    {
        double samples[][] = new double[ get_channel_number() ][ length ];
        int i, j, k;
        int temp_length;
        AudioSamplesWindow win;

        for( i = first_sample_index; i < first_sample_index + length; i++ )
        {
            win = cache.getCacheWindow( i );
            if( win == null )
            {
                temp_length = Math.min( cache.getNextCachedSampleIndex( i ) - i, first_sample_index + length - i );
                temp_length = ( temp_length < 0 ? first_sample_index - i + length : temp_length );

                win = dataSource.get_samples( i, temp_length );
                cache_samples( win );

                for( k = 0; k < get_channel_number(); k++ )
                {
                    for( j = 0; j < temp_length; j++ )
                    {
                        samples[ k ][ i - first_sample_index + j ] = win.getSample( i + j, k );
                    }
                }

                i += temp_length - 1;
            }
            else
            {
                temp_length = Math.min( length - i + first_sample_index,
                                        win.getSample_number() - i + win.getFirst_sample_index() );
                for( k = 0; k < get_channel_number(); k++ )
                {
                    for( j = 0; j < temp_length; j++ )
                    {
                        samples[ k ][ i - first_sample_index + j ] = win.getSample( i + j, k );
                    }
                }

                i += temp_length - 1;
            }
        }
        return new AudioSamplesWindow( samples, first_sample_index, length, get_channel_number() );
    }

    @Override
    public AudioSamplesWindow get_resized_samples( int first_sample_index, int length, int resized_length ) throws DataSourceException
    {
        int i, j, previ, curi;
        boolean alt;
        double samples[][] = new double[ get_channel_number() ][ resized_length ];
        AudioSamplesWindow win;

        previ = -1;
        alt = false;

        if( length <= resized_length )
        {
            win = get_samples( first_sample_index, length );

            for( curi = 0; curi < resized_length; curi++ )
            {
                for( j = 0; j < get_channel_number(); j++ )
                {
                    samples[ j ][ curi ] = win.getSample( first_sample_index + curi * length / resized_length, j );
                }
            }
        }
        else
        {
            if( length * get_channel_number() <= cache.getMaxCacheSize() )
            {
                win = get_samples( first_sample_index, length );

                for( i = 0; i < length; i++ )
                {
                    curi = i * resized_length / length;

                    if( previ != curi )
                    {
                        previ = curi;
                        for( j = 0; j < get_channel_number(); j++ )
                        {
                            samples[ j ][ curi ] = win.getSample( first_sample_index + i, j );
                        }
                    }
                    else
                    {
                        for( j = 0; j < get_channel_number(); j++ )
                        {
                            if( alt )
                            {
                                samples[ j ][ curi ] = Math.min( win.getSample( first_sample_index + i, j ), samples[ j ][ curi ] );
                            }
                            else
                            {
                                samples[ j ][ curi ] = Math.max( win.getSample( first_sample_index + i, j ), samples[ j ][ curi ] );
                            }
                        }
                    }
                    alt = !alt;
                }
            }

        }
        return new AudioSamplesWindow( samples, 0, resized_length, get_channel_number() );
    }

    @Override
    public void put_samples( AudioSamplesWindow new_samples )
    {

    }

    private void flush( AudioSamplesWindow win )
    {

    }

    public void flushAll()
    {

    }

    private void cache_samples( AudioSamplesWindow win ) throws DataSourceException
    {
        boolean succeeded = false;
        while( !succeeded )
        {
            try
            {
                cache.newCache( win );
                succeeded = true;
            }
            catch( DataSourceException e )
            {
                switch( e.getDSEcause() )
                {
                    case NOT_ENOUGH_FREE_SPACE:
                    {
                        if( cache.getOldestUsedCache().isModified() )
                        {
                            cache.getOldestUsedCache().markAsFlushed();
                            flush( cache.getOldestUsedCache() );
                            cache.freeOldestCache();
                        }
                        else
                        {
                            cache.freeOldestCache();
                        }
                        break;
                    }
                    default:
                        throw e;
                }
            }
        }
    }

}
