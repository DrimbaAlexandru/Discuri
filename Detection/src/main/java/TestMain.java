import WavFile.BasicWavFile.BasicWavFileException;
import WavFile.WavCache.WavCache;
import WavFile.WavCache.WavCacheError;
import WavFile.WavCache.WavCachedWindow;
import WavFile.WavFile;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by Alex on 08.09.2017.
 */
public class TestMain {

    private static void setVal( double buffer[][], double val, int size, int channels )
    {
        int i,j;
        for( i = 0; i < size; i++ )
        {
            for( j = 0; j < channels; j++ )
            {
                buffer[ i ][ j ] = val;
            }
        }
    }

    public static void main(String[] args)
    {
        WavCache cache = new WavCache( 100 );
        WavCachedWindow win;
        double s1[][] = new double[50][1];
        double s2[][] = new double[50][1];
        double s3[][] = new double[1][1];
        double s4[][] = new double[101][1];

        setVal( s1, 1, 50, 1 );
        setVal( s2, 2, 50, 1 );
        setVal( s3, 3, 1, 1 );
        setVal( s4, 4, 101, 1 );

        /*----------------------------------------------------------
        Test adding cache when size is sufficient
        ----------------------------------------------------------*/
        try {
            cache.newCache( s1, 0, 50, 1 );
        }
        catch ( Exception e )
        {
            assert ( false );
        }
        assert cache.getLastErr() == WavCacheError.NO_ERR;

        assert cache.getCaches().size() == 1;

        assert cache.containsSample( 0 );
        assert !cache.containsSample( -1 );
        assert cache.containsSample( 49 );
        assert !cache.containsSample( 50 );

        assert cache.getNextCachedSampleIndex( 0 ) == 1;
        assert cache.getNextCachedSampleIndex( -1 ) == 0;
        assert cache.getNextCachedSampleIndex( 48 ) == 49;
        assert cache.getNextCachedSampleIndex( 49 ) == -1;
        assert cache.getNextCachedSampleIndex( 50 ) == -1;

        win = cache.getCacheWindow( 0 );
        assert ( win != null );
        win = cache.getCacheWindow( 50 );
        assert ( win == null );

        /*----------------------------------------------------------
        Test adding cache when remaining size is sufficient
        ----------------------------------------------------------*/
        try{
            cache.newCache( s2, 150, 50, 1 );
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            assert ( false );
        }
        assert cache.getLastErr() == WavCacheError.NO_ERR;

        assert cache.getCaches().size() == 2;

        assert cache.containsSample( 0 );
        assert !cache.containsSample( 50 );
        assert cache.containsSample( 150 );
        assert !cache.containsSample( 200 );

        assert cache.getNextCachedSampleIndex( 0 ) == 1;
        assert cache.getNextCachedSampleIndex( -1 ) == 0;
        assert cache.getNextCachedSampleIndex( 49 ) == 150;
        assert cache.getNextCachedSampleIndex( 199 ) == -1;
        assert cache.getNextCachedSampleIndex( 200 ) == -1;

        win = cache.getCacheWindow( 150 );
        assert ( win != null );
        win = cache.getCacheWindow( 200 );
        assert ( win == null );

        /*----------------------------------------------------------
        Test adding cache when remaining size is insufficient
        ----------------------------------------------------------*/
        try{
            cache.newCache( s3, 200, 1, 1 );
            assert ( false );
        }
        catch ( Exception e )
        {
            assert cache.getLastErr() == WavCacheError.NOT_ENOUGH_FREE_SPACE;
            assert cache.getCaches().size() == 2;
            assert cache.getCacheWindow( 200 ) == null;
        }

        /*----------------------------------------------------------
        Test adding cache when total size is insufficient
        ----------------------------------------------------------*/
        while( cache.getCaches().size() > 0 )
        {
            cache.getOldestUsedCache().markAsFlushed();
            cache.freeOldestCache();
        }
        assert cache.getCaches().size() == 0;

        try{
            cache.newCache( s4, 1000, 101, 1 );
            assert ( false );
        }
        catch ( Exception e )
        {
            assert cache.getLastErr() == WavCacheError.NOT_ENOUGH_SPACE;
            assert cache.getCaches().size() == 0;
        }

    }

}
