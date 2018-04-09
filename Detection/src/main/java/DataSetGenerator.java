import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.ADCache.CachedAudioDataSource;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.FileADS.IFileAudioDataSource;
import AudioDataSource.FileADS.WAVFileAudioSource;
import AudioDataSource.IAudioDataSource;
import MarkerFile.MarkerFile;
import Utils.Interval;

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
        final String WAVFilePath = "D:\\datasets\\Chopin - Etude op. 25 no. 11 inv riaa.wav";
        final String MarkerFilePath = "D:\\teste procesare audio\\chopin m3 0.015.txt";
        final String DataSetFile = "D:\\datasets\\dataset_chopin inv riaa class 0.txt";

        final int window_size = 129;
        final int near_window_size = 32;
        final int fetch_size = 44100 + ( window_size - 1 );
        int marked_samples = 0;
        int unmarked_samples_written = 0;
        int near_mark_samples_written = 0;

        try
        {
            IFileAudioDataSource dataSource = FileAudioSourceFactory.fromFile( WAVFilePath );
            MarkerFile markerFile = MarkerFile.fromFile( MarkerFilePath );
            FileWriter writer = new FileWriter( DataSetFile );
            int i, j, k, ch;
            boolean isMarked;
            boolean willWrite, nearMarking;
            AudioSamplesWindow win;
            Random rand = new Random();
            Interval next_mark;
            Interval prev_mark;

            //for( i = 0; i < dataSource.get_sample_number() - window_size; )
            for( i = ( 5 * 60 + 55 ) * dataSource.get_sample_rate(); i < dataSource.get_sample_number() - window_size; )
            {
                win = dataSource.get_samples( i, Math.min( fetch_size, dataSource.get_sample_number() - i ) );

                for( ch = 0; ch < win.get_channel_number(); ch++ )
                {
                    next_mark = markerFile.getNextMark( i + ( window_size - 1 ) / 2, ch );
                    prev_mark = new Interval( -window_size, 0 );
                    for( j = ( window_size - 1 ) / 2; j < win.get_length() - ( window_size - 1 ) / 2; j++ )
                    {
                        if( next_mark != null && next_mark.r <= i + j )
                        {
                            prev_mark = next_mark;
                            next_mark = markerFile.getNextMark( i + j, ch );
                        }

                        isMarked = next_mark != null && next_mark.contains( i + j );

                        willWrite = isMarked || ( rand.nextFloat() < ( ( float )marked_samples - unmarked_samples_written ) / ( ( next_mark == null ) ? ( dataSource.get_sample_number() - prev_mark.r ) : ( next_mark.l - prev_mark.r ) ) );

                        nearMarking = ( i + j < prev_mark.r + near_window_size ) || ( ( next_mark != null ) && ( i + j > next_mark.l - near_window_size ) );

                        if( willWrite || nearMarking )
                        {
                            if( ( marked_samples + unmarked_samples_written + near_mark_samples_written ) % 1000 == 0 )
                            {
                                System.out.println( "Written " + ( marked_samples + unmarked_samples_written + near_mark_samples_written ) );
                                System.out.println( "At sample " + ( i + j ) + "/" + dataSource.get_sample_number() );
                            }
                            for( k = -( window_size - 1 ) / 2; k <= ( window_size - 1 ) / 2; k++ )
                            {
                                writer.write( String.format( Locale.US, "%f, ", ( float )win.getSample( i + j + k, ch ) ) );
                            }
                            //writer.write( String.format( Locale.US, "%d, ch%d ", i + j, ch ) );
                            if( isMarked )
                            {
                                marked_samples++;
                                writer.write( "1" );
                            }
                            else
                            {
                                if( nearMarking )
                                {
                                    near_mark_samples_written++;
                                }
                                else
                                {
                                    unmarked_samples_written++;
                                }
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
