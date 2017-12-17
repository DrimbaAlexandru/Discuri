import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.FileADS.WAVFileAudioSource;
import AudioDataSource.VersionedAudioDataSource.AudioDataSourceVersion;

/**
 * Created by Alex on 08.09.2017.
 */
public class TestMain {

    public static void main(String args[])
    {
        AudioDataSourceVersion ver = new AudioDataSourceVersion( 0, 44100, 2, 0 );
        double buffer[][] = new double[ 2 ][ 4410 ];

        for( int i = 0; i < buffer[ 0 ].length; i++ )
        {
            for( int k = 0; k < buffer.length; k++ )
            {
                buffer[ k ][ i ] = Math.sin( Math.PI * i / 49 );
            }
        }

        AudioSamplesWindow win;
        try
        {
            win = new AudioSamplesWindow( buffer, 0, 4410, 2 );
            ver.replace_block( win, 0 );

            win = new AudioSamplesWindow( buffer, 4410, 4410, 2 );
            ver.replace_block( win, 0 );

            for( int i = 0; i < buffer[ 0 ].length; i++ )
            {
                for( int k = 0; k < buffer.length; k++ )
                {
                    buffer[ k ][ i ] = Math.sin( Math.PI * i / 49 ) / 2;
                }
            }

            win = new AudioSamplesWindow( buffer, 2156, 4410, 2 );
            ver.replace_block( win, 4410 );

            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\proj_files\\out.wav", 2, 44100, 2 );
            wav.put_samples( ver.get_samples( 0, ver.get_sample_number() ) );
        }
        catch( DataSourceException e )
        {
            System.err.println( e.getMessage() );
            e.printStackTrace();
        }
    }
}
