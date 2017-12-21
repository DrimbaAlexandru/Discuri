package AudioDataSource.ADCache;

import AudioDataSource.Exceptions.DataSourceException;

import java.util.*;

import static AudioDataSource.Exceptions.DataSourceExceptionCause.NOT_ENOUGH_FREE_SPACE;
import static AudioDataSource.Exceptions.DataSourceExceptionCause.NOT_ENOUGH_SPACE;
import static AudioDataSource.Exceptions.DataSourceExceptionCause.SAMPLE_ALREADY_CACHED;

/**
 * Created by Alex on 10.09.2017.
 */
public class AudioDataCache
{
    private long maxCacheSize = 44100 * 2;
    private long usedCacheSize = 0;
    private int maxCachePageSize = 32768;

    private TreeSet< AudioSamplesWindow > ordered_caches = new TreeSet<>( ( cw1, cw2 ) ->
                                                                       {
                                                                           return cw1.get_first_sample_index() - cw2.get_first_sample_index();
                                                                       } );
    private LinkedList< AudioSamplesWindow > cache_access = new LinkedList<>();

    private boolean containedIn( int value, int minInclusive, int maxExclusive )
    {
        return ( ( value >= minInclusive ) && ( value < maxExclusive ) );
    }

    public AudioDataCache()
    {
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

    public AudioDataCache( long _maxCacheSize )
    {
        maxCacheSize = _maxCacheSize;
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
        showCacheStatus();
        AudioSamplesWindow left, right;
        int i;

        if( win.get_length() * win.get_channel_number() > maxCacheSize )
        {
            throw new DataSourceException( "Not enough space in cache", NOT_ENOUGH_SPACE );
        }

        if( win.get_length() * win.get_channel_number() > maxCacheSize - usedCacheSize )
        {
            throw new DataSourceException( "Not enough free space in cache", NOT_ENOUGH_FREE_SPACE );
        }

        left = ordered_caches.floor( new AudioSamplesWindow( null, win.get_first_sample_index(), 0, 0 ) );
        right = ordered_caches.ceiling( new AudioSamplesWindow( null, win.get_first_sample_index(), 0, 0 ) );

        if( ( left != null )
              && ( containedIn( win.get_first_sample_index(), left.get_first_sample_index(), left.get_first_sample_index() + left.get_length() )
                   || containedIn( win.get_first_sample_index() + win.get_length() - 1, left.get_first_sample_index(), left.get_first_sample_index() + left.get_length() )
                   || containedIn( left.get_first_sample_index(), win.get_first_sample_index(), win.get_first_sample_index() + win.get_length() ) ) )
        {
            throw new DataSourceException( "Current interval overlaps with cache. Tried to add cache interval " +
                                                     win.get_first_sample_index() + " to " + ( win.get_first_sample_index() + win.get_length() ) +
                                                     ". Window from " + left.get_first_sample_index() + " to "
                                                     + ( left.get_first_sample_index() + left.get_length() ) + " already contains it" ,
                                           SAMPLE_ALREADY_CACHED );
        }

        if( ( right != null )
              && ( containedIn( win.get_first_sample_index(), right.get_first_sample_index(), right.get_first_sample_index() + right.get_length() )
                   || containedIn( win.get_first_sample_index() + win.get_length() - 1, right.get_first_sample_index(), right.get_first_sample_index() + right.get_length() )
                   || containedIn( right.get_first_sample_index(), win.get_first_sample_index(), win.get_first_sample_index() + win.get_length() ) ) )
        {
            throw new DataSourceException( "Current interval overlaps with cache. Tried to add cache interval " +
                                                     win.get_first_sample_index() + " to " + ( win.get_first_sample_index() + win.get_length() ) +
                                                     ". Window from " + right.get_first_sample_index() + " to "
                                                     + ( right.get_first_sample_index() + right.get_length() ) + " already contains it" ,
                                           SAMPLE_ALREADY_CACHED );
        }

        i = 0;/*
        if( ( left != null )
              && ( left.get_length() * left.get_channel_number() < maxCachePageSize )
              && ( left.get_first_sample_index() + left.get_length() == first_sample_index )
              && ( maxCachePageSize / channels - left.get_length() < samples_number % ( maxCachePageSize / channels ) ) )
        {
            int temp_len = left.get_length() + samples_number;
            temp_len = ( temp_len * channels > maxCachePageSize ) ? maxCachePageSize / channels : temp_len;
            double buffer[][] = new double[ temp_len ][ channels ];
            for( int j = 0; j < temp_len; j++ )
            {
                if( j < left.get_length() )
                {
                    for( int c = 0; c < channels; c++ )
                    {
                        buffer[ j ][ c ] = left.getSample( left.get_first_sample_index() + j, c );
                    }
                }
                else
                {
                    for( int c = 0; c < channels; c++ )
                    {
                        buffer[ j ][ c ] = samples[ i++ ][ c ];
                    }
                }
            }
            ordered_caches.remove( left );
            cache_access.remove( left );
            WavCachedWindow newWin = new WavCachedWindow( buffer, left.get_first_sample_index(), temp_len, channels );
            if( left.isModified() )
            {
                newWin.setModified();
            }
            ordered_caches.add( newWin );
            cache_access.add( newWin );
        }
*/
        //caches.put( caches.size(), new WavCachedWindow( samples, first_sample_index, samples_number, channels ) );
        while( i < win.get_length() )
        {
            int temp_len = ( ( win.get_length() - i ) * win.get_channel_number() > maxCachePageSize ) ? maxCachePageSize / win.get_channel_number() : win.get_length() - i;
            double buffer[][] = new double[ win.get_channel_number() ][ temp_len ];
            for( int j = 0; j < temp_len; j++ )
            {
                for( int c = 0; c < win.get_channel_number(); c++ )
                {
                    buffer[ c ][ j ] = win.getSample( i + win.get_first_sample_index(), c );
                }
                i++;
            }
            AudioSamplesWindow newWin = new AudioSamplesWindow( buffer, i - temp_len + win.get_first_sample_index(), temp_len, win.get_channel_number() );
            ordered_caches.add( newWin );
            cache_access.add( newWin );
        }

        usedCacheSize += win.get_channel_number() * win.get_length();
    }

    public AudioSamplesWindow getCacheWindow( int sample_index )
    {
        AudioSamplesWindow result;
        result = ordered_caches.floor( new AudioSamplesWindow( null, sample_index, 0, 0 ) );
        if( result != null && !result.containsSample( sample_index ) )
        {
            result = null;
        }
        if( result != null )
        {
            cache_access.remove( result );
            cache_access.add( result );
        }
        return result;
    }

    public AudioSamplesWindow getOldestUsedCache()
    {
        return cache_access.getFirst();
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
            usedCacheSize -= win.get_channel_number() * win.get_length();
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
}