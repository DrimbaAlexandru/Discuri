package Utils;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import Exceptions.DataSourceException;
import MarkerFile.*;
import ProjectManager.ProjectManager;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.Random;

/**
 * Created by Alex on 08.01.2018.
 */
public class DataSetGenerator
{
    private static ByteBuffer buf;

    public static void generate( IAudioDataSource dataSource, Interval interval, String destination_path, int near_window_size, float non_marked_probab, float doubling_probab, float prob_of_skipping_marking ) throws DataSourceException, IOException
    {
        final int window_size = 129;
        final int fetch_size = dataSource.get_sample_rate() + ( window_size - 1 );
        int marked_written = 0;
        int unmarked_written = 0;

        Random rand = new Random();

        MarkerFile markerFile = ProjectManager.getMarkerFile();
        interval.limit( 0, dataSource.get_sample_number() - window_size );
        buf = ByteBuffer.allocate( ( 130 ) * 4 );
        buf.order( ByteOrder.LITTLE_ENDIAN );

        MarkerFile writes_prob_1 = new MarkerFile();
        MarkerFile writes_prob_0_5 = new MarkerFile();
        MarkerFile writes_prob_0_1 = new MarkerFile();

        final int prob_1_margin = near_window_size / 4;
        final int prob_05_margin = near_window_size / 2;
        final int prob_01_margin = near_window_size;

        for( Marking m : markerFile.get_all_markings( interval ) )
        {
            if( rand.nextFloat() < prob_of_skipping_marking )
            {
                continue;
            }
            writes_prob_1.addMark( m.get_first_marked_sample(), m.get_last_marked_sample(), m.getChannel() );

            writes_prob_1.addMark( m.get_first_marked_sample() - prob_1_margin, m.get_first_marked_sample() - 1, m.getChannel() );
            writes_prob_1.addMark( m.get_last_marked_sample() + 1, m.get_last_marked_sample() + prob_1_margin, m.getChannel() );

            writes_prob_0_5.addMark( m.get_first_marked_sample() - prob_05_margin, m.get_first_marked_sample() - 1 - prob_1_margin, m.getChannel() );
            writes_prob_0_5.addMark( m.get_last_marked_sample() + 1 + prob_1_margin, m.get_last_marked_sample() + prob_05_margin, m.getChannel() );

            writes_prob_0_1.addMark( m.get_first_marked_sample() - prob_01_margin, m.get_first_marked_sample() - prob_05_margin - 1, m.getChannel() );
            writes_prob_0_1.addMark( m.get_last_marked_sample() + 1 + prob_05_margin, m.get_last_marked_sample() + prob_01_margin, m.getChannel() );
        }

        FileOutputStream fos = new FileOutputStream( destination_path );
        BufferedOutputStream bos = new BufferedOutputStream( fos );
        DataOutputStream writer = new DataOutputStream( bos );

        int i, j, ch;
        boolean willWrite, isMarked;
        AudioSamplesWindow win;

        for( i = interval.l; i < interval.r - window_size; )
        {
            win = dataSource.get_samples( i, Math.min( fetch_size, dataSource.get_sample_number() - i ) );

            for( ch = 0; ch < win.get_channel_number(); ch++ )
            {
                for( j = ( window_size - 1 ) / 2; j < win.get_length() - ( window_size - 1 ) / 2; j++ )
                {
                    isMarked = markerFile.isMarked( i + j, ch );
                    willWrite = writes_prob_1.isMarked( i + j, ch );
                    willWrite = willWrite || ( rand.nextFloat() < 0.5f && writes_prob_0_5.isMarked( i + j, ch ) );
                    willWrite = willWrite || ( rand.nextFloat() < 0.1f && writes_prob_0_1.isMarked( i + j, ch ) );
                    if( !willWrite )
                    {
                        willWrite = ( rand.nextFloat() < non_marked_probab );
                    }

                    if( willWrite )
                    {
                        if( ( marked_written + unmarked_written ) % 1000 == 0 )
                        {
                            System.out.println( "Written " + ( marked_written + unmarked_written ) );
                            System.out.println( "At sample " + ( i + j ) + "/" + dataSource.get_sample_number() );
                            System.out.println( "Ratio ( unmarked / marked ): " + ( float )unmarked_written / marked_written );
                            marked_written = 0;
                            unmarked_written = 0;
                        }

                        write_case( win, i + j, window_size, ch, isMarked, writer );
                        if( !isMarked )
                        {
                            while( rand.nextFloat() < doubling_probab )
                            {
                                write_case( win, i + j, window_size, ch, isMarked, writer );
                                unmarked_written++;
                            }
                        }

                        if( isMarked )
                        {
                            marked_written++;
                        }
                        else
                        {
                            unmarked_written++;
                        }
                    }
                }
            }
            i += win.get_length() - ( window_size - 1 );

        }
        writer.close();

    }

