package AudioDataSource.ADCache;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.FileADS.IFileAudioDataSource;
import AudioDataSource.FileADS.WAVFileAudioSource;
import ProjectStatics.ProjectStatics;
import Utils.Interval;
import Utils.Utils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;

/**
 * Created by Alex on 03.06.2018.
 */
public class AnotherCachedAudioDataSourceTest
{
    IFileAudioDataSource dataSource;
    String temp_file_path = ProjectStatics.getTemp_folder() + "temp.wav";
    AudioSamplesWindow in_file_data;
    int page_size = 100;
    int nr_of_pages = 10;

    private void rewrite_temp_file() throws DataSourceException
    {
        double[][] samples = new double[ dataSource.get_channel_number() ][ page_size * nr_of_pages * 5 ];
        int k, i;
        in_file_data = new AudioSamplesWindow( samples, 0, page_size * nr_of_pages * 5, 2 );
        for( k = 0; k < in_file_data.get_channel_number(); k++ )
        {
            for( i = in_file_data.get_first_sample_index(); i < in_file_data.get_after_last_sample_index(); i++ )
            {
                samples[ k ][ i ] = 1.0 / 2 * Math.sin( 2 * Math.PI * ( k + 1 ) * i * ( dataSource.get_sample_rate() / 100 ) );
            }
        }
        dataSource.put_samples( in_file_data );
        dataSource.limit_sample_number( in_file_data.get_length() );
    }

    @Before
    public void test_Setup() throws DataSourceException
    {
        dataSource = new WAVFileAudioSource( temp_file_path, 2, 44100, 2 );
    }

    @After
    public void test_TearDown() throws IOException, DataSourceException
    {
        try
        {
            dataSource.close();
        }
        finally
        {
            Files.deleteIfExists( new File( dataSource.getFile_path() ).toPath() );
        }
    }

    @Test
    public void get_channel_number() throws Exception
    {
        CachedAudioDataSource cache = new CachedAudioDataSource( dataSource, 1000, 100 );
        Assert.assertEquals( dataSource.get_channel_number(), cache.get_channel_number() );
        cache.flushAll();
    }

    @Test
    public void get_sample_number() throws Exception
    {
        CachedAudioDataSource cache = new CachedAudioDataSource( dataSource, 1000, 100 );
        Assert.assertEquals( dataSource.get_sample_number(), cache.get_sample_number() );
        cache.flushAll();
    }

    @Test
    public void get_sample_rate() throws Exception
    {
        CachedAudioDataSource cache = new CachedAudioDataSource( dataSource, 1000, 100 );
        Assert.assertEquals( dataSource.get_sample_rate(), cache.get_sample_rate() );
        cache.flushAll();
    }

    @Test
    public void get_samples_test_1() throws Exception
    {
        int i;
        CachedAudioDataSource cache = new CachedAudioDataSource( dataSource, nr_of_pages * page_size, page_size );
        AudioSamplesWindow win, win2;

        rewrite_temp_file();
        cache.setDataSource( dataSource );

        win2 = dataSource.get_samples( 0, nr_of_pages * page_size );
        win = cache.get_samples( 0, nr_of_pages * page_size );
        Assert.assertEquals( win.get_first_sample_index(), 0 );
        Assert.assertEquals( win.get_after_last_sample_index(), page_size * nr_of_pages );
        Assert.assertEquals( win.get_channel_number(), dataSource.get_channel_number() );
        for( int k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( win.getSamples()[ k ], win2.getSamples()[ k ], Math.pow( 2, -14 ) );
        }

        i = nr_of_pages - 1;
        for( AudioSamplesWindow window : cache.getCache().getCache_access() )
        {
            Assert.assertEquals( window.get_length(), page_size );
            Assert.assertEquals( window.get_capacity(), page_size );
            Assert.assertEquals( window.get_first_sample_index(), page_size * ( i ) );
            i--;
        }
    }

    @Test
    public void get_samples_test_2() throws Exception
    {
        int i;
        CachedAudioDataSource cache = new CachedAudioDataSource( dataSource, nr_of_pages * page_size, page_size );
        AudioSamplesWindow win, win2;

        rewrite_temp_file();
        cache.setDataSource( dataSource );

        win2 = dataSource.get_samples( 0, nr_of_pages * page_size * 2 );
        win = cache.get_samples( 0, nr_of_pages * page_size * 2 );
        Assert.assertEquals( win.get_first_sample_index(), 0 );
        Assert.assertEquals( win.get_after_last_sample_index(), page_size * nr_of_pages * 2 );
        Assert.assertEquals( win.get_channel_number(), dataSource.get_channel_number() );
        for( int k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( win.getSamples()[ k ], win2.getSamples()[ k ], Math.pow( 2, -14 ) );
        }

        i = nr_of_pages * 2 - 1;
        for( AudioSamplesWindow window : cache.getCache().getCache_access() )
        {
            Assert.assertEquals( window.get_length(), page_size );
            Assert.assertEquals( window.get_capacity(), page_size );
            Assert.assertEquals( window.get_first_sample_index(), page_size * ( i ) );
            i--;
        }
    }

