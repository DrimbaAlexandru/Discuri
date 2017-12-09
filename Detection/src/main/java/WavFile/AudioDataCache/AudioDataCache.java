package WavFile.AudioDataCache;

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
                                                                           return cw1.getFirst_sample_index() - cw2.getFirst_sample_index();
                                                                       } );
    private LinkedList< AudioSamplesWindow > cache_acces = new LinkedList<>();

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
            System.out.println( w.getFirst_sample_index() + " to " + ( w.getFirst_sample_index() + w.getSample_number() ) + "( " + w.getSample_number() + " samples )" );
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

        if( win.getSample_number() * win.getChannel_number() > maxCacheSize )
        {
            throw new DataSourceException( "Not enough space in cache", NOT_ENOUGH_SPACE );
        }

        if( win.getSample_number() * win.getChannel_number() > maxCacheSize - usedCacheSize )
        {
            throw new DataSourceException( "Not enough free space in cache", NOT_ENOUGH_FREE_SPACE );
        }

        left = ordered_caches.floor( new AudioSamplesWindow( null, win.getFirst_sample_index(), 0, 0 ) );
        right = ordered_caches.ceiling( new AudioSamplesWindow( null, win.getFirst_sample_index(), 0, 0 ) );

        if( ( left != null )
              && ( containedIn( win.getFirst_sample_index(), left.getFirst_sample_index(), left.getFirst_sample_index() + left.getSample_number() )
                   || containedIn( win.getFirst_sample_index() + win.getSample_number() - 1, left.getFirst_sample_index(), left.getFirst_sample_index() + left.getSample_number() )
                   || containedIn( left.getFirst_sample_index(), win.getFirst_sample_index(), win.getFirst_sample_index() + win.getSample_number() ) ) )
        {
            throw new DataSourceException( "Current interval overlaps with cache. Tried to add cache interval " +
                                                     win.getFirst_sample_index() + " to " + ( win.getFirst_sample_index() + win.getSample_number() ) +
                                                     ". Window from " + left.getFirst_sample_index() + " to "
                                                     + ( left.getFirst_sample_index() + left.getSample_number() ) + " already contains it" ,
                                           SAMPLE_ALREADY_CACHED );
        }

        if( ( right != null )
              && ( containedIn( win.getFirst_sample_index(), right.getFirst_sample_index(), right.getFirst_sample_index() + right.getSample_number() )
                   || containedIn( win.getFirst_sample_index() + win.getSample_number() - 1, right.getFirst_sample_index(), right.getFirst_sample_index() + right.getSample_number() )
                   || containedIn( right.getFirst_sample_index(), win.getFirst_sample_index(), win.getFirst_sample_index() + win.getSample_number() ) ) )
        {
            throw new DataSourceException( "Current interval overlaps with cache. Tried to add cache interval " +
                                                     win.getFirst_sample_index() + " to " + ( win.getFirst_sample_index() + win.getSample_number() ) +
                                                     ". Window from " + right.getFirst_sample_index() + " to "
                                                     + ( right.getFirst_sample_index() + right.getSample_number() ) + " already contains it" ,
                                           SAMPLE_ALREADY_CACHED );
        }

        i = 0;/*
        if( ( left != null )
              && ( left.getSample_number() * left.getChannel_number() < maxCachePageSize )
              && ( left.getFirst_sample_index() + left.getSample_number() == first_sample_index )
              && ( maxCachePageSize / channels - left.getSample_number() < samples_number % ( maxCachePageSize / channels ) ) )
        {
            int temp_len = left.getSample_number() + samples_number;
            temp_len = ( temp_len * channels > maxCachePageSize ) ? maxCachePageSize / channels : temp_len;
            double buffer[][] = new double[ temp_len ][ channels ];
            for( int j = 0; j < temp_len; j++ )
            {
                if( j < left.getSample_number() )
                {
                    for( int c = 0; c < channels; c++ )
                    {
                        buffer[ j ][ c ] = left.getSample( left.getFirst_sample_index() + j, c );
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
            cache_acces.remove( left );
            WavCachedWindow newWin = new WavCachedWindow( buffer, left.getFirst_sample_index(), temp_len, channels );
            if( left.isModified() )
            {
                newWin.setModified();
            }
            ordered_caches.add( newWin );
            cache_acces.add( newWin );
        }
*/
        //caches.put( caches.size(), new WavCachedWindow( samples, first_sample_index, samples_number, channels ) );
        while( i < win.getSample_number() )
        {
            int temp_len = ( ( win.getSample_number() - i ) * win.getChannel_number() > maxCachePageSize ) ? maxCachePageSize / win.getChannel_number() : win.getSample_number() - i;
            double buffer[][] = new double[ win.getChannel_number() ][ temp_len ];
            for( int j = 0; j < temp_len; j++ )
            {
                for( int c = 0; c < win.getChannel_number(); c++ )
                {
                    buffer[ c ][ j ] = win.getSample( i + win.getFirst_sample_index(), c );
                }
                i++;
            }
            AudioSamplesWindow newWin = new AudioSamplesWindow( buffer, i - temp_len + win.getFirst_sample_index(), temp_len, win.getChannel_number() );
            ordered_caches.add( newWin );
            cache_acces.add( newWin );
        }

        usedCacheSize += win.getChannel_number() * win.getSample_number();
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
            cache_acces.remove( result );
            cache_acces.add( result );
        }
        return result;
    }

    public AudioSamplesWindow getOldestUsedCache()
    {
        return cache_acces.getFirst();
    }

    public boolean freeOldestCache()
    {
        AudioSamplesWindow win = getOldestUsedCache();
        if( ( win == null ) || ( win.isModified() ) )
            return false;
        else
        {
            ordered_caches.remove( win );
            cache_acces.remove( win );
            usedCacheSize -= win.getChannel_number() * win.getSample_number();
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
                return right.getFirst_sample_index();
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
                    return right.getFirst_sample_index();
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