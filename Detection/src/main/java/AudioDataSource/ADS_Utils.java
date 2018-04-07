package AudioDataSource;

import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.Exceptions.DataSourceException;

/**
 * Created by Alex on 17.01.2018.
 */
public class ADS_Utils
{
    public static int buffer_size = 16384;

    public static void copyToADS( IAudioDataSource source, IAudioDataSource destination ) throws DataSourceException
    {
        int temp_len;
        int i;
        AudioSamplesWindow win;

        for( i = 0; i < source.get_sample_number(); )
        {
            temp_len = Math.min( buffer_size, source.get_sample_number() - i );
            win = source.get_samples( i, temp_len );
            destination.put_samples( win );
            i += temp_len;
        }
    }
}