    @Test
    public void get_samples_test_3() throws Exception
    {
        int i;
        CachedAudioDataSource cache = new CachedAudioDataSource( dataSource, nr_of_pages * page_size, page_size );
        AudioSamplesWindow win, win2;

        rewrite_temp_file();
        cache.setDataSource( dataSource );

        win2 = dataSource.get_samples( page_size / 2, nr_of_pages * page_size );
        win = cache.get_samples( page_size / 2, nr_of_pages * page_size );
        Assert.assertEquals( win.get_first_sample_index(), page_size / 2 );
        Assert.assertEquals( win.get_after_last_sample_index(), page_size * nr_of_pages + page_size / 2 );
        Assert.assertEquals( win.get_channel_number(), dataSource.get_channel_number() );
        for( int k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( win.getSamples()[ k ], win2.getSamples()[ k ], Math.pow( 2, -14 ) );
        }

        i = nr_of_pages;
        for( AudioSamplesWindow window : cache.getCache().getCache_access() )
        {
            Assert.assertEquals( window.get_length(), page_size );
            Assert.assertEquals( window.get_capacity(), page_size );
            Assert.assertEquals( window.get_first_sample_index(), page_size * ( i ) );
            i--;
        }
    }

    @Test
    public void get_samples_test_4() throws Exception
    {
        page_size = 100;
        nr_of_pages = 10;
        int i;
        CachedAudioDataSource cache = new CachedAudioDataSource( dataSource, nr_of_pages * page_size, page_size );
        AudioSamplesWindow win, win2;

        rewrite_temp_file();
        cache.setDataSource( dataSource );

        win2 = dataSource.get_samples( page_size - 1, nr_of_pages / 2 * page_size );
        win = cache.get_samples( page_size - 1, nr_of_pages / 2 * page_size );

        Assert.assertEquals( win.get_first_sample_index(), page_size - 1 );
        Assert.assertEquals( win.get_after_last_sample_index(), page_size * nr_of_pages / 2 + page_size - 1 );
        Assert.assertEquals( win.get_channel_number(), dataSource.get_channel_number() );
        for( int k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( win.getSamples()[ k ], win2.getSamples()[ k ], Math.pow( 2, -14 ) );
        }

        win2 = dataSource.get_samples( 1000 + page_size - 1, nr_of_pages / 2 * page_size );
        win = cache.get_samples( 1000 + page_size - 1, nr_of_pages / 2 * page_size );

        Assert.assertEquals( win.get_first_sample_index(), 1000 + page_size - 1 );
        Assert.assertEquals( win.get_after_last_sample_index(), 1000 + page_size - 1 + page_size / 2 * nr_of_pages );
        Assert.assertEquals( win.get_channel_number(), dataSource.get_channel_number() );
        for( int k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( win.getSamples()[ k ], win2.getSamples()[ k ], Math.pow( 2, -14 ) );
        }

        int[] expected_pages = { 15, 14, 13, 12, 11, 10, 5, 4, 3, 2 };
        i = 0;
        for( AudioSamplesWindow window : cache.getCache().getCache_access() )
        {
            Assert.assertEquals( window.get_length(), page_size );
            Assert.assertEquals( window.get_capacity(), page_size );
            Assert.assertEquals( window.get_first_sample_index(), page_size * expected_pages[ i ] );
            i++;
        }
    }

