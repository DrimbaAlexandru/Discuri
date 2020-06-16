import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.CachedADS.CachedAudioDataSource;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.FileADS.IFileAudioDataSource;
import Utils.DataTypes.Interval;
import Utils.DataTypes.MyPair;
import Utils.Exceptions.DataSourceException;
import AudioDataSource.FileADS.WAVFileAudioSource;
import AudioDataSource.VersionedADS.VersionedAudioDataSource;
import Utils.DataStructures.MarkerFile.*;
import ProjectManager.*;
import Effects.*;
import SignalProcessing.Filters.FIR;
import SignalProcessing.FunctionApproximation.FourierInterpolator;
import SignalProcessing.FunctionApproximation.FunctionApproximation;
import SignalProcessing.LinearPrediction.BurgMethod;
import SignalProcessing.LinearPrediction.LinearPrediction;
import Effects.SampleClassifier.AIDamageRecognition;
import Effects.SampleClassifier.Create_Probability_Graph;
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

    public static void main8( String[] args )
    {
        long start_time, end_time;
        start_time = System.currentTimeMillis();
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\1 minute shit.wav");
            VersionedAudioDataSource vads = new VersionedAudioDataSource( wav.getFile_path() );
            CachedAudioDataSource cache = new CachedAudioDataSource( vads.create_next_version(), ProjectStatics.getProject_cache_size(), ProjectStatics.getProject_cache_page_size() );
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
            AIDamageRecognition create_marker_file = new AIDamageRecognition();
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

            CachedAudioDataSource src_cache = new CachedAudioDataSource( src, src.get_sample_number(), src.get_sample_rate() );
            CachedAudioDataSource fft_cache = new CachedAudioDataSource( dst_FFT, src.get_sample_number(), src.get_sample_rate() );
            CachedAudioDataSource fir_cache = new CachedAudioDataSource( dst_FIR, src.get_sample_number(), src.get_sample_rate() );

            long start_ms;
            long fir_total_duration;
            long fft_total_duration;
            int i;
            int filter_length;

            src_cache.get_samples( 0, src.get_sample_number() );

            for( filter_length = 8; filter_length <= 1024*16; filter_length *= 2 )
            {
                fir_total_duration = 0;
                fft_total_duration = 0;

                System.out.println( "Filter length: " + filter_length );

                FFT_Equalizer fft_filter = new FFT_Equalizer();
                FIR_Equalizer fir_filter = new FIR_Equalizer();
                //FIR fir = FIR.fromFreqResponse( FIR.get_RIAA_response().getLeft(), FIR.get_RIAA_response().getRight(), FIR.get_RIAA_response().getLeft().length, src.get_sample_rate(), filter_length - 1 );
                FIR fir = FIR.fromFreqResponse( FIR.get_flat_response().getLeft(), FIR.get_flat_response().getRight(), FIR.get_flat_response().getLeft().length, src.get_sample_rate(), filter_length - 1 );

                fft_filter.setFilter( fir );
                // fft_filter.setFFT_length( Util_Stuff.next_power_of_two( fir.getFf_coeff_nr() ) * 2 );

                fir_filter.setFilter( fir );
                fir_filter.setMax_chunk_size( filter_length * 2 );

                System.out.println( "FFT:" );
                for( i = 0; i < 10; i++ )
                {
                    start_ms = System.currentTimeMillis();
                    fft_filter.apply( src_cache, fft_cache, new Interval( 0, src_cache.get_sample_number() ) );
                    fft_total_duration += ( System.currentTimeMillis() - start_ms );
                }
                System.out.println( fft_total_duration / i + " ms" );

                System.out.println( "FIR:" );
                for( i = 0; i < 10; i++ )
                {
                    start_ms = System.currentTimeMillis();
                    fir_filter.apply( src_cache, fir_cache, new Interval( 0, src.get_sample_number() ) );
                    fir_total_duration += ( System.currentTimeMillis() - start_ms );
                }
                System.out.println( fir_total_duration / i + " ms" );
            }

            fft_cache.flushAll();
            fir_cache.flushAll();

            src_cache.close();
            fft_cache.close();
            fir_cache.close();
        }
        catch( Exception e )
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
        class markedFile
        {
            public String audio_path;
            public String marker_path;
            public String dest_path;
            public float non_marked_prob;
            public float marked_prob;
            public float doubling_prob;

            public markedFile( String a, String m, String d, float nm, float mp, float dp )
            {
                audio_path = a;
                marker_path = m;
                dest_path = d;
                non_marked_prob = nm;
                marked_prob = mp;
                doubling_prob = dp;
            }
        }

        markedFile files[] = new markedFile[]
        {
        new markedFile( "Bach triosonata 3.wav","Bach triosonata 3 mark diff s 4 m 4 0.2000 avg 18.txt", "Bach triosonata.bin",  0.00033f, 0.02f,  0.0f ),
        new markedFile( "Boccherini cello.wav", "Boccherini cello mark diff s 2 m 4 0.1000 avg 12.txt",  "Boccherini cello.bin", 0.0025f,  0.15f,  0.0f ),
        new markedFile( "les preludes.wav",     "les preludes mark diff s 4 m 1 0.1000 avg 17.txt",      "les preludes.bin",     0.01f,    0.03f,  0.0f ),
        new markedFile( "Marie, osanca me.wav", "Marie, osanca me mark diff s 3 m 1 0.1000 avg 21.txt",  "petreusi.bin",         0.01f,    0.0125f,0.0f ),
        new markedFile( "mendelssohn.wav",      "mendelssohn mark diff s 3 m 1 0.1500 avg 19.txt",       "mendelssohn.bin",      0.01f,    0.0175f,0.0f ),
        new markedFile( "phoenix.wav",          "phoenix mark diff s 3 m 5 0.1000 avg 15.txt",           "phoenix.bin",          0.005f,   0.033f, 0.0f ),
        new markedFile( "vivaldi.wav",          "vivaldi mark diff s 3 m 1 0.1000 avg 12.txt",           "vivaldi.bin",          0.0025f,  0.075f, 0.0f ),

        new markedFile( "bagpipe.wav",       null, "neg_bagpipe.bin",    0.05f,  0.0f, 0.0f ),
        new markedFile( "bathory.wav",       null, "neg_bathory.bin",    0.1f,   0.0f, 0.0f ),
        new markedFile( "cargo.wav",         null, "neg_cargo.bin",      0.1f,   0.0f, 0.0f ),
        new markedFile( "infinitum.wav",     null, "neg_infinitum.bin",  0.1f,   0.0f, 0.0f ),
        new markedFile( "ioan bocsa.wav",    null, "neg_ioan bocsa.bin", 0.05f,  0.0f, 0.0f ),
        new markedFile( "prokofiev 2.wav",   null, "neg_prokofiev.bin",  0.05f,  0.0f, 0.0f ),
        new markedFile( "trumpet.wav",       null, "neg_trumpet.bin",    0.075f, 0.0f, 0.0f ),

        new markedFile( "andries - dracula blues.wav",                                 "andries - dracula blues mark diff s 2 m 4 0.1750 avg 16.txt",                                 "andries.bin",           0.0025f, 0.033f, 0.0f ),
        new markedFile( "Beethoven - Quartet no 4 - IV.wav",                           "Beethoven - Quartet no 4 - IV mark diff s 3 m 1 0.1000 avg 15.txt",                           "beethoven quartet.bin", 0.002f,  0.025f, 0.0f ),
        new markedFile( "Chopin - Etude op. 25 no. 11.wav",                            "Chopin - Etude op. 25 no. 11 mark diff s 3 m 5 0.1000 avg 11.txt",                            "chopin.bin",            0.0005f, 0.066f, 0.0f ),
        new markedFile( "dvorak 4th symph fin.wav",                                    "dvorak 4th symph fin mark diff s 3 m 1 0.1000 avg 12.txt",                                    "dvorak.bin",            0.0025f, 0.033f, 0.0f ),
        new markedFile( "Enescu - Rapsodia romana nr. 2 in re major op. 11 nr. 2.wav", "Enescu - Rapsodia romana nr. 2 in re major op. 11 nr. 2 mark diff s 4 m 1 0.1000 avg 16.txt", "enescu.bin",            0.0015f, 0.0125f,0.0f ),
        new markedFile( "Shostakovich - Simfoniya nr. 10 2 chast.wav",                 "Shostakovich - Simfoniya nr. 10 2 chast mark diff s 3 m 1 0.1000 avg 25.txt",                 "shostakovich.bin",      0.01f,   0.025f, 0.0f ),
        };

        ProjectManager.lock_access();
        String wav_base_path="e:\\datasets\\inv riaa\\";
        String marking_base_path="e:\\datasets\\inv riaa\\markings\\";
        String dest_base_path="e:\\datasets\\257-1 reduced\\";

        float master_nonmarked = 0.1f;
        float master_marked = 0.2f;
        float master_doubling_prob = 0.0f;
        int side_grab = 64;
        int outputs = 1;

        DataSetGenerator dse = new DataSetGenerator( side_grab * 2 + outputs, outputs, side_grab );

        try
        {
            for( markedFile file : files )
            {
                ProjectManager.clear_all_markings();
                if( file.marker_path != null )
                {
                    ProjectManager.add_from_marker_file( marking_base_path + file.marker_path );
                }
                IFileAudioDataSource srcfile = FileAudioSourceFactory.fromFile( wav_base_path + file.audio_path );

                dse.generate( srcfile,
                              new Interval( 0, srcfile.get_sample_number(), false ),
                              dest_base_path + file.dest_path,
                              side_grab / 4,
                              file.non_marked_prob * master_nonmarked,
                              file.doubling_prob + master_doubling_prob,
                              1.0f - ( file.marked_prob * master_marked ) );
            }

            dse.write_final_results();
        }
        catch( DataSourceException | IOException | ParseException e )
        {
            e.printStackTrace();
        }
        finally
        {
            ProjectManager.release_access();
        }
    }

    public static void main19( String[] args )
    {
        ProjectManager.lock_access();
        String base_path = "e:\\datasets\\inv riaa\\";
        String filename = "Beethoven - Quartet no 4 - IV mark diff";
        try
        {
            WAVFileAudioSource src = new WAVFileAudioSource( base_path + filename + ".wav" );
            WAVFileAudioSource dest = new WAVFileAudioSource( base_path + filename + " mvg avg.wav", src.get_channel_number(), src.get_sample_rate(), src.getByte_depth() );
            CachedAudioDataSource dest_cache = new CachedAudioDataSource( dest, 96000, 48000 );

            MarkerFileGenerator generator = new MarkerFileGenerator();
            generator.setDest_path( base_path + filename );
            generator.setAbs_threshold( 0.005f );
            generator.setDuplicate_L_to_R( false );
            generator.setSide_extend( 3 );
            generator.setMin_marking_spacing( 1 );
            generator.setSpike_threshold( 0.10f );
            generator.apply( src, dest_cache, new Interval( 0, src.get_sample_number() ) );

            dest_cache.flushAll();
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

    public static void main( String[] args )
    {
        main19( args );
    }
}
