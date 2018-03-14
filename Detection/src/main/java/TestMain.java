import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.ADCache.CachedAudioDataSource;
import AudioDataSource.ADS_Utils;
import AudioDataSource.Cached_ADS_Manager;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.FileADS.WAVFileAudioSource;
import AudioDataSource.VersionedADS.VersionedAudioDataSource;
import ProjectStatics.ProjectStatics;
import SignalProcessing.Effects.Equalizer;
import SignalProcessing.Effects.FIR_Filter;
import SignalProcessing.Effects.Repair_in_memory;
import SignalProcessing.Filters.FIR;
import SignalProcessing.Interpolation.Interpolator;
import SignalProcessing.Interpolation.LinearInterpolator;
import SignalProcessing.Windowing.Windowing;
import Utils.Interval;

import static Utils.Utils.plot_in_matlab;

/**
 * Created by Alex on 08.09.2017.
 */
public class TestMain {

    public static void main2(String args[])
    {
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\proj_files\\out.wav", 2, 44100, 2 );
            AudioSamplesWindow win;
            double buffer[][] = new double[ 2 ][ 4410 ];

            CachedAudioDataSource cached = new CachedAudioDataSource( wav, 4410, 1024 );

            for( int i = 0; i < buffer[ 0 ].length; i++ )
            {
                for( int k = 0; k < buffer.length; k++ )
                {
                    buffer[ k ][ i ] = Math.sin( Math.PI * i / 49 );
                }
            }

            win = new AudioSamplesWindow( buffer, 0, 4410, 2 );
            cached.put_samples( win );

            win = new AudioSamplesWindow( buffer, 4410, 4410, 2 );
            cached.put_samples( win );

            win = new AudioSamplesWindow( buffer, 8820, 4410, 2 );
            cached.put_samples( win );

            for( int i = 0; i < buffer[ 0 ].length; i++ )
            {
                for( int k = 0; k < buffer.length; k++ )
                {
                    buffer[ k ][ i ] = Math.sin( Math.PI * i / 49 ) / 2;
                }
            }

            win = new AudioSamplesWindow( buffer, 0 + 4410/2, 4410, 2 );
            cached.put_samples( win );
            win = new AudioSamplesWindow( buffer, 4410 + 4410/2, 4410, 2 );
            cached.put_samples( win );

            cached.flushAll();
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();

        }
    }

    public static void main3( String args[] )
    {
        try
        {
            WAVFileAudioSource src = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\eq test.wav" );
            WAVFileAudioSource dst = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\out.wav", src.get_channel_number(), src.get_sample_rate(), src.getByte_depth() );
            CachedAudioDataSource src_cache = new CachedAudioDataSource( src, 44100, 2048 );
            CachedAudioDataSource dst_cache = new CachedAudioDataSource( dst, 44100, 2048 );

            double[] x = { 0, -48, 0, -48, 0, -48, 0, -48, 0, -48, 0, -48, 0, -48, 0, -48, 0 };
            Windowing.apply( x, x.length, ( v ) -> 1.0 / 6 );
            FIR fir = FIR.fromFreqResponse( x, x.length - 1, src.get_sample_rate(), 493 );
            Windowing.apply( fir.getFf(), fir.getFf_coeff_nr(), Windowing.Hann_window );
            Windowing.apply( fir.getFf(), fir.getFf_coeff_nr(), ( v ) -> 1.0 / ( fir.getFf_coeff_nr() ) );

            plot_in_matlab( x, 0, x.length, fir.getFf(), 0, fir.getFf_coeff_nr() );

            FIR_Filter effect = new FIR_Filter();
            effect.setFilter( fir );
            effect.setMax_chunk_size( 44100 );
            effect.apply( src_cache, dst_cache, new Interval( 0, src.get_sample_number() ) );
            dst_cache.flushAll();
            dst.close();
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
    }

    public static void main4( String args[] )
    {
        double[] in = { 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0 };
        int n = in.length;
        int m = 31;
        double[] out = new double[ m ];
        int i;

        Interpolator interpolator = new LinearInterpolator();
        try
        {
            interpolator.resize( in, n, out, m );
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }

        plot_in_matlab( in, 0, n, out, 0, m );
    }

    public static void main5( String[] args )
    {
        try
        {
            WAVFileAudioSource src = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\eq test.wav" );
            WAVFileAudioSource dst = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\out.wav", src.get_channel_number(), src.get_sample_rate(), src.getByte_depth() );
            CachedAudioDataSource src_cache = new CachedAudioDataSource( src, 44100, 2048 );
            CachedAudioDataSource dst_cache = new CachedAudioDataSource( dst, 44100, 2048 );

            double[] FR = FIR.get_RIAA_response( 512, src.get_sample_rate() );
            FIR fir = FIR.fromFreqResponse( FR, FR.length - 1, src.get_sample_rate(), 2047 );
            Windowing.apply( fir.getFf(), fir.getFf_coeff_nr(), Windowing.Hann_window );
            Windowing.apply( fir.getFf(), fir.getFf_coeff_nr(), ( v ) -> 1.0 / ( fir.getFf_coeff_nr() ) );

            plot_in_matlab( new double[]{ 0, 0 }, 0, 2, FR, 0, FR.length );

            FIR_Filter effect = new FIR_Filter();
            effect.setFilter( fir );
            effect.setMax_chunk_size( 44100 );
            effect.apply( src_cache, dst_cache, new Interval( 0, src.get_sample_number() ) );
            dst_cache.flushAll();
            dst.close();
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
    }

    public static void main6( String[] args )
    {
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\test.wav", 1, 44100, 2 );
            CachedAudioDataSource cache = new CachedAudioDataSource( wav, 44100, 2048 );
            WAVFileAudioSource out = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\out.wav", 1, 44100, 2 );

            double[] fir = { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
            double[][] samples = { { -1, -0.9, -0.8, -0.7, -0.6, -0.5, -0.4, -0.3, -0.2, -0.1, 0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1 } };
            AudioSamplesWindow win = new AudioSamplesWindow( samples, 0, samples[ 0 ].length, samples.length );
            cache.put_samples( win );

            Equalizer effect = new Equalizer();
            effect.setMax_chunk_size( 11 );
            effect.setFilter( new FIR( fir, fir.length ) );
            effect.apply( cache, out, new Interval( 0, samples[ 0 ].length, false ) );

            cache.flushAll();
            out.close();
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
    }

    public static void main7( String[] args )
    {
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\in2.wav");
            CachedAudioDataSource cache = new CachedAudioDataSource( wav, 44100, 2048 );
            WAVFileAudioSource out = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\out.wav", wav.get_channel_number(), wav.get_sample_rate(), wav.getByte_depth() );

            //Utils.copyToADS( wav, out );
            Repair_in_memory effect = new Repair_in_memory();
            effect.setWork_on_position_domain( true );
            effect.setWork_on_high_pass( true );
            effect.apply( cache, out, new Interval( 0, wav.get_sample_number() ) );

            cache.flushAll();
            out.close();
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
    }

    public static void main( String[] args )
    {
        long start_time, end_time;
        start_time = System.currentTimeMillis();
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\1 minute shit.wav");
            VersionedAudioDataSource vads = new VersionedAudioDataSource( wav.getFile_path() );
            CachedAudioDataSource cache = new CachedAudioDataSource( vads.create_new(), ProjectStatics.getDefault_cache_size(), ProjectStatics.getDefault_cache_page_size() );
            AudioSamplesWindow win;
            int i;
            for( i = 0; i < cache.get_sample_number(); )
            {
                win = cache.get_samples( i, 1 );
                //win.putSample( win.get_first_sample_index(), 0, 1 );
                cache.put_samples( win );
                i += 2048;
            }
            double[][] append = new double[ 1 ][ 441000 ];
            cache.put_samples( new AudioSamplesWindow( append, cache.get_sample_number(), append[0].length, append.length ) );

            WAVFileAudioSource out = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\out.wav", wav.get_channel_number(), wav.get_sample_rate(), wav.getByte_depth() );

            ADS_Utils.copyToADS( cache, out );

            //Cached_ADS_Manager.flush_all_caches();

            vads.dispose();

        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
        end_time = System.currentTimeMillis();
        System.out.println( "Duration: " + ( end_time - start_time ) + " ms" );
    }
}