    @Test
    public void get_samples_test_5() throws Exception
    {
        int i;
        Interval interval;
        CachedAudioDataSource cache = new CachedAudioDataSource( dataSource, nr_of_pages * page_size, page_size );
        AudioSamplesWindow win, win2;

        rewrite_temp_file();
        double[][] appended = new double[][]{ { 0.0 }, { 0.0 } };
        dataSource.put_samples( new AudioSamplesWindow( appended, dataSource.get_sample_number(), 1, 2 ) );
        cache.setDataSource( dataSource );

        Assert.assertNotEquals( dataSource.get_sample_number() % page_size, 0 );
        interval = new Interval( dataSource.get_sample_number() - 1, 1 );

        win2 = dataSource.get_samples( interval.l, interval.get_length() );
        win = cache.get_samples( interval.l, interval.get_length() );
        Assert.assertEquals( win.get_first_sample_index(), interval.l );
        Assert.assertEquals( win.get_after_last_sample_index(), interval.r );
        Assert.assertEquals( win.get_channel_number(), dataSource.get_channel_number() );
        for( int k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( win.getSamples()[ k ], win2.getSamples()[ k ], Math.pow( 2, -14 ) );
        }

        Assert.assertEquals( cache.getCache().getCache_access().size(), 1 );

        AudioSamplesWindow window = cache.getCache().getCache_access().get( 0 );

        Assert.assertEquals( window.get_length(), dataSource.get_sample_number() - dataSource.get_sample_number() / page_size * page_size );
        Assert.assertEquals( window.get_capacity(), page_size );
        Assert.assertEquals( window.get_first_sample_index(), dataSource.get_sample_number() / page_size * page_size );
    }

    @Test
    public void put_samples_test_1() throws Exception
    {
        nr_of_pages = 2;
        int i, k;
        double val;
        double[][] samples2write = new double[ dataSource.get_channel_number() ][ page_size + 1 ];
        CachedAudioDataSource cache = new CachedAudioDataSource( dataSource, nr_of_pages * page_size, page_size );
        AudioSamplesWindow win, winDS, win2write, winC;

        rewrite_temp_file();
        cache.setDataSource( dataSource );
        for( k = 0; k < dataSource.get_channel_number(); k++ )
        {
            val = new Random().nextDouble() - 0.5;
            for( i = 0; i < page_size + 1; i++ )
            {
                samples2write[ k ][ i ] = val;
            }
        }
        win2write = new AudioSamplesWindow( samples2write, page_size + page_size / 2, page_size + 1, dataSource.get_channel_number() );

        Assert.assertEquals( cache.getCache().getCache_access().size(), 0 );
        winDS = dataSource.get_samples( win2write.get_first_sample_index(), win2write.get_length() );
        winC = cache.get_samples( win2write.get_first_sample_index(), win2write.get_length() );
        Assert.assertEquals( cache.getCache().getCache_access().size(), 2 );
        for( k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( winC.getSamples()[ k ], winDS.getSamples()[ k ], Math.pow( 2, -14 ) );
        }

        cache.put_samples( win2write );
        Assert.assertEquals( cache.getCache().getCache_access().size(), 2 );

        win = winDS;
        winDS = dataSource.get_samples( win2write.get_first_sample_index(), win2write.get_length() );
        winC = cache.get_samples( win2write.get_first_sample_index(), win2write.get_length() );

        for( k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( winC.getSamples()[ k ], win2write.getSamples()[ k ], Math.pow( 2, -14 ) );
            Assert.assertArrayEquals( win.getSamples()[ k ], winDS.getSamples()[ k ], Math.pow( 2, -14 ) );
        }

        cache.flushAll();
        winDS = dataSource.get_samples( win2write.get_first_sample_index(), win2write.get_length() );
        for( k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( win2write.getSamples()[ k ], winDS.getSamples()[ k ], Math.pow( 2, -14 ) );
        }
    }

