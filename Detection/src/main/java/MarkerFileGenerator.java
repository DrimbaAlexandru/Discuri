import AudioDataSource.AudioSamplesWindow;
import Exceptions.DataSourceException;
import AudioDataSource.FileADS.WAVFileAudioSource;
import MarkerFile.MarkerFile;

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
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\chopin valtz op posth inv riaa poc.wav" );
            double threshold = 0.004;
            int side = 3;
            int[] mark_start = new int[]{ -1, -1 };
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
                    mark = false;
                    for( j = -side; j <= side; j++ )
                    {
                        mark = mark || ( Math.abs( win.getSample( i + j, k ) ) >= threshold );
                    }
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
                            mf.addMark( mark_start[ k ], i - 1, k );
                            if( duplicate_L_to_R && k == 0 )
                            {
                                mf.addMark( mark_start[ k ], i - 1, 1 );
                            }
                            mark_start[ k ] = -1;
                        }
                    }
                }
            }
            mf.writeMarkingsToFile( new FileWriter( "C:\\Users\\Alex\\Desktop\\chopin valtz op posth mark l " + String.format( "%.4f", threshold ) + ".txt" ) );
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