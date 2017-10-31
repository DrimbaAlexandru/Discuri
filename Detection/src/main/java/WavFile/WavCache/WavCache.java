package WavFile.WavCache;

import WavFile.BasicWavFile.BasicWavFileException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Alex on 10.09.2017.
 */
public class WavCache
{

    private long maxCacheSize = 44100 * 2;
    private long usedCacheSize = 0;
    private int maxCachePageSize = 32768;

    private TreeSet< WavCachedWindow > ordered_caches = new TreeSet<>( ( cw1, cw2 ) ->
                                                                       {
                                                                           return cw1.getFirst_sample_index() - cw2.getFirst_sample_index();
                                                                       } );
    private LinkedList< WavCachedWindow > cache_acces = new LinkedList<>();

    WavCacheError lastErr = WavCacheError.NO_ERR;

    private boolean containedIn( int value, int minInclusive, int maxExclusive )
    {
        return ( ( value >= minInclusive ) && ( value < maxExclusive ) );
    }

    public WavCache()
    {
    }

    public void showCacheStatus()
    {
        System.out.println( "Cache status:" );
        System.out.println( usedCacheSize + " samples cached" );
        System.out.println( ( maxCacheSize - usedCacheSize ) + " samples free" );
        System.out.println( "Cache windows:" );
        for( WavCachedWindow w : ordered_caches )
        {
            System.out.println( w.getFirst_sample_index() + " to " + ( w.getFirst_sample_index() + w.getSample_number() ) + "( " + w.getSample_number() + " samples )" );
        }
        System.out.println( "-------" );
    }

    public WavCache( long _maxCacheSize )
    {
        maxCacheSize = _maxCacheSize;
    }

    public boolean containsSample( int sample_index )
    {
        WavCachedWindow win = ordered_caches.floor( new WavCachedWindow( null, sample_index, 1, 1 ) );
        boolean result = false;
        if( win != null )
        {
            result = win.containsSample( sample_index );
        }

        lastErr = result ? WavCacheError.NO_ERR : WavCacheError.SAMPLE_NOT_CACHED;
        return result;
    }

    public void newCache( double samples[][], int first_sample_index, int samples_number, int channels ) throws BasicWavFileException
    {
        showCacheStatus();
        WavCachedWindow left, right;
        int i;

        if( samples_number * channels > maxCacheSize )
        {
            lastErr = WavCacheError.NOT_ENOUGH_SPACE;
            throw new BasicWavFileException( "Not enough space in cache" );
        }

        if( samples_number * channels > maxCacheSize - usedCacheSize )
        {
            lastErr = WavCacheError.NOT_ENOUGH_FREE_SPACE;
            throw new BasicWavFileException( "Not enough free space in cache" );
        }

        left = ordered_caches.floor( new WavCachedWindow( null, first_sample_index, 0, 0 ) );
        right = ordered_caches.ceiling( new WavCachedWindow( null, first_sample_index, 0, 0 ) );

        if( ( left != null )
              && ( containedIn( first_sample_index, left.getFirst_sample_index(), left.getFirst_sample_index() + left.getSample_number() )
                   || containedIn( first_sample_index + samples_number - 1, left.getFirst_sample_index(), left.getFirst_sample_index() + left.getSample_number() )
                   || containedIn( left.getFirst_sample_index(), first_sample_index, first_sample_index + samples_number ) ) )
        {
            lastErr = WavCacheError.SAMPLE_ALREADY_CACHED;
            throw new BasicWavFileException( "Current interval overlaps with cache. Tried to add cache interval " +
                                                     first_sample_index + " to " + ( first_sample_index + samples_number ) +
                                                     ". Window from " + left.getFirst_sample_index() + " to "
                                                     + ( left.getFirst_sample_index() + left.getSample_number() ) + " already contains it" );
        }

        if( ( right != null )
              && ( containedIn( first_sample_index, right.getFirst_sample_index(), right.getFirst_sample_index() + right.getSample_number() )
                   || containedIn( first_sample_index + samples_number - 1, right.getFirst_sample_index(), right.getFirst_sample_index() + right.getSample_number() )
                   || containedIn( right.getFirst_sample_index(), first_sample_index, first_sample_index + samples_number ) ) )
        {
            lastErr = WavCacheError.SAMPLE_ALREADY_CACHED;
            throw new BasicWavFileException( "Current interval overlaps with cache. Tried to add cache interval " +
                                                     first_sample_index + " to " + ( first_sample_index + samples_number ) +
                                                     ". Window from " + right.getFirst_sample_index() + " to "
                                                     + ( right.getFirst_sample_index() + right.getSample_number() ) + " already contains it" );
        }

        lastErr = WavCacheError.NO_ERR;
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
        while( i < samples_number )
        {
            double buffer[][] = new double[ maxCachePageSize ][ channels ];
            int temp_len = ( ( samples_number - i ) * channels > maxCachePageSize ) ? maxCachePageSize / channels : samples_number - i;
            for( int j = 0; j < temp_len; j++ )
            {
                for( int c = 0; c < channels; c++ )
                {
                    buffer[ j ][ c ] = samples[ i ][ c ];
                }
                i++;
            }
            WavCachedWindow newWin = new WavCachedWindow( buffer, i - temp_len + first_sample_index, temp_len, channels );
            ordered_caches.add( newWin );
            cache_acces.add( newWin );
        }

        usedCacheSize += samples_number * channels;
    }

    public WavCachedWindow getCacheWindow( int sample_index )
    {
        WavCachedWindow result;
        result = ordered_caches.floor( new WavCachedWindow( null, sample_index, 0, 0 ) );
        if( result != null && !result.containsSample( sample_index ) )
        {
            result = null;
        }
        lastErr = WavCacheError.SAMPLE_NOT_CACHED;
        if( result != null )
        {
            cache_acces.remove( result );
            cache_acces.add( result );
            lastErr = WavCacheError.NO_ERR;
        }
        return result;
    }

    public WavCachedWindow getOldestUsedCache()
    {
        return cache_acces.pollFirst();
    }

    public boolean freeOldestCache()
    {
        WavCachedWindow win = getOldestUsedCache();
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
        WavCachedWindow left = ordered_caches.floor( new WavCachedWindow( null, sample_index, 0, 0 ) );
        WavCachedWindow right = ordered_caches.ceiling( new WavCachedWindow( null, sample_index, 0, 0 ) );
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

    public WavCacheError getLastErr() {
        return lastErr;
    }

    public TreeSet< WavCachedWindow > getCaches()
    {
        return ordered_caches;
    }

    public long getMaxCacheSize()
    {
        return maxCacheSize;
    }
}