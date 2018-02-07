import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.ADCache.CachedAudioDataSource;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.FileADS.AUFileAudioSource;
import AudioDataSource.FileADS.WAVFileAudioSource;
import MarkerFile.MarkerFile;
import SignalProcessing.Effects.FIR_Filter;
import SignalProcessing.Effects.IIR_Filter;
import SignalProcessing.Filters.FIR;
import SignalProcessing.Filters.IIR;
import SignalProcessing.Interpolation.FFTInterpolator;
import SignalProcessing.Interpolation.Interpolator;
import SignalProcessing.Interpolation.LinearInterpolator;
import SignalProcessing.Windowing.Windowing;
import Utils.Interval;

import java.io.IOException;

/**
 * Created by Alex on 08.09.2017.
 */
public class TestMain {
    public static void main1( String[] args )
    {
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\fp mark.wav" );
            double threshold = 0.1;
            int side = 1;
            MarkerFile mf = new MarkerFile( "C:\\Users\\Alex\\Desktop\\fp mark 0.1.txt" );
            if( wav.get_channel_number() != 1 )
            {
                throw new DataSourceException( "Not mono" );
            }
            AudioSamplesWindow win = wav.get_samples( 0, wav.get_sample_number() );
            int i, j;
            boolean mark;
            for( i = side; i < wav.get_sample_number() - side; i++ )
            {
                if( i % 44100 == 0 )
                {
                    System.out.println( "Processed " + i / 44100 + " seconds" );
                }
                mark = false;
                for( j = -side; j <= side; j++ )
                {
                    mark = mark || ( Math.abs( win.getSample( i + j, 0 ) ) >= threshold );
                }
                if( mark )
                {
                    mf.addMark( i, i, 0 );
                    mf.addMark( i, i, 1 );
                }
            }
            mf.writeMarkingsToFile();

        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
        catch( IOException e )
        {
            e.printStackTrace();
        }

    }

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

    public static void main( String args[] )
    {
        try
        {
            WAVFileAudioSource src = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\eq test.wav" );
            WAVFileAudioSource dst = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\out.wav", src.get_channel_number(), src.get_sample_rate(), src.getByte_depth() );
            CachedAudioDataSource src_cache = new CachedAudioDataSource( src, 44100, 2048 );
            CachedAudioDataSource dst_cache = new CachedAudioDataSource( dst, 44100, 2048 );

            double[] x = { 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1 };
            FIR fir = FIR.fromFreqResponse2( x, x.length - 1, src.get_sample_rate(), 127 );
            Windowing.apply( fir.getB(), fir.getTap_nr(), Windowing.Hann_window );
            Windowing.apply( fir.getB(), fir.getTap_nr(), ( v ) -> 1.0 / ( fir.getTap_nr() ) );

            plot_in_matlab( x, x.length, fir.getB(), fir.getTap_nr() );

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

        plot_in_matlab( in, n, out, m );
    }

    private static void plot_in_matlab( double[] in, int n, double[] out, int m )
    {
        int i;
        System.out.print( "t1 = [ " );
        for( i = 0; i < n - 1; i++ )
        {
            System.out.print( ( double )i / ( n - 1 ) + ", " );
        }
        System.out.println( "1 ];" );

        System.out.print( "t2 = [ " );
        for( i = 0; i < m - 1; i++ )
        {
            System.out.print( ( double )i / ( m - 1 ) + ", " );
        }
        System.out.println( "1 ];" );

        System.out.print( "in = [ " );
        for( i = 0; i < n - 1; i++ )
        {
            System.out.print( in[ i ] + ", " );
        }
        System.out.println( in[ n - 1 ] + " ];" );

        System.out.print( "out = [ " );
        for( i = 0; i < m - 1; i++ )
        {
            System.out.print( out[ i ] + ", " );
        }
        System.out.println( out[ m - 1 ] + " ];" );

        System.out.println( "plot( t1, in, 'LineWidth', 1, ...\n t2, out, 'LineWidth', 1 );" );
        System.out.println( "legend( 'original signal', 'resized signal' );" );
    }
}
