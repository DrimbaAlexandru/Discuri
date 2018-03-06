import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.FileADS.WAVFileAudioSource;
import MarkerFile.MarkerFile;

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
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\salesbury mark 1.wav" );
            double threshold = 0.006;
            int side = 3;
            int mark_start = -1;
            int i, j;
            boolean mark;
            MarkerFile mf = new MarkerFile( "C:\\Users\\Alex\\Desktop\\salesbury mark s3 0.006.txt" );
            AudioSamplesWindow win;

            if( wav.get_channel_number() != 1 )
            {
                throw new DataSourceException( "Not mono" );
            }

            win = wav.get_samples( 0, wav.get_sample_rate() + side );

            for( i = side; i < wav.get_sample_number() - side; i++ )
            {
                if( i % wav.get_sample_rate() == 0 )
                {
                    win = wav.get_samples( i - side, Math.min( wav.get_sample_rate() + side * 2 + 1, wav.get_sample_number() - i + side ) );
                    System.out.println( "Processed " + i / wav.get_sample_rate() + " seconds" );
                }
                mark = false;
                for( j = -side; j <= side; j++ )
                {
                    mark = mark || ( Math.abs( win.getSample( i + j, 0 ) ) >= threshold );
                }
                if( mark )
                {
                    if( mark_start == -1 )
                    {
                        mark_start = i;
                    }
                }
                else
                {
                    if( mark_start != -1 )
                    {
                        mf.addMark( mark_start, i-1, 0 );
                        mf.addMark( mark_start, i-1, 1 );
                        mark_start = -1;
                    }
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
}
