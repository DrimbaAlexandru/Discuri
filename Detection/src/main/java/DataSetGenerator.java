import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.ADCache.CachedAudioDataSource;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.FileADS.IFileAudioDataSource;
import AudioDataSource.FileADS.WAVFileAudioSource;
import AudioDataSource.IAudioDataSource;
import MarkerFile.MarkerFile;

import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.Locale;
import java.util.Random;

/**
 * Created by Alex on 08.01.2018.
 */
public class DataSetGenerator
{
    public static void main( String[] args )
    {
        final String WAVFilePath = "C:\\Users\\Alex\\Desktop\\Chopin - Etude op. 25 no. 11 inv riaa sum 2000.wav";
        final String MarkerFilePath = "D:\\teste procesare audio\\chopin m3 0.015.txt";
        final String DataSetFile = "D:\\datasets\\dataset_chopin inv riaa sum 2000.txt";
        final int window_size = 129;
        final int fetch_size = 44100 + ( window_size - 1 );
        int marked_samples = 0;
        int unmarked_samples_written = 0;

        try
        {
            IFileAudioDataSource dataSource = FileAudioSourceFactory.fromFile( WAVFilePath );
            MarkerFile markerFile = MarkerFile.fromFile( MarkerFilePath );
            FileWriter writer = new FileWriter( DataSetFile );
            int i, j, k, ch;
            boolean isMarked;
            boolean willWrite;
            AudioSamplesWindow win;
            Random rand = new Random();


            //for( i = window_size / 2; i < dataSource.get_sample_number() - window_size / 2; )
            for( i = 44100 * ( 5 * 60 + 55 ); i < dataSource.get_sample_number() - window_size; )
            {
                win = dataSource.get_samples( i, Math.min( fetch_size, dataSource.get_sample_number() - i ) );

                for( j = ( window_size - 1 ) / 2; j < win.get_length() - ( window_size - 1 ) / 2; j++ )
                {
                    for( ch = 0; ch < win.get_channel_number(); ch++ )
                    {
                        isMarked = markerFile.isMarked( i + j, ch );
                        willWrite = isMarked || ( rand.nextFloat() > ( ( float )unmarked_samples_written / marked_samples ) );

                        if( willWrite )
                        {
                            if( ( marked_samples + unmarked_samples_written ) % 1000 == 0 )
                            {
                                System.out.println( "Written " + ( marked_samples + unmarked_samples_written ) );
                                System.out.println( "At sample " + ( i + j ) + "/" + dataSource.get_sample_number() );
                            }
                            for( k = -( window_size - 1 ) / 2; k <= ( window_size - 1 ) / 2; k++ )
                            {
                                writer.write( String.format( Locale.US, "%f, ", ( float )win.getSample( i + j + k, ch ) ) );
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
                i += win.get_length() - ( window_size - 1 );

            }
            writer.close();

        }
        catch( DataSourceException | ParseException | IOException e )
        {
            e.printStackTrace();
        }
    }
}
