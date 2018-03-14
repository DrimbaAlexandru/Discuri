package AudioDataSource.ADCache;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;

/**
 * Created by Alex on 06.12.2017.
 */
public class CachedAudioDataSource implements IAudioDataSource
{
    private AudioDataCache cache;
    private IAudioDataSource dataSource;
    private int max_cache_page_size;
    private int sample_number = 0;

    public CachedAudioDataSource( IAudioDataSource dataSource, int max_cached_samples, int max_cache_page_size )
    {
        this.dataSource = dataSource;
        cache = new AudioDataCache( max_cached_samples * dataSource.get_channel_number() );
        this.max_cache_page_size = Math.min( max_cache_page_size, max_cached_samples );
        sample_number = dataSource.get_sample_number();
    }

    @Override
    public int get_channel_number()
    {
        return dataSource.get_channel_number();
    }

    @Override
    public int get_sample_number()
    {
        return sample_number;
    }

    @Override
    public int get_sample_rate()
    {
        return dataSource.get_sample_rate();
    }

    private AudioSamplesWindow cache_samples( int first_sample_index, int length, boolean build_window ) throws DataSourceException
    {
        double samples[][] = null;
        if( build_window )
        {
            samples = new double[ get_channel_number() ][ length ];
        }
        int i, j, k;
        int temp_length;
        int fetch_size;
        AudioSamplesWindow win;
        int cacheLastSampleIndex;

        length = Math.min( length, get_sample_number() - first_sample_index );

        for( i = 0; i < length; )
        {
            win = cache.getCacheWindow( i + first_sample_index );
            if( win == null )
            {
                cacheLastSampleIndex = cache.getNextCachedSampleIndex( i + first_sample_index );
                cacheLastSampleIndex = ( cacheLastSampleIndex < 0 ) ? get_sample_number() : cacheLastSampleIndex;
                temp_length = Math.min( cacheLastSampleIndex - i - first_sample_index, length - i );
                //temp_length = ( temp_length < 0 ) ? length - i : temp_length;
                temp_length = ( temp_length > max_cache_page_size ) ? max_cache_page_size : temp_length;
                fetch_size = Math.min( max_cache_page_size, cacheLastSampleIndex - i - first_sample_index );

                win = dataSource.get_samples( i + first_sample_index, fetch_size );
                if( win.get_length() < max_cache_page_size && cacheLastSampleIndex == get_sample_number() )
                {
                    double newbuffer[][] = new double[ get_channel_number() ][ max_cache_page_size ];
                    for( k = 0; k < get_channel_number(); k++ )
                    {
                        for( j = 0; j < win.get_length(); j++ )
                        {
                            newbuffer[ k ][ j ] = win.getSamples()[ k ][ j ];
                        }
                    }
                    win = new AudioSamplesWindow( newbuffer, win.get_first_sample_index(), win.get_length(), win.get_channel_number() );
                }
                cache_samples( win );

                if( build_window )
                {
                    for( k = 0; k < get_channel_number(); k++ )
                    {
                        for( j = 0; j < temp_length; j++ )
                        {
                            samples[ k ][ i + j ] = win.getSample( i + j + first_sample_index, k );
                        }
                    }
                }
                i += temp_length;
            }
            else
            {
                temp_length = Math.min( length - i, win.get_length() - i - first_sample_index + win.get_first_sample_index() );
                if( build_window )
                {
                    for( k = 0; k < get_channel_number(); k++ )
                    {
                        for( j = 0; j < temp_length; j++ )
                        {
                            samples[ k ][ i + j ] = win.getSample( i + j + first_sample_index, k );
                        }
                    }
                }
                i += temp_length;
            }
        }
        if( build_window )
        {
            return new AudioSamplesWindow( samples, first_sample_index, length, get_channel_number() );
        }
        else
        {
            return null;
        }
    }

    @Override
    public AudioSamplesWindow get_samples( int first_sample_index, int length ) throws DataSourceException
    {
        return cache_samples( first_sample_index, length, true );
    }

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
    public void put_samples( AudioSamplesWindow new_samples ) throws DataSourceException
    {
        int i, j, k;
        int temp_length;
        AudioSamplesWindow win;

        for( i = 0; i < new_samples.get_length(); )
        {
            win = cache.getCacheWindow( i + new_samples.get_first_sample_index() );
            if( win == null )
            {
                cache_samples( i + new_samples.get_first_sample_index(), 1, false );
            }
            win = cache.getCacheWindow( i + new_samples.get_first_sample_index() );
            if( win == null )
            {
                if( i + new_samples.get_first_sample_index() == get_sample_number() )
                {
                    double samples[][] = new double[ new_samples.get_channel_number() ][ max_cache_page_size ];
                    temp_length = Math.min( max_cache_page_size, new_samples.get_length() - i );
                    for( k = 0; k < get_channel_number(); k++ )
                    {
                        for( j = 0; j < temp_length; j++ )
                        {
                            samples[ k ][ j ] = new_samples.getSample( i + j + new_samples.get_first_sample_index(), k );
                        }
                    }
                    win = new AudioSamplesWindow( samples, new_samples.get_first_sample_index() + i, temp_length, new_samples.get_channel_number() );
                    win.markModified();
                    cache_samples( win );
                    i += temp_length;
                }
                else
                {
                    throw new DataSourceException( "This should never happen", DataSourceExceptionCause.THIS_SHOULD_NEVER_HAPPEN );
                }
            }
            else
            {
                temp_length = Math.min( new_samples.get_length() - i, win.get_capacity() + win.get_first_sample_index() - ( new_samples.get_first_sample_index() + i ) );
                int max_put = i + temp_length + new_samples.get_first_sample_index();
                if( max_put > win.get_after_last_sample_index() )
                {
                    win.set_length( max_put - win.get_first_sample_index() );
                }

                for( k = 0; k < get_channel_number(); k++ )
                {
                    for( j = 0; j < temp_length; j++ )
                    {
                        win.putSample( j + i + new_samples.get_first_sample_index(), k, new_samples.getSample( i + j + new_samples.get_first_sample_index(), k ) );
                    }
                }

                i += temp_length;
            }
            sample_number = Math.max( sample_number, new_samples.get_first_sample_index() + i );
        }

    }

    private void flush( AudioSamplesWindow win ) throws DataSourceException
    {
        dataSource.put_samples( win );
        win.markAsFlushed();
    }

    public void flushAll() throws DataSourceException
    {
        for( AudioSamplesWindow win : cache.getCaches() )
        {
            if( win.isModified() )
            {
                flush( win );
                win.markAsFlushed();
            }
        }
        sample_number = dataSource.get_sample_number();
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

    public AudioDataCache getCache()
    {
        return cache;
    }

    private void invalidateCache() throws DataSourceException
    {
        flushAll();
        while( cache.getCache_access().size() > 0 && cache.freeOldestCache() );

        if( cache.getCache_access().size() != 0 )
        {
            throw new DataSourceException( "Cache invalidation failed", DataSourceExceptionCause.GENERIC_ERROR );
        }
    }

    public void setDataSource( IAudioDataSource dataSource ) throws DataSourceException
    {
        if( dataSource != this.dataSource )
        {
            invalidateCache();
            this.dataSource = dataSource;
            sample_number = dataSource.get_sample_number();
        }

    }
}
