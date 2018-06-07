package AudioDataSource.ADCache;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;

/**
 * Created by Alex on 03.06.2018.
 */
public class CachedAudioDataSource implements IAudioDataSource
{
    private AudioDataCache cache;
    private IAudioDataSource dataSource;
    private int cache_page_size;
    private int sample_number = 0;

    public CachedAudioDataSource( IAudioDataSource dataSource, int max_cached_samples, int max_cache_page_size )
    {
        this.dataSource = dataSource;
        cache = new AudioDataCache( max_cached_samples * dataSource.get_channel_number() );
        this.cache_page_size = Math.min( max_cache_page_size, max_cached_samples );
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

    private AudioSamplesWindow get_cached_page( int any_sample_in_page ) throws DataSourceException
    {
        int page_nr = any_sample_in_page / cache_page_size;
        AudioSamplesWindow win = cache.getCacheWindow( any_sample_in_page );
        if( win == null )
        {
            win = dataSource.get_samples( page_nr * cache_page_size, cache_page_size );
            if( win.get_capacity() < cache_page_size )
            {
                double[][] samples = new double[ get_channel_number() ][ cache_page_size ];
                int k, i;
                for( k = 0; k < get_channel_number(); k++ )
                {
                    for( i = 0; i < win.get_length(); i++ )
                    {
                        samples[ k ][ i ] = win.getSamples()[ k ][ i ];
                    }
                }
                win = new AudioSamplesWindow( samples, page_nr * cache_page_size, Math.max( 0, Math.min( cache_page_size, sample_number - page_nr * cache_page_size ) ), win.get_channel_number() );
            }
            cache_window( win );
            return cache.getCacheWindow( any_sample_in_page );
        }
        else
        {
            if( win.get_first_sample_index() != page_nr * cache_page_size || win.get_capacity() != cache_page_size )
            {
                throw new DataSourceException( "Cache page doesn't have the expected interval/capacity", DataSourceExceptionCause.INVALID_STATE );
            }
            return win;
        }
    }


    @Override
    public AudioSamplesWindow get_samples( int first_sample_index, int length ) throws DataSourceException
    {
        int i, j, k, end;
        first_sample_index = Math.min( first_sample_index, get_sample_number() );
        length = Math.min( length, get_sample_number() - first_sample_index );
        double[][] samples = new double[ get_channel_number() ][ length ];
        AudioSamplesWindow win;

        for( i = first_sample_index; i < first_sample_index + length; )
        {
            win = get_cached_page( i );
            end = Math.min( first_sample_index + length, win.get_after_last_sample_index() );
            for( k = 0; k < get_channel_number(); k++ )
            {
                for( j = i; j < end; j++ )
                {
                    samples[ k ][ j - first_sample_index ] = win.getSample( j, k );
                }
            }
            i = end;
        }
        return new AudioSamplesWindow( samples, first_sample_index, length, get_channel_number() );
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
        int i, j, k, last;
        int temp_length;
        AudioSamplesWindow win;

        for( i = new_samples.get_first_sample_index(); i < new_samples.get_after_last_sample_index(); )
        {
            win = get_cached_page( i );
            last = Math.min( win.get_first_sample_index() + win.get_capacity(), new_samples.get_after_last_sample_index() );
            if( last > win.get_after_last_sample_index() )
            {
                win.set_length( last - win.get_first_sample_index() );
            }
            for( k = 0; k < get_channel_number(); k++ )
            {
                for( j = i; j < last; j++ )
                {
                    win.putSample( j, k, new_samples.getSample( j, k ) );
                }
            }
            i = last;
            sample_number = Math.max( sample_number, i );
        }
    }

    @Override
    public void close() throws DataSourceException
    {
        dataSource.close();
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

    private void cache_window( AudioSamplesWindow win ) throws DataSourceException
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
                        }
                        cache.freeOldestCache();
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
        }
        sample_number = dataSource.get_sample_number();
    }
}
