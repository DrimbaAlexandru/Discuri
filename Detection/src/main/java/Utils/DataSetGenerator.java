package Utils;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import Exceptions.DataSourceException;
import MarkerFile.*;
import ProjectManager.ProjectManager;

import java.io.*;
import java.util.Locale;
import java.util.Random;

/**
 * Created by Alex on 08.01.2018.
 */
public class DataSetGenerator
{
    public static void generate( IAudioDataSource dataSource, Interval interval, String destination_path, int near_window_size, double non_marked_probab ) throws DataSourceException, IOException
    {
        final int window_size = 129;
        final int fetch_size = dataSource.get_sample_rate() + ( window_size - 1 );
        int certain_write_samples = 0;
        int uncertain_write_samples_written = 0;
        int marked_written = 0;
        int unmarked_written = 0;
        double prob_of_skipping_interval = 0.5;

        Random rand = new Random();

        MarkerFile markerFile = ProjectManager.getMarkerFile();
        interval.limit( 0, dataSource.get_sample_number() - window_size );

        MarkerFile certainWrites = new MarkerFile();

        for( Marking m : markerFile.get_all_markings( interval ) )
        {
            if( rand.nextFloat() < prob_of_skipping_interval )
            {
                continue;
            }
            certainWrites.addMark( m.get_first_marked_sample() - near_window_size, m.get_last_marked_sample() + near_window_size, m.getChannel() );
        }

        DataOutputStream writer = new DataOutputStream( new BufferedOutputStream( new FileOutputStream( destination_path ) ) );

        int i, j, k, ch;
        boolean willWrite, isMarked;
        AudioSamplesWindow win;

        for( i = interval.l; i < interval.r - window_size; )
        {
            win = dataSource.get_samples( i, Math.min( fetch_size, dataSource.get_sample_number() - i ) );

            for( ch = 0; ch < win.get_channel_number(); ch++ )
            {
                for( j = ( window_size - 1 ) / 2; j < win.get_length() - ( window_size - 1 ) / 2; j++ )
                {
                    willWrite = certainWrites.isMarked( i + j, ch );
                    isMarked = markerFile.isMarked(  i + j, ch );
                    if( !willWrite )
                    {
                        willWrite = ( rand.nextDouble() < non_marked_probab );
                        if( willWrite )
                        {
                            uncertain_write_samples_written++;
                        }
                    }
                    else
                    {
                        certain_write_samples++;
                    }

                    if( willWrite )
                    {
                        if( ( uncertain_write_samples_written + certain_write_samples ) % 1000 == 0 )
                        {
                            System.out.println( "Written " + ( uncertain_write_samples_written + certain_write_samples ) );
                            System.out.println( "At sample " + ( i + j ) + "/" + dataSource.get_sample_number() );
                            System.out.println( "Ratio ( unmarked / marked ): " + ( double )unmarked_written / marked_written );
                        }

                        write_case( win, i + j, window_size, ch, isMarked, writer );

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
        for( int k = -win_length / 2; k <= win_length / 2; k++ )
        {
            writer.writeFloat( ( float )win.getSample( sample_idx + k, ch ) );
        }
        writer.writeInt( isMarked ? 1 : 0 );
        //writer.write( "\n" );
    }
}
