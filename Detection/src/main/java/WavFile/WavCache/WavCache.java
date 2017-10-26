package WavFile.WavCache;

import WavFile.BasicWavFile.BasicWavFileException;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Alex on 10.09.2017.
 */
public class WavCache
{

    private long maxCacheSize = 44100 * 2;
    private long usedCacheSize = 0;
    private Map< Integer, WavCachedWindow > caches = new HashMap<>();

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
        for( WavCachedWindow w : caches.values() )
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
        boolean result = false;
        for( WavCachedWindow w : caches.values() )
        {
            result = result || w.containsSample( sample_index );
            lastErr = result ? WavCacheError.NO_ERR : WavCacheError.SAMPLE_NOT_CACHED;
        }

        return result;
    }

    public void newCache( double samples[][], int first_sample_index, int samples_number, int channels ) throws BasicWavFileException
    {
        boolean intervalAlreadyContained = false;
        WavCachedWindow overlap = null;
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

        for( WavCachedWindow w : caches.values() )
        {
            if( containedIn( first_sample_index, w.getFirst_sample_index(), w.getFirst_sample_index() + w.getSample_number() )
             || containedIn( first_sample_index + samples_number - 1, w.getFirst_sample_index(), w.getFirst_sample_index() + w.getSample_number() )
             || containedIn( w.getFirst_sample_index(), first_sample_index, first_sample_index + samples_number ) )
            {
                intervalAlreadyContained = true;
                overlap = w;
                break;
            }
        }
        if( intervalAlreadyContained )
        {
            lastErr = WavCacheError.SAMPLE_ALREADY_CACHED;
            throw new BasicWavFileException( "Current interval overlaps with cache. Tried to add cache interval " +
                                             first_sample_index + " to " + ( first_sample_index + samples_number ) +
                                             ". Window from " + overlap.getFirst_sample_index() + " to "
                                             + ( overlap.getFirst_sample_index() + overlap.getSample_number() ) + " already contains it" );
        }
        lastErr = WavCacheError.NO_ERR;
        caches.put( caches.size(), new WavCachedWindow( samples, first_sample_index, samples_number, channels ) );
        usedCacheSize += samples_number * channels;
    }

    public WavCachedWindow getCacheWindow( int sample_index )
    {
        WavCachedWindow result = null;
        int k = 0;
        for( int key : caches.keySet() )
        {
            if( caches.get( key ).containsSample( sample_index ) )
            {
                result = caches.get( key );
                k = key;
                break;
            }
        }

        lastErr = WavCacheError.SAMPLE_NOT_CACHED;
        if( result != null )
        {
            caches.remove( k );
            for( int i = k; i > 0; i-- )
            {
                caches.put( i, caches.get( i - 1 ) );
            }
            caches.put( 0, result );
            lastErr = WavCacheError.NO_ERR;
        }
        return result;
    }

    public WavCachedWindow getOldestUsedCache()
    {
        return caches.get( caches.size() - 1 );
    }

    public boolean freeOldestCache()
    {
        WavCachedWindow win = getOldestUsedCache();
        if( ( win == null ) || ( win.isModified() ) )
            return false;
        else
        {
            caches.remove( caches.size() - 1 );
            usedCacheSize -= win.getChannel_number() * win.getSample_number();
            return true;
        }
    }

    public int getNextCachedSampleIndex( int sample_index )
    {
        int nextSample = -1;
        for( WavCachedWindow win:caches.values())
        {
            if( ( sample_index + 1 >= win.getFirst_sample_index() )
             && ( sample_index + 1 < win.getFirst_sample_index() + win.getSample_number() ) )
            {
                return sample_index + 1;
            }
            if( sample_index + 1 < win.getFirst_sample_index() )
            {
                if( nextSample == -1 )
                {
                    nextSample = win.getFirst_sample_index();
                }
                else
                {
                    nextSample = Math.min( win.getFirst_sample_index(), nextSample );
                }
            }
        }
        return nextSample;
    }

    public WavCacheError getLastErr() {
        return lastErr;
    }

    public Map<Integer, WavCachedWindow> getCaches() {
        return caches;
    }

    public long getMaxCacheSize()
    {
        return maxCacheSize;
    }
}