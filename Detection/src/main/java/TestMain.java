import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.CachedADS.CachedAudioDataSource;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.FileADS.IFileAudioDataSource;
import AudioDataSource.MemoryADS.InMemoryADS;
import Exceptions.DataSourceException;
import AudioDataSource.FileADS.WAVFileAudioSource;
import AudioDataSource.VersionedADS.VersionedAudioDataSource;
import MarkerFile.*;
import ProjectManager.*;
import SignalProcessing.Effects.*;
import SignalProcessing.Filters.Equalizer_FIR;
import SignalProcessing.Filters.FIR;
import SignalProcessing.FourierTransforms.Fourier;
import SignalProcessing.FunctionApproximation.FourierInterpolator;
import SignalProcessing.FunctionApproximation.FunctionApproximation;
import SignalProcessing.LinearPrediction.BurgMethod;
import SignalProcessing.LinearPrediction.LinearPrediction;
import Utils.*;

import java.io.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static Utils.Util_Stuff.plot_in_matlab;
import static Utils.Util_Stuff.remap_to_interval;

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
            float buffer[][] = new float[ 2 ][ 4410 ];

            CachedAudioDataSource cached = new CachedAudioDataSource( wav, 4410, 1024 );

            for( int i = 0; i < buffer[ 0 ].length; i++ )
            {
                for( int k = 0; k < buffer.length; k++ )
                {
                    buffer[ k ][ i ] = ( float )( Math.sin( Math.PI * i / 49 ) );
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
                    buffer[ k ][ i ] = ( float )( Math.sin( Math.PI * i / 49 ) / 2 );
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

    public static void main6( String[] args )
    {
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\test.wav", 1, 44100, 2 );
            CachedAudioDataSource cache = new CachedAudioDataSource( wav, 44100, 2048 );
            WAVFileAudioSource out = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\out.wav", 1, 44100, 2 );

            float[] fir = { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
            float[][] samples = { { -1, -0.9f, -0.8f, -0.7f, -0.6f, -0.5f, -0.4f, -0.3f, -0.2f, -0.1f, 0, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1 } };
            AudioSamplesWindow win = new AudioSamplesWindow( samples, 0, samples[ 0 ].length, samples.length );
            cache.put_samples( win );

            FIR_Equalizer effect = new FIR_Equalizer();
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
            effect.setWork_on_band_pass( true );
            effect.apply( cache, out, new Interval( 0, wav.get_sample_number() ) );

            cache.flushAll();
            out.close();
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
    }

    public static void main8( String[] args )
    {
        long start_time, end_time;
        start_time = System.currentTimeMillis();
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\1 minute shit.wav");
            VersionedAudioDataSource vads = new VersionedAudioDataSource( wav.getFile_path() );
            CachedAudioDataSource cache = new CachedAudioDataSource( vads.create_new(), ProjectStatics.getProject_cache_size(), ProjectStatics.getProject_cache_page_size() );
            AudioSamplesWindow win;
            int i;
            for( i = 0; i < cache.get_sample_number(); )
            {
                win = cache.get_samples( i, 1 );
                //win.putSample( win.get_first_sample_index(), 0, 1 );
                cache.put_samples( win );
                i += 2048;
            }
            float[][] append = new float[ 1 ][ 441000 ];
            cache.put_samples( new AudioSamplesWindow( append, cache.get_sample_number(), append[0].length, append.length ) );

            WAVFileAudioSource out = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\out.wav", wav.get_channel_number(), wav.get_sample_rate(), wav.getByte_depth() );

            new Copy_to_ADS().apply( cache, out, new Interval( 0, cache.get_sample_number() ) );

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

    public static void main9( String[] args )
    {
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\Beethoven - Quartet no 4 - IV frg pre RIAA.wav");
            Create_Marker_File create_marker_file = new Create_Marker_File();
            create_marker_file.setThreshold( 0.5f );
            create_marker_file.apply( wav, null, new Interval( 0, wav.get_sample_number() ) );

            ProjectManager.getMarkerFile().writeMarkingsToFile( new OutputStreamWriter( new FileOutputStream( "C:\\Users\\Alex\\Desktop\\generated_markings beet 0.5.txt" ) ) );

        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
        catch( FileNotFoundException e )
        {
            e.printStackTrace();
        }
        catch( IOException e )
        {
            e.printStackTrace();
        }
    }

    public static void main10( String[] args )
    {
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\4p riaa.wav" );
            CachedAudioDataSource cache = new CachedAudioDataSource( wav, 44100, 4096 );
            WAVFileAudioSource dest = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\probs hg.wav", wav.get_channel_number(), wav.get_sample_rate(), wav.getByte_depth() );
            Create_Probability_Graph graph = new Create_Probability_Graph();
            graph.create_graph( cache, dest );
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
    }

    public static void main11( String[] args )
    {
        Function<Float,Float> func=new Function< Float, Float >()
        {
            @Override
            public Float apply( Float aFloat )
            {
                return ( float )( Math.sin( ( aFloat + 0.125f ) * 2 * Math.PI ) * 0.5f + Math.sin( ( aFloat - 0.375f ) * 2 * Math.PI * 2 ) * 0.25f + Math.sin( ( aFloat + 0.05f ) * 2 * Math.PI * 8 ) * 0.33f );
            }
        };

        int full_n = 500;
        float[] full_xs = new float[ full_n ];
        float[] full_ys = new float[ full_n ];

        int orig_n = 32;
        float[] orig_xs = new float[ orig_n ];
        float[] orig_ys = new float[ orig_n ];

        int approx_n = 200;
        float[] approx_xs = new float[ approx_n ];
        float[] approx_ys = new float[ approx_n ];

        int i;
        for( i = 0; i < full_n; i++ )
        {
            full_xs[ i ] = i * ( 1.0f / full_n );
            full_ys[ i ] = func.apply( full_xs[ i ] );
        }

        for( i = 0; i < orig_n; i++ )
        {
            orig_xs[ i ] = i * ( 1.0f / orig_n );
            orig_ys[ i ] = func.apply( orig_xs[ i ] );
        }

        for( i = 0; i < approx_n; i++ )
        {
            approx_xs[ i ] = i * ( 1.0f / approx_n );
        }

        FunctionApproximation fa = new FourierInterpolator();
        try
        {
            fa.prepare( orig_xs, orig_ys, orig_n );
            fa.get_values( approx_xs, approx_ys, approx_n );
            plot_in_matlab( orig_xs, orig_ys, orig_n, approx_xs, approx_ys, approx_n );
            plot_in_matlab( full_xs, full_ys, full_n, approx_xs, approx_ys, approx_n );
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }

    }

    public static void main12( String[] args )
    {
        try
        {
            //WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\test.wav" );
            //WAVFileAudioSource dest = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\dest.wav", wav.get_channel_number(), wav.get_sample_rate(), 2 );

            float[] freqs;
            float[] resps;
            MyPair< float[], float[] > r = FIR.get_RIAA_response();
            freqs = r.getLeft();
            resps = r.getRight();
            plot_in_matlab( freqs, resps, freqs.length );

            FIR fir = FIR.fromFreqResponse( freqs, resps, freqs.length, 44100, 511 );
            plot_in_matlab( fir.getFf(), fir.getFf_coeff_nr() );
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }

    }

    public static void main13( String[] args )
    {
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "D:\\training sets\\resampled\\results\\chopin\\Chopin - Etude op. 25 no. 11 inv riaa.wav" );
            WAVFileAudioSource dest = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\dest.wav", wav.get_channel_number(), wav.get_sample_rate(), wav.getByte_depth() );
            new Copy_to_ADS().apply( wav, dest, new Interval( 0, wav.get_sample_number() ) );
            long st = System.currentTimeMillis();
            Interval r = new Interval( 2225535, 28 );
            float ratio = 16;
            Repair_One repairOne = new Repair_One();
            repairOne.set_fetch_ratio( ratio );
            ArrayList< Integer > ch = new ArrayList<>();
            ch.add( 0 );
            repairOne.setAffected_channels( ch );
            repairOne.apply( wav, dest, r );
            System.out.println( "Total time: " + ( System.currentTimeMillis() - st ) + "ms " );
            wav.close();
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
    }

    public static void main14( String[] args )
    {
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\4p.wav" );
            WAVFileAudioSource dest = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\predict.wav", wav.get_channel_number(), wav.get_sample_rate(), wav.getByte_depth() );
            new Copy_to_ADS().apply( wav, dest, new Interval( 0, wav.get_sample_number() ) );
            long st = System.currentTimeMillis();
            int fit_start = 85000;
            int prediction_length = 44100 * 10;
            AudioSamplesWindow win = wav.get_samples( fit_start, wav.get_sample_number() - fit_start );
            for( int k = 0; k < win.get_channel_number(); k++ )
            {
                BurgMethod burgMethod = new BurgMethod( win.getSamples()[ k ], 0, win.get_length(), win.get_length() - 1 );
                System.out.println( "Calculated coeffs" );
                LinearPrediction lp = new LinearPrediction( burgMethod.get_coeffs(), burgMethod.get_nr_coeffs() );
                float samples[] = Arrays.copyOf( win.getSamples()[ k ], prediction_length + win.get_length() );
                //lp.predict_forward( samples, win.get_length(), samples.length );
                System.out.println( "Prediction done" );
                win.getSamples()[ k ] = samples;
            }
            win.set_length( win.get_length() + prediction_length );
            dest.put_samples( win );
            System.out.println( "Total time: " + ( System.currentTimeMillis() - st ) + "ms " );
            dest.close();
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
    }

    public static void main15( String[] args )
    {
        try
        {
            ProjectManager.lock_access();
            WAVFileAudioSource src = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\signal.wav" );
            WAVFileAudioSource dst_FFT = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\fft.wav", src.get_channel_number(), src.get_sample_rate(), src.getByte_depth() );
            WAVFileAudioSource dst_FIR = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\fir.wav", src.get_channel_number(), src.get_sample_rate(), src.getByte_depth() );

            CachedAudioDataSource src_cache = new CachedAudioDataSource( src, 44100 * 10, 44100 );
            CachedAudioDataSource fft_cache = new CachedAudioDataSource( dst_FFT, 44100 * 10, 44100 );
            CachedAudioDataSource fir_cache = new CachedAudioDataSource( dst_FIR, 44100 * 10, 44100 );

            long start_ms;
            int filter_length = ( int )Math.pow( 2, 5 );

            FFT_Equalizer filter = new FFT_Equalizer();
            FIR_Equalizer fir_filter = new FIR_Equalizer();
            FIR fir = FIR.fromFreqResponse( FIR.get_RIAA_response().getLeft(), FIR.get_RIAA_response().getRight(), FIR.get_RIAA_response().getLeft().length, src.get_sample_rate(), filter_length - 1 );
            filter.setFilter( fir );
            fir_filter.setFilter( fir );
            filter.setFFT_length( filter_length );
            fir_filter.setMax_chunk_size( 44100 );

            start_ms = System.currentTimeMillis();
            System.out.println( "FFT:" );
            filter.apply( src_cache, fft_cache, new Interval( filter_length / 2, src.get_sample_number() - filter_length / 2 ) );
            System.out.println( ( System.currentTimeMillis() - start_ms ) + " ms" );
/*
            start_ms = System.currentTimeMillis();
            System.out.println( "FIR:" );
            fir_filter.apply( src_cache, fir_cache, new Interval( filter_length / 2, src.get_sample_number() - filter_length / 2 ) );
            System.out.println( ( System.currentTimeMillis() - start_ms ) + " ms" );
*/
            System.out.println( "Closing..." );

            src_cache.close();
            fft_cache.close();
            fir_cache.close();
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
        finally
        {
            ProjectManager.release_access();
        }
    }

    public static void main16( String[] args )
    {
        try
        {
            Interval orig_interval = new Interval( 0, 251238336 );
            Interval new_interval = new Interval( -231380243, 251238336 );
            ProjectManager.lock_access();
            System.out.println( "Reading" );
            ProjectManager.add_from_marker_file( "C:\\Users\\Alex\\Desktop\\marking alfred toccata.txt" );
            List< Marking > markings = ProjectManager.getMarkerFile().get_all_markings( orig_interval );
            MarkerFile new_markerfile = new MarkerFile();
            System.out.println( "Processing" );
            for( Marking m : markings )
            {
                new_markerfile.addMark( remap_to_interval( m.get_first_marked_sample(), orig_interval.l, orig_interval.r, new_interval.l, new_interval.r ), remap_to_interval( m.get_last_marked_sample(), orig_interval.l, orig_interval.r, new_interval.l, new_interval.r ), m.getChannel() );
            }
            System.out.println( "Writing" );
            new_markerfile.writeMarkingsToFile( new FileWriter( "C:\\Users\\Alex\\Desktop\\marking alfred toccata r.txt" ) );
            System.out.println( "Done" );
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
        catch( ParseException e )
        {
            e.printStackTrace();
        }
        catch( FileNotFoundException e )
        {
            e.printStackTrace();
        }
        catch( IOException e )
        {
            e.printStackTrace();
        }
    }

    public static void main17( String[] args )
    {
        try
        {
            float width = 5;
            float length = 19;
            IFileAudioDataSource src1 = FileAudioSourceFactory.fromFile( "C:\\Users\\Alex\\Desktop\\impulse.wav" );
            CachedAudioDataSource src = new CachedAudioDataSource( src1, 100000, 10000 );
            IFileAudioDataSource dst = FileAudioSourceFactory.createFile( "C:\\Users\\Alex\\Desktop\\retrack invriia " + String.format( "%.1f", length ) + "um l " + String.format( "%.1f", width ) + "um w.wav", src.get_channel_number(), src.get_sample_rate(), 2 );
            //InMemoryADS dst = new InMemoryADS( src.get_sample_number(), src.get_channel_number(), src.get_sample_rate() );

            double ch_dif, min_ch_dif = 100000;
            float min_w = -1, min_l = -1;
            Groove_Retracking eff = new Groove_Retracking();
/*
            for( width = 5; width <= 20; width += 2 )
            {
                for( length = 5; length <= 20; length += 2 )
                {
                    System.out.println( width + ", " + length );
                    eff.setResample_rate_factor( 2 );
                    eff.setDrop_size( 4 );
                    eff.setStylus_width( width );
                    eff.setStylus_length( length );
                    eff.setChunk_size( 32 );
                    eff.setUpside_down( true );
                    eff.setGroove_max_ampl_um( 100 );

                    eff.apply( src, dst, new Interval( 0, src.get_sample_number() ) );
                    ch_dif = 0;
                    AudioSamplesWindow win = dst.get_samples( 0, dst.get_sample_number() );
                    for( int i = 0; i < win.get_length(); i++ )
                    {
                        ch_dif += Math.abs( win.getSamples()[ 1 ][ i ] - win.getSamples()[ 0 ][ i ] );
                    }
                    if( ch_dif / win.get_length() <= min_ch_dif )
                    {
                        min_w = width;
                        min_l = length;
                        min_ch_dif = ch_dif / win.get_length();
                    }
                }
            }

            System.out.println( "W: " + min_w + "; L: " + min_l +"; Dif: " + min_ch_dif);
*/

            eff.setResample_rate_factor( 2 );
            eff.setDrop_size( 4 );
            eff.setStylus_width( width );
            eff.setStylus_length( length );
            eff.setChunk_size( 32 );
            eff.setUpside_down( true );
            eff.setGroove_max_ampl_um( 100 );
            eff.apply( src, dst, new Interval( 0, src.get_sample_number() ) );
            dst.close();

        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
    }

    public static void main18( String[] args )
    {
        ProjectManager.lock_access();
        String filePath = "D:\\marked recordings\\resampled\\results\\andries\\andries - dracula blues inv riaa.wav";
        Interval interval = new Interval( ( 1 * 60 + 24 ) * 96000, ( 2 * 60 + 3 ) * 96000, false );
        String dest = "D:\\datasets\\andries 1 24 - 2 03 train set.bin";
        try
        {
            ProjectManager.add_from_marker_file( "D:\\marked recordings\\resampled\\andries - dracula blues mark 0,010 96000.txt" );
            IFileAudioDataSource file = FileAudioSourceFactory.fromFile( filePath );
            DataSetGenerator.generate( file, interval, dest, 64, 0.001f, 0, 0.75f );
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
        catch( IOException e )
        {
            e.printStackTrace();
        }
        catch( ParseException e )
        {
            e.printStackTrace();
        }
        finally
        {
            ProjectManager.release_access();
        }
    }

    public static void main( String[] args )
    {
        main15( args );
    }
}
