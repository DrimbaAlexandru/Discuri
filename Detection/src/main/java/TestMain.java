import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.FileADS.WAVFileAudioSource;
import AudioDataSource.ADCache.CachedAudioDataSource;
import SignalProcessing.Effects.FIR_Filter;
import SignalProcessing.FIR.FIR;
import Utils.Interval;

/**
 * Created by Alex on 08.09.2017.
 */
public class TestMain {
    public static void main( String[] args )
    {
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\temp.wav", 2, 44100, 2 );
            wav.put_samples( new AudioSamplesWindow( new double[][]{ { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 }, { 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5 } }, 0, 10, 2 ) );
            FIR_Filter effect = new FIR_Filter();
            FIR fir = new FIR( new double[]{ 0.2, 0, 0, 0, 0, 0.4 }, 6 );
            effect.setFilter( fir );
            effect.apply( wav, new Interval( 0, 10 ) );
            wav.close();

        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }

    }

    public static void main2(String args[])
    {
        try
        {
            WAVFileAudioSource wav = new WAVFileAudioSource( "C:\\Users\\Alex\\Desktop\\proj_files\\out.wav", 2, 44100, 2 );
            AudioSamplesWindow win;
            double buffer[][] = new double[ 2 ][ 4410 ];

            CachedAudioDataSource cached = new CachedAudioDataSource( wav, 4410, 1024 );

            for( int i = 0; i < buffer[ 0 ].length; i++ )
            {
                for( int k = 0; k < buffer.length; k++ )
                {
                    buffer[ k ][ i ] = Math.sin( Math.PI * i / 49 );
                }
            }

            win = new AudioSamplesWindow( buffer, 0, 4410, 2 );
            cached.put_samples( win );

            win = new AudioSamplesWindow( buffer, 4410, 4410, 2 );
            cached.put_samples( win );

            win = new AudioSamplesWindow( buffer, 8820, 4410, 2 );
            cached.put_samples( win );

            for( int i = 0; i < buffer[ 0 ].length; i++ )
            {
                for( int k = 0; k < buffer.length; k++ )
                {
                    buffer[ k ][ i ] = Math.sin( Math.PI * i / 49 ) / 2;
                }
            }

            win = new AudioSamplesWindow( buffer, 0 + 4410/2, 4410, 2 );
            cached.put_samples( win );
            win = new AudioSamplesWindow( buffer, 4410 + 4410/2, 4410, 2 );
            cached.put_samples( win );

            cached.flushAll();
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();

        }
    }
}