    @Test
    public void put_samples_test_2() throws Exception
    {
        nr_of_pages = 2;
        int i, k;
        double val;
        double[][] samples2write = new double[ dataSource.get_channel_number() ][ page_size + 1 ];
        CachedAudioDataSource cache = new CachedAudioDataSource( dataSource, nr_of_pages * page_size, page_size );
        AudioSamplesWindow winDS_orig, winDS, win2write, winC;

        rewrite_temp_file();
        cache.setDataSource( dataSource );
        for( k = 0; k < dataSource.get_channel_number(); k++ )
        {
            val = new Random().nextDouble() - 0.5;
            for( i = 0; i < page_size + 1; i++ )
            {
                samples2write[ k ][ i ] = val;
            }
        }
        win2write = new AudioSamplesWindow( samples2write, page_size + page_size / 2, page_size + 1, dataSource.get_channel_number() );

        Assert.assertEquals( cache.getCache().getCache_access().size(), 0 );
        winDS = dataSource.get_samples( win2write.get_first_sample_index(), win2write.get_length() );
        winC = cache.get_samples( win2write.get_first_sample_index(), win2write.get_length() );
        Assert.assertEquals( cache.getCache().getCache_access().size(), 2 );
        for( k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( winC.getSamples()[ k ], winDS.getSamples()[ k ], Math.pow( 2, -14 ) );
        }

        cache.put_samples( win2write );
        Assert.assertEquals( cache.getCache().getCache_access().size(), 2 );

        winDS_orig = winDS;
        winDS = dataSource.get_samples( win2write.get_first_sample_index(), win2write.get_length() );
        winC = cache.get_samples( win2write.get_first_sample_index(), win2write.get_length() );

        for( k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( winC.getSamples()[ k ], win2write.getSamples()[ k ], Math.pow( 2, -14 ) );
            Assert.assertArrayEquals( winDS_orig.getSamples()[ k ], winDS.getSamples()[ k ], Math.pow( 2, -14 ) );
        }
        Assert.assertEquals( cache.getCache().getCache_access().get( 0 ).get_first_sample_index(), page_size * 2 );
        Assert.assertEquals( cache.getCache().getCache_access().get( 1 ).get_first_sample_index(), page_size );

        samples2write = new double[][]{ { 0 }, { 0 } };
        cache.put_samples( new AudioSamplesWindow( samples2write, 0, 1, dataSource.get_channel_number() ) );

        Assert.assertEquals( cache.getCache().getCache_access().size(), 2 );
        Assert.assertEquals( cache.getCache().getCache_access().get( 0 ).get_first_sample_index(), 0 );
        Assert.assertEquals( cache.getCache().getCache_access().get( 1 ).get_first_sample_index(), page_size * 2 );

        winDS = dataSource.get_samples( win2write.get_first_sample_index(), page_size * 2 - win2write.get_first_sample_index() );
        boolean equals = true;
        for( k = 0; k < cache.get_channel_number(); k++ )
        {
            for( i = winDS.get_first_sample_index(); i < winDS.get_after_last_sample_index(); i++ )
            {
                equals = equals && ( Math.abs( winDS.getSample( i, k ) - win2write.getSample( i, k ) ) < Math.pow( 2, -14 ) );
            }
        }
        Assert.assertTrue( equals );

        winDS = dataSource.get_samples( page_size * 2, win2write.get_after_last_sample_index() - page_size * 2 );
        equals = true;
        for( k = 0; k < cache.get_channel_number(); k++ )
        {
            for( i = winDS.get_first_sample_index(); i < winDS.get_after_last_sample_index(); i++ )
            {
                equals = equals && ( Math.abs( winDS.getSample( i, k ) - winDS_orig.getSample( i, k ) ) < Math.pow( 2, -14 ) );
            }
        }
        Assert.assertTrue( equals );

        cache.flushAll();
        winDS = dataSource.get_samples( win2write.get_first_sample_index(), win2write.get_length() );
        for( k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( win2write.getSamples()[ k ], winDS.getSamples()[ k ], Math.pow( 2, -14 ) );
        }
    }

