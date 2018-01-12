import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.ADCache.CachedAudioDataSource;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.FileADS.WAVFileAudioSource;
import AudioDataSource.IAudioDataSource;
import MarkerFile.MarkerFile;

import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.Random;

/**
 * Created by Alex on 08.01.2018.
 */
public class DataSetGenerator
{
    public static void main( String[] args )
    {
        final String WAVFilePath = "C:\\Users\\Alex\\Desktop\\for training derivative.wav";
        final String MarkerFilePath = "C:\\Users\\Alex\\Desktop\\marker.txt";
        final String DataSetFile = "D:\\dataset3.txt";
        final int window_size = 128;
        double[] values = new double[ window_size ];
        int marked_samples = 0;
        int unmarked_samples_written = 0;

        try
        {
            IAudioDataSource dataSource = new CachedAudioDataSource( new WAVFileAudioSource( WAVFilePath ), 44100 + window_size, 8192 );
            MarkerFile markerFile = MarkerFile.fromFile( MarkerFilePath );
            FileWriter writer = new FileWriter( DataSetFile );
            int i, j, ch;
            double meanValue, standardDeviation;
            boolean isMarked;
            boolean willWrite;
            AudioSamplesWindow win;
            Random rand = new Random();

            for( i = 0; i < 10000 - window_size; i++ )
            {
                if( i % 10000 == 0 )
                {
                    System.out.println( i );
                }
                win = dataSource.get_samples( i, window_size );
                //win.applyWindow( ( x ) -> x );
                for( ch = 0; ch < win.get_channel_number(); ch++ )
                {
                    isMarked = markerFile.isMarked( i + window_size / 2, ch );
                    willWrite = isMarked || ( rand.nextFloat() > ( ( float )unmarked_samples_written / marked_samples ) );

                    if( willWrite )
                    {
                        for( j = win.get_first_sample_index(); j < win.get_first_sample_index() + win.get_length(); j++ )
                        {
                            writer.write( (float)win.getSample( j, ch ) + " " );
                        }
                        if( isMarked )
                        {
                            marked_samples++;
                            writer.write( "1" );
                        }
                        else
                        {
                            unmarked_samples_written++;
                            writer.write( "0" );
                        }
                        writer.write( "\n" );
                    }
                }
            }
            writer.close();

        }
        catch( DataSourceException | ParseException | IOException e )
        {
            e.printStackTrace();
        }
    }
}