    public static void generateV2( IAudioDataSource dataSource, Interval interval, String out_path ) throws DataSourceException, IOException
    {
        final int fetch_size = dataSource.get_sample_rate() / 8 * 8;
        MarkerFile markerFile = ProjectManager.getMarkerFile();
        AudioSamplesWindow win;

        FileOutputStream fos = new FileOutputStream( out_path );
        BufferedOutputStream bos = new BufferedOutputStream( fos );
        DataOutputStream writer = new DataOutputStream( bos );

        int i, ch;
        int flags;

        interval.limit( 0, dataSource.get_sample_number() );
        buf = ByteBuffer.allocate( fetch_size * 2 + fetch_size / 8 * 2 );
        buf.order( ByteOrder.LITTLE_ENDIAN );

        for( ch = 0; ch < dataSource.get_channel_number(); ch++ )
        {
            for( i = interval.l; i < interval.r; i += fetch_size )
            {
                win = dataSource.get_samples( i, fetch_size );
                buf.rewind();

                for( int s = 0; s < win.get_length() / 8; s++ )
                {
                    buf.putShort( ( short )( win.getSample( win.get_first_sample_index() + s * 8 + 0, ch ) * 32768 ) );
                    buf.putShort( ( short )( win.getSample( win.get_first_sample_index() + s * 8 + 1, ch ) * 32768 ) );
                    buf.putShort( ( short )( win.getSample( win.get_first_sample_index() + s * 8 + 2, ch ) * 32768 ) );
                    buf.putShort( ( short )( win.getSample( win.get_first_sample_index() + s * 8 + 3, ch ) * 32768 ) );
                    buf.putShort( ( short )( win.getSample( win.get_first_sample_index() + s * 8 + 4, ch ) * 32768 ) );
                    buf.putShort( ( short )( win.getSample( win.get_first_sample_index() + s * 8 + 5, ch ) * 32768 ) );
                    buf.putShort( ( short )( win.getSample( win.get_first_sample_index() + s * 8 + 6, ch ) * 32768 ) );
                    buf.putShort( ( short )( win.getSample( win.get_first_sample_index() + s * 8 + 7, ch ) * 32768 ) );

                    flags = ( ( markerFile.isMarked( s * 8 + 0, ch ) ? 1 : 0 ) << 7 ) | ( ( markerFile.isMarked( s * 8 + 1, ch ) ? 1 : 0 ) << 6 ) | ( ( markerFile.isMarked( s * 8 + 2, ch ) ? 1 : 0 ) << 5 ) | ( ( markerFile.isMarked( s * 8 + 3, ch ) ? 1 : 0 ) << 4 ) | ( ( markerFile.isMarked( s * 8 + 4, ch ) ? 1 : 0 ) << 3 ) | ( ( markerFile.isMarked( s * 8 + 5, ch ) ? 1 : 0 ) << 2 ) | ( ( markerFile.isMarked( s * 8 + 6, ch ) ? 1 : 0 ) << 1 ) | ( ( markerFile.isMarked( s * 8 + 7, ch ) ? 1 : 0 ) << 0 );
                    buf.put( ( byte )flags );
                }
                writer.write( buf.array(), 0, buf.position() );
            }
        }
        writer.flush();
        writer.close();

    }

    private static void write_case( AudioSamplesWindow win, int sample_idx, int win_length, int ch, boolean isMarked, DataOutputStream writer ) throws DataSourceException, IOException
    {
        buf.clear();
        for( int k = -win_length / 2; k <= win_length / 2; k++ )
        {
            buf.putFloat( ( float )win.getSample( sample_idx + k, ch ) );
        }
        buf.putInt( isMarked ? 1 : 0 );
        writer.write( buf.array() );
        //writer.write( "\n" );
    }
}
