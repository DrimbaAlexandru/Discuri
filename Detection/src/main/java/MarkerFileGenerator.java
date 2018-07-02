import AudioDataSource.AudioSamplesWindow;
import Exceptions.DataSourceException;
import AudioDataSource.FileADS.WAVFileAudioSource;
import MarkerFile.*;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by Alex on 16.02.2018.
 */
public class MarkerFileGenerator
{
    public static void main( String[] args )
    {
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "D:\\training sets\\Beethoven - Quartet no 4 - IV mark.wav" );
            double threshold = 0.0090;
            int side =  2;
            int min_distance_markings = 1;
            int[] mark_start = new int[]{ -1, -1 };
            Marking[] last_marking = new Marking[]{ new Marking( -2 * side, -2 * side, 0 ), new Marking( -2 * side, -2 * side, 1 ) };
            int i, j, k;
            boolean mark;
            boolean duplicate_L_to_R = true;
            MarkerFile mf = new MarkerFile();
            AudioSamplesWindow win;

            if( wav.get_channel_number() > 2 )
            {
                throw new DataSourceException( "Not mono or stereo" );
            }

            win = wav.get_samples( 0, wav.get_sample_rate() + side );

            for( i = side; i < wav.get_sample_number() - side; i++ )
            {
                if( i % wav.get_sample_rate() == 0 )
                {
                    win = wav.get_samples( i - side, Math.min( wav.get_sample_rate() + side * 2 + 1, wav.get_sample_number() - i + side ) );
                    System.out.println( "Processed " + i / wav.get_sample_rate() + " seconds" );
                }
                for( k = 0; k < wav.get_channel_number(); k++ )
                {
                    mark = ( Math.abs( win.getSample( i, k ) ) >= threshold );
                    if( mark )
                    {
                        if( mark_start[ k ] == -1 )
                        {
                            mark_start[ k ] = i;
                        }
                    }
                    else
                    {
                        if( mark_start[ k ] != -1 )
                        {
                            Marking newMarking = new Marking( mark_start[ k ] - side, i - 1 + side, k );
                            if( newMarking.get_first_marked_sample() - last_marking[ k ].get_last_marked_sample() - 1 < min_distance_markings )
                            {
                                newMarking.set_first_marked_sample( last_marking[ k ].get_last_marked_sample() + 1 );
                            }
                            last_marking[ k ] = newMarking;
                            mf.addMark( newMarking.get_first_marked_sample(),newMarking.get_last_marked_sample(), k );
                            if( duplicate_L_to_R && k == 0 )
                            {
                                mf.addMark( newMarking.get_first_marked_sample(),newMarking.get_last_marked_sample(), 1 );
                            }
                            mark_start[ k ] = -1;
                        }
                    }
                }
            }
            mf.writeMarkingsToFile( new FileWriter( "D:\\training sets\\Beethoven - Quartet no 4 - IV mark s " + side + " m " + min_distance_markings + " " + String.format( "%.4f", threshold ) + ".txt" ) );
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
}