    @Test
    public void put_samples_test_3() throws Exception
    {
        nr_of_pages = 2;
        int i, k;
        double val;
        double[][] samples2write = new double[ dataSource.get_channel_number() ][ page_size + 1 ];
        CachedAudioDataSource cache = new CachedAudioDataSource( dataSource, nr_of_pages * page_size, page_size );
        AudioSamplesWindow winDS_orig, winDS, win2write, winC;

        rewrite_temp_file();
        cache.setDataSource( dataSource );
        for( k = 0; k < dataSource.get_channel_number(); k++ )
        {
            val = new Random().nextDouble() - 0.5;
            for( i = 0; i < page_size + 1; i++ )
            {
                samples2write[ k ][ i ] = val;
            }
        }
        win2write = new AudioSamplesWindow( samples2write, ( dataSource.get_sample_number() - 1 ) / page_size * page_size, page_size + 1, dataSource.get_channel_number() );
        winC = cache.get_samples( win2write.get_first_sample_index(),win2write.get_length() );
        winDS_orig = dataSource.get_samples( win2write.get_first_sample_index(),win2write.get_length() );

        Assert.assertEquals( winC.getInterval(), new Interval( win2write.get_first_sample_index(), dataSource.get_sample_number(), false ) );
        Assert.assertEquals( winC.getInterval(), winDS_orig.getInterval() );
        Assert.assertEquals( cache.getCache().getCache_access().size(), 1 );
        Assert.assertEquals( cache.getCache().getCache_access().get( 0 ).get_first_sample_index(), ( dataSource.get_sample_number() - 1 ) / page_size * page_size );
        Assert.assertEquals( cache.getCache().getCache_access().get( 0 ).get_capacity(), page_size );

        cache.put_samples( win2write );
        winC = cache.get_samples( win2write.get_first_sample_index(), win2write.get_length() );
        winDS = dataSource.get_samples( win2write.get_first_sample_index(), win2write.get_length() );

        Assert.assertEquals( cache.get_sample_number(), win2write.get_after_last_sample_index() );
        Assert.assertEquals( dataSource.get_sample_number(),winDS_orig.get_after_last_sample_index() );
        Assert.assertEquals( winC.getInterval(), win2write.getInterval() );
        Assert.assertEquals( winDS.getInterval(), winDS_orig.getInterval() );
        Assert.assertEquals( cache.getCache().getCache_access().size(), 2 );
        Assert.assertEquals( cache.getCache().getCache_access().get( 0 ).getInterval(), new Interval( winDS_orig.get_after_last_sample_index(), win2write.get_after_last_sample_index(), false ) );
        Assert.assertEquals( cache.getCache().getCache_access().get( 0 ).get_capacity(), page_size );

        for( k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( winC.getSamples()[ k ], win2write.getSamples()[ k ], Math.pow( 2, -14 ) );
            Assert.assertArrayEquals( winDS.getSamples()[ k ], winDS_orig.getSamples()[ k ], Math.pow( 2, -14 ) );
        }

        cache.flushAll();

        winDS = dataSource.get_samples( win2write.get_first_sample_index(), win2write.get_length() );

        Assert.assertEquals( cache.get_sample_number(), win2write.get_after_last_sample_index() );
        Assert.assertEquals( dataSource.get_sample_number(),win2write.get_after_last_sample_index() );
        Assert.assertEquals( winC.getInterval(), win2write.getInterval() );
        Assert.assertEquals( winDS.getInterval(), win2write.getInterval() );

        for( k = 0; k < cache.get_channel_number(); k++ )
        {
            Assert.assertArrayEquals( winDS.getSamples()[ k ], win2write.getSamples()[ k ], Math.pow( 2, -14 ) );
        }
    }

    @Test
    public void put_samples_test_4() throws Exception
    {
        nr_of_pages = 0;
        rewrite_temp_file();
        nr_of_pages = 5;
        int i, k;
        double val;
        double[][] samples2write = new double[ dataSource.get_channel_number() ][ page_size + 1 ];
        CachedAudioDataSource cache = new CachedAudioDataSource( dataSource, nr_of_pages * page_size, page_size );
        AudioSamplesWindow winDS_orig, winDS, win2write, winC;

        cache.setDataSource( dataSource );

        for( k = 0; k < dataSource.get_channel_number(); k++ )
        {
            val = new Random().nextDouble() - 0.5;
            for( i = 0; i < page_size + 1; i++ )
            {
                samples2write[ k ][ i ] = val;
            }
        }
        win2write = new AudioSamplesWindow( samples2write, page_size, page_size + 1, dataSource.get_channel_number() );

        Assert.assertEquals( dataSource.get_sample_number(), 0 );
        Assert.assertEquals( cache.get_sample_number(), 0 );

        cache.put_samples( win2write );

        Assert.assertEquals( cache.get_sample_number(), 2 * page_size + 1 );
        Assert.assertEquals( dataSource.get_sample_number(), 0 );

        winC = cache.get_samples( 0, cache.get_sample_number() );
        Assert.assertEquals( winC.get_length(), 2 * page_size + 1 );

        boolean equals = true;
        for( k = 0; k < dataSource.get_channel_number(); k++ )
        {
            for( i = 0; i < page_size; i++ )
            {
                equals = equals && ( winC.getSample( i, k ) == 0 );
            }
            for( i = page_size; i < page_size * 2 + 1; i++ )
            {
                equals = equals && ( winC.getSample( i, k ) == win2write.getSample( i, k ) );
            }
        }
        Assert.assertTrue( equals );

        cache.flushAll();

        winDS = dataSource.get_samples( 0, dataSource.get_sample_number() );
        Assert.assertEquals( dataSource.get_sample_number(), 2 * page_size + 1 );

        for( k = 0; k < dataSource.get_channel_number(); k++ )
        {
            for( i = 0; i < page_size; i++ )
            {
                equals = equals && ( winDS.getSample( i, k ) == 0 );
            }
            for( i = page_size; i < page_size * 2 + 1; i++ )
            {
                equals = equals && ( Math.abs( winDS.getSample( i, k ) - win2write.getSample( i, k ) ) < 6e-5 );
            }
        }
        Assert.assertTrue( equals );
    }
}