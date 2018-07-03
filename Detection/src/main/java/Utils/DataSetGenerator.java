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
    private static ByteBuffer buf = ByteBuffer.allocate( ( 130 ) * 4 );

    public static void generate( IAudioDataSource dataSource, Interval interval, String destination_path, int near_window_size, double non_marked_probab, float doubling_probab ) throws DataSourceException, IOException
    {
        final int window_size = 129;
        final int fetch_size = dataSource.get_sample_rate() + ( window_size - 1 );
        int marked_written = 0;
        int unmarked_written = 0;
        double prob_of_skipping_interval = 0;

        Random rand = new Random();

        MarkerFile markerFile = ProjectManager.getMarkerFile();
        interval.limit( 0, dataSource.get_sample_number() - window_size );
        buf.order( ByteOrder.LITTLE_ENDIAN );

        MarkerFile writes_prob_0_5 = new MarkerFile();
        MarkerFile writes_prob_0_1 = new MarkerFile();

        for( Marking m : markerFile.get_all_markings( interval ) )
        {
            if( rand.nextFloat() < prob_of_skipping_interval )
            {
                continue;
            }
            writes_prob_0_5.addMark( m.get_first_marked_sample() - near_window_size / 4, m.get_first_marked_sample() - 1, m.getChannel() );
            writes_prob_0_5.addMark( m.get_last_marked_sample() + 1, m.get_last_marked_sample() + near_window_size / 4, m.getChannel() );
            writes_prob_0_1.addMark( m.get_first_marked_sample() - near_window_size, m.get_first_marked_sample() - near_window_size / 4 - 1, m.getChannel() );
            writes_prob_0_1.addMark( m.get_last_marked_sample() + near_window_size / 4 + 1, m.get_last_marked_sample() + near_window_size, m.getChannel() );
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
                    willWrite = isMarked = markerFile.isMarked( i + j, ch );
                    willWrite = willWrite || ( rand.nextFloat() < 0.5 && writes_prob_0_5.isMarked( i + j, ch ) );
                    willWrite = willWrite || ( rand.nextFloat() < 0.1 && writes_prob_0_1.isMarked( i + j, ch ) );
                    if( !willWrite )
                    {
                        willWrite = ( rand.nextDouble() < non_marked_probab );

                    }

                    if( willWrite )
                    {
                        if( ( marked_written + unmarked_written ) % 1000 == 0 )
                        {
                            System.out.println( "Written " + ( marked_written + unmarked_written ) );
                            System.out.println( "At sample " + ( i + j ) + "/" + dataSource.get_sample_number() );
                            System.out.println( "Ratio ( unmarked / marked ): " + ( double )unmarked_written / marked_written );
                        }

                        write_case( win, i + j, window_size, ch, isMarked, writer );
                        if( isMarked )
                        {
                            while( rand.nextFloat() < doubling_probab )
                            {
                                write_case( win, i + j, window_size, ch, isMarked, writer );
                                marked_written++;
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
