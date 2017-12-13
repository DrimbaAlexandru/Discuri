import AudioDataSource.AUFileAudioSource;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.WAVFileAudioSource;
import MarkerFile.MarkerFile;
import WavFile.AudioDataCache.AudioSamplesWindow;

/**
 * Created by Alex on 08.09.2017.
 */
public class TestMain {

    public static void main(String args[])
    {
        /*MarkerFile mf = new MarkerFile( "C:\\Users\\Alex\\Desktop\\dump.txt" );
        mf.addMark( 10, 20, 0 );    //10-20
        mf.addMark( 30, 40, 0 );    //10-20, 30-40
        mf.addMark( 21, 25, 0 );    //10-25, 30-40
        mf.addMark( 26, 29, 0 );    //10-40

        mf.addMark( 50, 70, 0 );    //10-40, 50-70
        mf.addMark( 55, 65, 0 );    //10-40, 50-70
        mf.addMark( 55, 85, 0 );    //10-40, 50-85

        mf.deleteMark( 5, 15, 0 );  //16-40, 50-85
        mf.deleteMark( 36, 45, 0 ); //16-35, 50-85
        mf.deleteMark( 21, 29, 0 ); //16-20, 30-35, 50-85
        mf.deleteMark( 35, 50, 0 ); //16-20, 30-34, 51-85
        mf.deleteMark( 25, 75, 0 ); //16-20, 76-85

        mf.addMark( 25, 30, 0 );    //16-20, 25-30, 76-85

        for( int i = 15; i < 35; i++ )
        {
            if( mf.isMarked( i, 0 ) )
            {
                System.out.println( i + " is marked" );
            }
            else
            {
                System.out.println( i + " is not marked" );
            }
        }*/
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\8.wav" );
            AUFileAudioSource au = new AUFileAudioSource( "C:\\Users\\Alex\\Desktop\\test.au", 1, 44100, 2 );
            au.put_samples( wav.get_samples( 0, wav.get_sample_number() ) );
            au.close();
        }
        catch( DataSourceException e )
        {
            System.err.println( e.getDSEcause().name() + " : " + e.getMessage() );
            e.printStackTrace();
        }
    }
}
