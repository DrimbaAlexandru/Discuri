import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.CachedADS.CachedAudioDataSource;
import Exceptions.DataSourceException;
import AudioDataSource.FileADS.WAVFileAudioSource;
import AudioDataSource.VersionedADS.VersionedAudioDataSource;
import MarkerFile.*;
import ProjectManager.*;
import SignalProcessing.Effects.*;
import SignalProcessing.Filters.FIR;
import SignalProcessing.FunctionApproximation.FourierInterpolator;
import SignalProcessing.FunctionApproximation.FunctionApproximation;
import SignalProcessing.LinearPrediction.BurgMethod;
import SignalProcessing.LinearPrediction.LinearPrediction;
import Utils.Interval;

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
            double[][] append = new double[ 1 ][ 441000 ];
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
            create_marker_file.setThreshold( 0.5 );
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
        Function<Double,Double> func=new Function< Double, Double >()
        {
            @Override
            public Double apply( Double aDouble )
            {
                return Math.sin( ( aDouble + 0.125 ) * 2 * Math.PI ) * 0.5 + Math.sin( ( aDouble - 0.375 ) * 2 * Math.PI * 2 ) * 0.25 + Math.sin( ( aDouble + 0.05 ) * 2 * Math.PI * 8 ) * 0.33;
            }
        };

        int full_n = 500;
        double[] full_xs = new double[ full_n ];
        double[] full_ys = new double[ full_n ];

        int orig_n = 32;
        double[] orig_xs = new double[ orig_n ];
        double[] orig_ys = new double[ orig_n ];

        int approx_n = 200;
        double[] approx_xs = new double[ approx_n ];
        double[] approx_ys = new double[ approx_n ];

        int i;
        for( i = 0; i < full_n; i++ )
        {
            full_xs[ i ] = i * ( 1.0 / full_n );
            full_ys[ i ] = func.apply( full_xs[ i ] );
        }

        for( i = 0; i < orig_n; i++ )
        {
            orig_xs[ i ] = i * ( 1.0 / orig_n );
            orig_ys[ i ] = func.apply( orig_xs[ i ] );
        }

        for( i = 0; i < approx_n; i++ )
        {
            approx_xs[ i ] = i * ( 1.0 / approx_n );
        }

        FunctionApproximation fa = new FourierInterpolator();
        try
        {
            fa.prepare( orig_xs, orig_ys, orig_n );
            fa.get_values( null, approx_ys, approx_n );
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
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\test.wav" );
            WAVFileAudioSource dest = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\dest.wav", wav.get_channel_number(), wav.get_sample_rate(), 2 );

            int nr_freq = 500;
            double[] freqs = new double[ nr_freq ];
            double[] resps = new double[ nr_freq ];
            for( int i = 0; i < nr_freq; i++ )
            {
                freqs[ i ] = i * wav.get_sample_rate() / nr_freq;
            }

            FIR.add_pass_cut_freq_resp( freqs, resps, nr_freq, 2000, 0, 0 );
            //plot_in_matlab( freqs, resps, nr_freq );

            FIR fir = FIR.fromFreqResponse( freqs, resps, nr_freq, wav.get_sample_rate(), 2047 );
            Equalizer effect = new Equalizer();
            effect.setMax_chunk_size( 100000 );
            effect.setFilter( fir );
            effect.apply( wav, dest, new Interval( 0, wav.get_sample_number() ) );
            dest.close();
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
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\test.wav" );
            WAVFileAudioSource dest = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\dest.wav", wav.get_channel_number(), wav.get_sample_rate(), wav.getByte_depth() );
            new Copy_to_ADS().apply( wav, dest, new Interval( 0, wav.get_sample_number() ) );
            long st = System.currentTimeMillis();
            Interval r = new Interval( 20000, 1000 );
            float ratio = 20;
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
                double samples[] = Arrays.copyOf( win.getSamples()[ k ], prediction_length + win.get_length() );
                lp.predict_forward( samples, win.get_length(), samples.length );
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
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\chopin valtz op posth inv riaa.wav" );
            CachedAudioDataSource dest = new CachedAudioDataSource( new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\ch 2000 500 var thresh 8.wav", wav.get_channel_number(), wav.get_sample_rate(), wav.getByte_depth() ), 50000, 2048 );
            new Copy_to_ADS().apply( wav, dest, new Interval( 0, wav.get_sample_number() ) );
            ProjectManager.add_from_marker_file( "C:\\Users\\Alex\\Desktop\\chopin valtz op posth mark h 0,003 + l 0,005.txt" );
            Multi_Band_Repair_Marked repair_marked = new Multi_Band_Repair_Marked( 511, 512, 16 );
            repair_marked.getBand_cutoffs().add( 2000 );
            repair_marked.getBand_cutoffs().add( 500 );
            //repair_marked.setRepair_residue( true );
            repair_marked.setCompare_with_direct_repair( true );
            /*Repair_in_memory repair_marked = new Repair_in_memory();
            repair_marked.setBandpass_cutoff_frqs( 2000, -1 );
            repair_marked.setWork_on_band_pass( true );*/
            repair_marked.apply( wav, dest, new Interval( 0, wav.get_sample_number() ) );
            dest.flushAll();
            dest.close();
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
        catch( FileNotFoundException e )
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
        try
        {
            int old_sample_number = 16689280, new_sample_number = 16218931;
            ProjectManager.lock_access();
            ProjectManager.add_from_marker_file( "D:\\training sets\\dvorak 4th symph fin mark 0,007.txt" );
            List< Marking > markings = ProjectManager.getMarkerFile().get_all_markings( new Interval( 0, old_sample_number ) );
            MarkerFile new_markerfile = new MarkerFile();
            for( Marking m : markings )
            {
                new_markerfile.addMark( remap_to_interval( m.get_first_marked_sample(), 0, old_sample_number, 0, new_sample_number ), remap_to_interval( m.get_last_marked_sample(), 0, old_sample_number, 0, new_sample_number ), m.getChannel() );
            }
            new_markerfile.writeMarkingsToFile( new FileWriter( "D:\\training sets\\resampled\\dvorak 4th symph fin mark 96000.txt" ) );
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
}
