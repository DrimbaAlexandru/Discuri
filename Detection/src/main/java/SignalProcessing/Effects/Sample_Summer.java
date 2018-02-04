package SignalProcessing.Effects;

import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.FIR.IIR;
import Utils.Interval;

/**
 * Created by Alex on 22.01.2018.
 */
public class Sample_Summer implements IEffect
{
    private final static int max_buffer_size = 2048;
    private static double[] buffer = new double[ max_buffer_size ];

    @Override
    public String getName()
    {
        return "Sample Summer";
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        int temp_len;
        int i, j, k;
        double sum;
        AudioSamplesWindow win;

        interval.r = Math.min( dataSource.get_sample_number(), interval.r );
        interval.l = Math.max( 1, interval.l );

        for( i = interval.l; i < interval.r - 1; )
        {
            temp_len = Math.min( interval.r - i, max_buffer_size );
            win = dataSource.get_samples( i - 1, temp_len );
            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                sum = win.getSample( i - 1, k );
                for( j = 0; j < temp_len - 1; j++ )
                {
                    sum += win.getSample( i + j, k );
                    win.putSample( i + j, k, sum );
                }
            }
            dataDest.put_samples( win );
            i += temp_len - 1;
        }
    }

}
