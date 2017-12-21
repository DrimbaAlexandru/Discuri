package Tests;

import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.ADCache.d_CADS;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.FileADS.WAVFileAudioSource;

/**
 * Created by Alex on 21.12.2017.
 */
public class CachedADS_Test
{
    final static double max_16_bit_val = ( ( double )Short.MAX_VALUE ) / ( Short.MAX_VALUE + 1 );
    final static double min_16_bit_val = -1;

    public static void main( String[] args )
    {
        try
        {
            String temp_file_path = "D:\\test.wav";
            WAVFileAudioSource wavADS = new WAVFileAudioSource( temp_file_path, 2, 44100, 2 );
            d_CADS cache = new d_CADS( wavADS, 8096, 1024 );
            double buffer[][] = new double[ 2 ][ 2048 ];
            AudioSamplesWindow win;
            int i, k;

            /* Preparing phase. Populate the WAV ADS. 2 channels.
            *  Put 2048 samples with values ( -0.5, -0.25 ) in the WAV ADS, and 2048 more with values ( 0.5, 0.25 ).
            *  Overwrite samples 1024-3072 with values (-1,1)
            *  Test by getting back some of the values and comparing to the expected values.
            */

            for( k = 0; k < 2; k++ )
            {
                for( i = 0; i < 2048; i++ )
                {
                    buffer[ k ][ i ] = -0.5/( k + 1 );
                }
            }
            win = new AudioSamplesWindow( buffer, 0, 2048, 2 );
            wavADS.put_samples( win );

            for( k = 0; k < 2; k++ )
            {
                for( i = 0; i < 2048; i++ )
                {
                    buffer[ k ][ i ] = 0.5/( k + 1 );
                }
            }
            win = new AudioSamplesWindow( buffer, 2048, 2048, 2 );
            wavADS.put_samples( win );

            for( i = 0; i < 2048; i++ )
            {
                buffer[ 0 ][ i ] = min_16_bit_val;
                buffer[ 1 ][ i ] = max_16_bit_val;
            }
            win = new AudioSamplesWindow( buffer, 1024, 2048, 2 );
            wavADS.put_samples( win );

            assert wavADS.get_sample_number() == 4096;
            assert wavADS.get_channel_number() == 2;
            assert wavADS.get_samples( 0, 1 ).getSample( 0, 0 ) == -0.5;
            assert wavADS.get_samples( 0, 1 ).getSample( 0, 1 ) == -0.25;
            assert wavADS.get_samples( 1023, 1 ).getSample( 1023, 0 ) == -0.5;
            assert wavADS.get_samples( 1023, 1 ).getSample( 1023, 1 ) == -0.25;

            assert wavADS.get_samples( 1024, 1 ).getSample( 1024, 0 ) == min_16_bit_val;
            assert wavADS.get_samples( 1024, 1 ).getSample( 1024, 1 ) == max_16_bit_val;
            assert wavADS.get_samples( 3071, 1 ).getSample( 3071, 0 ) == min_16_bit_val;
            assert wavADS.get_samples( 3071, 1 ).getSample( 3071, 1 ) == max_16_bit_val;

            assert wavADS.get_samples( 3072, 1 ).getSample( 3072, 0 ) == 0.5;
            assert wavADS.get_samples( 3072, 1 ).getSample( 3072, 1 ) == 0.25;
            assert wavADS.get_samples( 4095, 1 ).getSample( 4095, 0 ) == 0.5;
            assert wavADS.get_samples( 4095, 1 ).getSample( 4095, 1 ) == 0.25;

        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
            switch( e.getDSEcause() )
            {

            }
            assert false;
        }
    }

}
