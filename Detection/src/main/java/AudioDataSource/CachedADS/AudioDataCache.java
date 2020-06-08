package AudioDataSource.CachedADS;

import AudioDataSource.AudioSamplesWindow;
import Utils.Exceptions.DataSourceException;

import java.util.LinkedList;
import java.util.TreeSet;

import static Utils.Exceptions.DataSourceExceptionCause.*;

/**
 * Created by Alex on 10.09.2017.
 */
public class AudioDataCache
{
    private int maxCacheSize = 44100 * 2;
    private int usedCacheSize = 0;

    private TreeSet< AudioSamplesWindow > ordered_caches = new TreeSet<>( ( cw1, cw2 ) ->
                                                                          {
                                                                              return cw1.get_first_sample_index() - cw2.get_first_sample_index();
                                                                          } );
    private LinkedList< AudioSamplesWindow > cache_access = new LinkedList<>();

    public AudioDataCache( int _max_cache_size )
    {
        maxCacheSize = ( _max_cache_size == 0 ) ? maxCacheSize : _max_cache_size;
    }

    public void showCacheStatus()
    {
        System.out.println( "Cache status:" );
        System.out.println( usedCacheSize + " samples cached" );
        System.out.println( ( maxCacheSize - usedCacheSize ) + " samples free" );
        System.out.println( "Cache windows:" );
        for( AudioSamplesWindow w : ordered_caches )
        {
            System.out.println( w.get_first_sample_index() + " to " + ( w.get_first_sample_index() + w.get_length() ) + "( " + w.get_length() + " samples )" );
        }
        System.out.println( "-------" );
    }

    public boolean containsSample( int sample_index )
    {
        AudioSamplesWindow win = ordered_caches.floor( new AudioSamplesWindow( null, sample_index, 1, 1 ) );
        boolean result = false;
        if( win != null )
        {
            result = win.containsSample( sample_index );
        }
        return result;
    }

    public void newCache( AudioSamplesWindow win ) throws DataSourceException
    {
        //showCacheStatus();
        AudioSamplesWindow left, right;

        if( win.get_capacity() * win.get_channel_number() > maxCacheSize )
        {
            throw new DataSourceException( "Not enough space in cache", NOT_ENOUGH_SPACE );
        }

        if( win.get_capacity() * win.get_channel_number() > maxCacheSize - usedCacheSize )
        {
            throw new DataSourceException( "Not enough free space in cache", NOT_ENOUGH_FREE_SPACE );
        }

        left = ordered_caches.floor( new AudioSamplesWindow( null, win.get_first_sample_index(), 0, 0 ) );
        right = ordered_caches.ceiling( new AudioSamplesWindow( null, win.get_first_sample_index(), 0, 0 ) );

        if( ( left != null ) && ( left.getInterval().getIntersection( win.getInterval() ) ) != null )
        {
            throw new DataSourceException( "Current interval overlaps with cache. Tried to add cache interval " + win.get_first_sample_index() + " to " + ( win.get_first_sample_index() + win.get_length() ) + ". Window from " + left.get_first_sample_index() + " to " + ( left.get_first_sample_index() + left.get_length() ) + " already contains it", SAMPLE_ALREADY_CACHED );
        }

        if( ( right != null ) && ( right.getInterval().getIntersection( win.getInterval() ) ) != null )
        {
            throw new DataSourceException( "Current interval overlaps with cache. Tried to add cache interval " + win.get_first_sample_index() + " to " + ( win.get_first_sample_index() + win.get_length() ) + ". Window from " + right.get_first_sample_index() + " to " + ( right.get_first_sample_index() + right.get_length() ) + " already contains it", SAMPLE_ALREADY_CACHED );
        }

        ordered_caches.add( win );
        cache_access.addFirst( win );

        usedCacheSize += win.get_channel_number() * win.get_capacity();
    }

    public AudioSamplesWindow getCacheWindow( int sample_index )
    {
        AudioSamplesWindow result;
        result = ordered_caches.floor( new AudioSamplesWindow( null, sample_index, 0, 0 ) );
        if( result != null && !result.fitsSample( sample_index ) )
        {
            result = null;
        }
        if( result != null )
        {
            cache_access.remove( result );
            cache_access.addFirst( result );
        }
        return result;
    }

    public AudioSamplesWindow getOldestUsedCache()
    {
        return cache_access.getLast();
    }

    public boolean freeOldestCache()
    {
        AudioSamplesWindow win = getOldestUsedCache();
        if( ( win == null ) || ( win.isModified() ) )
            return false;
        else
        {
            ordered_caches.remove( win );
            cache_access.remove( win );
            usedCacheSize -= win.get_channel_number() * win.get_capacity();
            return true;
        }
    }

    public int getNextCachedSampleIndex( int sample_index )
    {
        AudioSamplesWindow left = ordered_caches.floor( new AudioSamplesWindow( null, sample_index, 0, 0 ) );
        AudioSamplesWindow right = ordered_caches.ceiling( new AudioSamplesWindow( null, sample_index, 0, 0 ) );
        if( left == null )
        {
            if( right == null )
            {
                return -1;
            }
            else
            {
                return right.get_first_sample_index();
            }
        }
        else
        {
            if( !left.containsSample( sample_index + 1 ) )
            {
                if( right == null )
                {
                    return -1;
                }
                else
                {
                    return right.get_first_sample_index();
                }
            }
            else
            {
                return sample_index + 1;
            }
        }
    }

    public TreeSet< AudioSamplesWindow > getCaches()
    {
        return ordered_caches;
    }

    public long getMaxCacheSize()
    {
        return maxCacheSize;
    }

    public LinkedList< AudioSamplesWindow > getCache_access()
    {
        return cache_access;
    }

}